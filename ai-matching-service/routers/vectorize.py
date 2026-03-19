from fastapi import APIRouter
from schemas import VectorizeResumeRequest, VectorizeJobRequest, VectorizeResponse
from services import embedding, vector_store

router = APIRouter()


@router.post("/resume", response_model=VectorizeResponse, response_model_exclude_none=True)
async def vectorize_resume(request: VectorizeResumeRequest):
    if not request.raw_text:
        return VectorizeResponse(status="skipped", reason="no text")

    vector = await embedding.embed_text(request.raw_text)
    await vector_store.upsert_resume(request.resume_id, vector, request.raw_text)
    return VectorizeResponse(status="ok")


@router.post("/job", response_model=VectorizeResponse, response_model_exclude_none=True)
async def vectorize_job(request: VectorizeJobRequest):
    job_text = f"Title: {request.title}\n\nDescription:\n{request.description}"
    if request.requirements:
        job_text += f"\n\nRequirements:\n{request.requirements}"
    if request.skills:
        job_text += f"\n\nRequired Skills: {request.skills}"

    vector = await embedding.embed_text(job_text)
    await vector_store.upsert_job(
        request.job_id, vector, request.title,
        request.description, request.requirements, request.skills
    )
    return VectorizeResponse(status="ok")
