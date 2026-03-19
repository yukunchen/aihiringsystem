from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    llm_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    vector_size: int = 1536
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    port: int = 8001
    log_level: str = "info"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
