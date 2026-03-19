from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from config import settings

client = AsyncQdrantClient(host=settings.qdrant_host, port=settings.qdrant_port)


async def ensure_collections():
    existing = await client.get_collections()
    existing_names = {c.name for c in existing.collections}

    if "resumes" not in existing_names:
        await client.create_collection(
            collection_name="resumes",
            vectors_config=VectorParams(size=settings.vector_size, distance=Distance.COSINE),
        )

    if "jobs" not in existing_names:
        await client.create_collection(
            collection_name="jobs",
            vectors_config=VectorParams(size=settings.vector_size, distance=Distance.COSINE),
        )


async def upsert_resume(resume_id: str, vector: list[float], raw_text: str):
    await client.upsert(
        collection_name="resumes",
        points=[PointStruct(
            id=resume_id,
            vector=vector,
            payload={"resume_id": resume_id, "raw_text": raw_text},
        )],
    )


async def upsert_job(
    job_id: str, vector: list[float], title: str,
    description: str, requirements: str | None, skills: str | None
):
    await client.upsert(
        collection_name="jobs",
        points=[PointStruct(
            id=job_id,
            vector=vector,
            payload={
                "job_id": job_id,
                "title": title,
                "description": description,
                "requirements": requirements,
                "skills": skills,
            },
        )],
    )


async def get_job_record(job_id: str):
    results = await client.retrieve(
        collection_name="jobs", ids=[job_id], with_vectors=True
    )
    return results[0] if results else None


async def search_resumes(job_vector: list[float], top_k: int):
    return await client.search(
        collection_name="resumes", query_vector=job_vector, limit=top_k
    )
