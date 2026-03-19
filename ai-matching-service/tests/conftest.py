import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi.testclient import TestClient
import services.vector_store as vs


@pytest.fixture(autouse=True, scope="module")
def mock_qdrant_client():
    """Replace the Qdrant client for every test to avoid real network calls."""
    mock = AsyncMock()
    mock.get_collections = AsyncMock(return_value=MagicMock(collections=[]))
    mock.create_collection = AsyncMock()
    original = vs.client
    vs.client = mock
    yield mock
    vs.client = original


@pytest.fixture(scope="module")
def client(mock_qdrant_client):
    from main import app
    with TestClient(app) as c:
        yield c
