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

    raw_candidates = await vector_store.search_resumes(job_record.vector, request.top_k * 5)
    if not raw_candidates:
        return MatchResponse(job_id=request.job_id, results=[])

    # Deduplicate by raw_text content — keep highest vector score per unique resume
    seen_texts: dict[str, object] = {}
    candidates = []
    for c in raw_candidates:
        text = c.payload.get("raw_text", "")
        text_key = text[:200]  # Use first 200 chars as dedup key
        if text_key not in seen_texts:
            seen_texts[text_key] = True
            candidates.append(c)
        if len(candidates) >= request.top_k * 2:
            break

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

    results = await asyncio.gather(*[score_candidate(c) for c in candidates])
    ranked = sorted(results, key=lambda r: r.llm_score, reverse=True)
    return MatchResponse(job_id=request.job_id, results=ranked[: request.top_k])
