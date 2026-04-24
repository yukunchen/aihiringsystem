import pytest
from unittest.mock import AsyncMock, patch


def test_vectorize_resume_with_text_returns_ok(client):
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_resume", new=AsyncMock()):
        response = client.post("/internal/vectorize/resume", json={
            "resume_id": "550e8400-e29b-41d4-a716-446655440000",
            "raw_text": "John Smith Software Engineer",
        })
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_vectorize_resume_with_null_text_returns_skipped(client):
    response = client.post("/internal/vectorize/resume", json={
        "resume_id": "550e8400-e29b-41d4-a716-446655440000",
        "raw_text": None,
    })
    assert response.status_code == 200
    assert response.json()["status"] == "skipped"
    assert "reason" in response.json()


def test_vectorize_resume_calls_embed_and_upsert(client):
    embed_mock = AsyncMock(return_value=[0.5] * 1536)
    upsert_mock = AsyncMock()

    with patch("services.embedding.embed_text", new=embed_mock), \
         patch("services.vector_store.upsert_resume", new=upsert_mock):
        client.post("/internal/vectorize/resume", json={
            "resume_id": "550e8400-e29b-41d4-a716-446655440001",
            "raw_text": "Alice developer",
        })

    embed_mock.assert_called_once_with("Alice developer")
    upsert_mock.assert_called_once_with(
        "550e8400-e29b-41d4-a716-446655440001", [0.5] * 1536, "Alice developer"
    )


def test_vectorize_job_returns_ok(client):
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_job", new=AsyncMock()):
        response = client.post("/internal/vectorize/job", json={
            "job_id": "660e8400-e29b-41d4-a716-446655440000",
            "title": "Senior Java Developer",
            "description": "Build great software",
            "requirements": "5 years Java",
            "skills": '["Java", "Spring Boot"]',
        })
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_vectorize_job_constructs_text_with_all_fields(client):
    embed_mock = AsyncMock(return_value=[0.1] * 1536)
    upsert_mock = AsyncMock()

    with patch("services.embedding.embed_text", new=embed_mock), \
         patch("services.vector_store.upsert_job", new=upsert_mock):
        client.post("/internal/vectorize/job", json={
            "job_id": "660e8400-e29b-41d4-a716-446655440001",
            "title": "Engineer",
            "description": "Core work",
            "requirements": "3yr exp",
            "skills": "Python",
        })

    embedded_text = embed_mock.call_args[0][0]
    assert "Engineer" in embedded_text
    assert "Core work" in embedded_text
    assert "3yr exp" in embedded_text
    assert "Python" in embedded_text


def test_delete_resume_vector_calls_vector_store(client):
    """Regression for issue #147: resume deletion must remove its Qdrant vector
    so stale vectors don't poison match results."""
    delete_mock = AsyncMock()
    with patch("services.vector_store.delete_resume", new=delete_mock):
        response = client.delete(
            "/internal/vectorize/resume/550e8400-e29b-41d4-a716-446655440002"
        )

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    delete_mock.assert_called_once_with("550e8400-e29b-41d4-a716-446655440002")


def test_delete_resume_vector_is_idempotent(client):
    """Deleting a non-existent vector is not an error — Qdrant delete is a no-op."""
    with patch("services.vector_store.delete_resume", new=AsyncMock()):
        response = client.delete(
            "/internal/vectorize/resume/550e8400-e29b-41d4-a716-446655440003"
        )
    assert response.status_code == 200


def test_vectorize_job_upsert_is_idempotent(client):
    """Re-vectorizing the same ID overwrites — upsert is called each time."""
    upsert_mock = AsyncMock()
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_job", new=upsert_mock):
        for _ in range(2):
            client.post("/internal/vectorize/job", json={
                "job_id": "same-job-id",
                "title": "T", "description": "D",
            })

    assert upsert_mock.call_count == 2
