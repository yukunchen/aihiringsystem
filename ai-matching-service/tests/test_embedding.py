import pytest
from unittest.mock import AsyncMock, patch, MagicMock


@pytest.mark.asyncio
async def test_embed_text_returns_vector():
    mock_response = MagicMock()
    mock_response.data = [{"embedding": [0.1, 0.2, 0.3]}]

    with patch("litellm.aembedding", new=AsyncMock(return_value=mock_response)) as mock_embed:
        from services.embedding import embed_text
        result = await embed_text("hello world")

    assert result == [0.1, 0.2, 0.3]
    mock_embed.assert_called_once()
    call_kwargs = mock_embed.call_args
    assert call_kwargs.kwargs["input"] == ["hello world"]


@pytest.mark.asyncio
async def test_embed_text_uses_configured_model():
    mock_response = MagicMock()
    mock_response.data = [{"embedding": [0.5]}]

    with patch("litellm.aembedding", new=AsyncMock(return_value=mock_response)) as mock_embed:
        from services.embedding import embed_text
        await embed_text("test")

    assert mock_embed.call_args.kwargs["model"] == "text-embedding-3-small"
