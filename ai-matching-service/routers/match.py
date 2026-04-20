import asyncio
from fastapi import APIRouter, HTTPException
from schemas import MatchRequest, MatchResponse, MatchResultItem
from services import vector_store, llm

router = APIRouter()


@router.post("/match", response_model=MatchResponse)
async def match_resumes(request: MatchRequest):
    job_record = await vector_store.get_job_record(request.job_id)
    if job_record is None:
        raise HTTPException(status_code=404, detail="Job not found in vector store")

    candidates = await vector_store.search_resumes(job_record.vector, request.top_k * 2)
    if not candidates:
        return MatchResponse(job_id=request.job_id, results=[])

    payload = job_record.payload
    job_text = f"Title: {payload['title']}\n\nDescription:\n{payload['description']}"
    if payload.get("requirements"):
        job_text += f"\n\nRequirements:\n{payload['requirements']}"
    if payload.get("skills"):
        job_text += f"\n\nRequired Skills: {payload['skills']}"

    async def score_candidate(candidate):
        resume_text = candidate.payload.get("raw_text", "")
        score = await llm.score_match(job_text, resume_text)
        return MatchResultItem(
            resume_id=str(candidate.id),
            vector_score=candidate.score,
            llm_score=score.score,
            reasoning=score.reasoning,
            highlights=score.highlights,
        )

    scored = await asyncio.gather(*[score_candidate(c) for c in candidates])
    ranked = sorted(
        zip(scored, candidates), key=lambda pair: pair[0].llm_score, reverse=True
    )

    # Issue #115: collapse duplicates so each unique resume — both by id and by
    # raw content (re-uploaded files produce distinct ids for identical text) —
    # appears at most once. Highest LLM score wins because list is pre-sorted.
    deduped: list[MatchResultItem] = []
    seen_ids: set[str] = set()
    seen_text_hashes: set[str] = set()
    for item, candidate in ranked:
        if item.resume_id in seen_ids:
            continue
        raw_text = (candidate.payload.get("raw_text") or "").strip()
        text_key = raw_text if raw_text else None
        if text_key and text_key in seen_text_hashes:
            continue
        seen_ids.add(item.resume_id)
        if text_key:
            seen_text_hashes.add(text_key)
        deduped.append(item)
        if len(deduped) >= request.top_k:
            break

    return MatchResponse(job_id=request.job_id, results=deduped)
