"""Regression test for docker-compose environment variable consistency.

Issue #103: staging used AI_MATCHING_URL instead of AI_MATCHING_BASE_URL,
causing the backend to fall back to localhost and fail to reach the AI
service inside Docker.
"""

import pathlib
import re

import pytest

PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE_DIR = PROJECT_ROOT / "docker"


def _parse_backend_env_vars(compose_path: pathlib.Path) -> dict[str, str]:
    """Extract environment variables from the backend service block."""
    text = compose_path.read_text()
    in_backend = False
    in_env = False
    env_vars: dict[str, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        # detect service blocks (top-level keys under 'services:')
        if re.match(r"^\w", line) and not line.startswith(" "):
            in_backend = False
            in_env = False
        if stripped.startswith("backend:"):
            in_backend = True
            continue
        if in_backend and stripped == "environment:":
            in_env = True
            continue
        if in_backend and in_env and stripped.startswith("- "):
            kv = stripped.lstrip("- ").strip()
            if "=" in kv:
                key, value = kv.split("=", 1)
                env_vars[key] = value
            continue
        if in_env and stripped and not stripped.startswith("-"):
            in_env = False
    return env_vars


@pytest.mark.parametrize(
    "env_name",
    ["dev", "staging", "prod"],
)
def test_ai_matching_base_url_env_var_name(env_name: str):
    """All compose files must use AI_MATCHING_BASE_URL (not AI_MATCHING_URL).

    The Spring property ai.matching.base-url binds to AI_MATCHING_BASE_URL.
    A typo (e.g. AI_MATCHING_URL) silently falls back to localhost, which
    breaks container-to-container communication.
    """
    compose_path = COMPOSE_DIR / env_name / "docker-compose.yml"
    if not compose_path.exists():
        pytest.skip(f"{compose_path} not found")

    env_vars = _parse_backend_env_vars(compose_path)

    assert "AI_MATCHING_URL" not in env_vars, (
        f"{env_name}/docker-compose.yml uses AI_MATCHING_URL — "
        f"must be AI_MATCHING_BASE_URL to match Spring property ai.matching.base-url"
    )
    assert "AI_MATCHING_BASE_URL" in env_vars, (
        f"{env_name}/docker-compose.yml is missing AI_MATCHING_BASE_URL"
    )
