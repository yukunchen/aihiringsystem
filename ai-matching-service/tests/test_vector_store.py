import pytest
from unittest.mock import AsyncMock, MagicMock, patch
import services.vector_store as vs


@pytest.fixture(autouse=True)
def mock_client():
    mock = AsyncMock()
    mock.get_collections = AsyncMock(return_value=MagicMock(collections=[]))
    mock.create_collection = AsyncMock()
    vs.client = mock
    return mock


@pytest.mark.asyncio
async def test_ensure_collections_creates_missing():
    mock = vs.client
    mock.get_collections.return_value = MagicMock(collections=[])

    await vs.ensure_collections()

    assert mock.create_collection.call_count == 2
    names_created = {
        call.kwargs["collection_name"]
        for call in mock.create_collection.call_args_list
    }
    assert names_created == {"resumes", "jobs"}


@pytest.mark.asyncio
async def test_ensure_collections_skips_existing():
    existing_col = MagicMock()
    existing_col.name = "resumes"
    vs.client.get_collections.return_value = MagicMock(collections=[existing_col])

    await vs.ensure_collections()

    # Only "jobs" should be created
    assert vs.client.create_collection.call_count == 1
    assert vs.client.create_collection.call_args.kwargs["collection_name"] == "jobs"


@pytest.mark.asyncio
async def test_upsert_resume_stores_vector_and_text():
    await vs.upsert_resume("resume-123", [0.1, 0.2], "John Smith resume text")

    vs.client.upsert.assert_called_once()
    call_kwargs = vs.client.upsert.call_args.kwargs
    assert call_kwargs["collection_name"] == "resumes"
    point = call_kwargs["points"][0]
    assert str(point.id) == "resume-123"
    assert point.vector == [0.1, 0.2]
    assert point.payload["resume_id"] == "resume-123"
    assert point.payload["raw_text"] == "John Smith resume text"


@pytest.mark.asyncio
async def test_upsert_job_stores_vector_and_payload():
    await vs.upsert_job("job-456", [0.3, 0.4], "Engineer", "Build things", "5yr exp", "Java")

    vs.client.upsert.assert_called_once()
    call_kwargs = vs.client.upsert.call_args.kwargs
    assert call_kwargs["collection_name"] == "jobs"
    point = call_kwargs["points"][0]
    assert point.payload["job_id"] == "job-456"
    assert point.payload["title"] == "Engineer"


@pytest.mark.asyncio
async def test_get_job_vector_returns_vector_and_payload():
    mock_record = MagicMock()
    mock_record.vector = [0.5, 0.6]
    mock_record.payload = {"job_id": "job-456", "title": "Engineer", "description": "Build things"}
    vs.client.retrieve = AsyncMock(return_value=[mock_record])

    result = await vs.get_job_record("job-456")

    assert result is not None
    assert result.vector == [0.5, 0.6]
    vs.client.retrieve.assert_called_once_with(
        collection_name="jobs", ids=["job-456"], with_vectors=True
    )


@pytest.mark.asyncio
async def test_get_job_vector_returns_none_when_not_found():
    vs.client.retrieve = AsyncMock(return_value=[])

    result = await vs.get_job_record("missing-job")

    assert result is None


@pytest.mark.asyncio
async def test_search_resumes_returns_scored_points():
    mock_point = MagicMock()
    mock_point.id = "resume-123"
    mock_point.score = 0.92
    mock_point.payload = {"resume_id": "resume-123", "raw_text": "some text"}
    vs.client.search = AsyncMock(return_value=[mock_point])

    results = await vs.search_resumes([0.1, 0.2], top_k=5)

    assert len(results) == 1
    assert results[0].score == 0.92
    vs.client.search.assert_called_once_with(
        collection_name="resumes", query_vector=[0.1, 0.2], limit=5
    )
