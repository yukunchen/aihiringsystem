import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from schemas import MatchScore


def _make_scored_point(resume_id: str, score: float, raw_text: str):
    p = MagicMock()
    p.id = resume_id
    p.score = score
    p.payload = {"resume_id": resume_id, "raw_text": raw_text}
    return p


def _make_job_record(job_id: str):
    r = MagicMock()
    r.vector = [0.5] * 1536
    r.payload = {
        "job_id": job_id, "title": "Engineer",
        "description": "Build things", "requirements": "5yr", "skills": "Java",
    }
    return r


def test_match_returns_ranked_results(client):
    job_record = _make_job_record("job-001")
    candidates = [
        _make_scored_point("resume-a", 0.85, "Alice Java"),
        _make_scored_point("resume-b", 0.80, "Bob Python"),
    ]
    scores = [
        MatchScore(score=90, reasoning="Strong match", highlights=["Java"]),
        MatchScore(score=60, reasoning="Partial", highlights=[]),
    ]

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=candidates)), \
         patch("services.llm.score_match", new=AsyncMock(side_effect=scores)):
        response = client.post("/match", json={"job_id": "job-001", "top_k": 2})

    assert response.status_code == 200
    data = response.json()
    assert data["job_id"] == "job-001"
    assert len(data["results"]) == 2
    # Sorted by llm_score descending
    assert data["results"][0]["llm_score"] == 90
    assert data["results"][1]["llm_score"] == 60


def test_match_returns_404_when_job_not_vectorized(client):
    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=None)):
        response = client.post("/match", json={"job_id": "missing-job", "top_k": 5})

    assert response.status_code == 404
    assert "not found" in response.json()["detail"].lower()


def test_match_returns_empty_results_when_no_resumes(client):
    job_record = _make_job_record("job-002")

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=[])):
        response = client.post("/match", json={"job_id": "job-002", "top_k": 10})

    assert response.status_code == 200
    assert response.json()["results"] == []


def test_match_overfetches_from_qdrant(client):
    job_record = _make_job_record("job-003")
    search_mock = AsyncMock(return_value=[])

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=search_mock):
        client.post("/match", json={"job_id": "job-003", "top_k": 5})

    # Should search for top_k * 2 = 10 candidates
    search_mock.assert_called_once_with(job_record.vector, 10)


def test_match_result_includes_vector_score(client):
    job_record = _make_job_record("job-004")
    candidates = [_make_scored_point("resume-x", 0.93, "Expert")]
    mock_score = MatchScore(score=88, reasoning="Great", highlights=["skill"])

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=candidates)), \
         patch("services.llm.score_match", new=AsyncMock(return_value=mock_score)):
        response = client.post("/match", json={"job_id": "job-004", "top_k": 1})

    result = response.json()["results"][0]
    assert result["resume_id"] == "resume-x"
    assert result["vector_score"] == 0.93
    assert result["llm_score"] == 88


def test_match_top_k_max_50(client):
    response = client.post("/match", json={"job_id": "job-999", "top_k": 51})
    assert response.status_code == 422
