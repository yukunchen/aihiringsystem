import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch


@pytest.mark.asyncio
async def test_score_match_returns_match_score():
    mock_content = json.dumps({"score": 85, "reasoning": "Strong Java background", "highlights": ["Java", "Spring"]})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)):
        from services.llm import score_match
        result = await score_match("Java developer role", "John Smith Java engineer")

    assert result.score == 85
    assert result.reasoning == "Strong Java background"
    assert result.highlights == ["Java", "Spring"]


@pytest.mark.asyncio
async def test_score_match_uses_configured_model():
    mock_content = json.dumps({"score": 70, "reasoning": "OK match", "highlights": []})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)) as mock_call:
        from services.llm import score_match
        await score_match("job text", "resume text")

    call_kwargs = mock_call.call_args.kwargs
    assert call_kwargs["model"] == "gpt-4o-mini"
    assert call_kwargs["temperature"] == 0.1
    assert call_kwargs["response_format"] == {"type": "json_object"}


@pytest.mark.asyncio
async def test_score_match_sends_job_and_resume_in_prompt():
    mock_content = json.dumps({"score": 50, "reasoning": "Partial", "highlights": []})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)) as mock_call:
        from services.llm import score_match
        await score_match("Senior Engineer role", "Alice Python developer")

    messages = mock_call.call_args.kwargs["messages"]
    user_message = next(m for m in messages if m["role"] == "user")
    assert "Senior Engineer role" in user_message["content"]
    assert "Alice Python developer" in user_message["content"]
