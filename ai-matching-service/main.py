import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from config import settings
from schemas import HealthResponse
import services.vector_store as vector_store
from routers import vectorize, match

logging.basicConfig(level=settings.log_level.upper())


@asynccontextmanager
async def lifespan(app: FastAPI):
    await vector_store.ensure_collections()
    yield


app = FastAPI(title="AI Matching Service", lifespan=lifespan)

app.include_router(vectorize.router, prefix="/internal/vectorize")
app.include_router(match.router)


@app.get("/health", response_model=HealthResponse)
async def health():
    try:
        await vector_store.client.get_collections()
        qdrant_status = "ok"
    except Exception:
        qdrant_status = "error"

    return HealthResponse(
        status="ok",
        qdrant=qdrant_status,
        llm_model=settings.llm_model,
        embedding_model=settings.embedding_model,
    )
