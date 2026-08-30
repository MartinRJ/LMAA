from __future__ import annotations

import os
from dataclasses import dataclass, field

DEFAULT_OPENAI_MODEL = "gpt-5.6-sol"


@dataclass(frozen=True, slots=True)
class Settings:
    environment: str
    openai_model: str
    openai_api_key: str = field(repr=False)

    @classmethod
    def from_environment(cls) -> Settings:
        model = os.getenv("LMAA_OPENAI_MODEL", DEFAULT_OPENAI_MODEL).strip()
        if not model:
            raise ValueError("LMAA_OPENAI_MODEL darf nicht leer sein")

        return cls(
            environment=os.getenv("LMAA_ENVIRONMENT", "development").strip() or "development",
            openai_model=model,
            openai_api_key=os.getenv("LMAA_OPENAI_API_KEY", "").strip(),
        )

    @property
    def readiness(self) -> dict[str, bool]:
        return {
            "openai_api_key_configured": bool(self.openai_api_key),
            "openai_model_configured": bool(self.openai_model),
        }
