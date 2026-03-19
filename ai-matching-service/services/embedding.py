import litellm
from config import settings


async def embed_text(text: str) -> list[float]:
    response = await litellm.aembedding(
        model=settings.embedding_model,
        input=[text]
    )
    return response.data[0]["embedding"]
