import litellm
from config import settings
from schemas import MatchScore

SCORING_SYSTEM_PROMPT = """You are a recruiting assistant. Score how well this resume matches the job description.

Return JSON only:
{
  "score": <integer 0-100>,
  "reasoning": "<2-3 sentences explaining the match quality>",
  "highlights": ["<key matching point>", ...]
}

Score guide: 80-100 = strong match, 60-79 = good match, 40-59 = partial match, 0-39 = weak match."""


async def score_match(job_text: str, resume_text: str) -> MatchScore:
    response = await litellm.acompletion(
        model=settings.llm_model,
        messages=[
            {"role": "system", "content": SCORING_SYSTEM_PROMPT},
            {"role": "user", "content": f"Job:\n{job_text}\n\nResume:\n{resume_text}"},
        ],
        response_format={"type": "json_object"},
        temperature=0.1,
    )
    return MatchScore.model_validate_json(response.choices[0].message.content)
