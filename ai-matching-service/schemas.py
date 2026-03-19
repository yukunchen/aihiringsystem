from pydantic import BaseModel, Field
from typing import Optional


class VectorizeResumeRequest(BaseModel):
    resume_id: str
    raw_text: Optional[str] = None


class VectorizeJobRequest(BaseModel):
    job_id: str
    title: str
    description: str
    requirements: Optional[str] = None
    skills: Optional[str] = None


class VectorizeResponse(BaseModel):
    status: str
    reason: Optional[str] = None


class MatchRequest(BaseModel):
    job_id: str
    top_k: int = Field(default=10, ge=1, le=50)


class MatchResultItem(BaseModel):
    resume_id: str
    vector_score: float
    llm_score: int
    reasoning: str
    highlights: list[str]


class MatchResponse(BaseModel):
    job_id: str
    results: list[MatchResultItem]


class MatchScore(BaseModel):
    score: int
    reasoning: str
    highlights: list[str]


class HealthResponse(BaseModel):
    status: str
    qdrant: str
    llm_model: str
    embedding_model: str
