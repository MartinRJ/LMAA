from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

from lmaa_backend.briefings import DEFAULT_HEADINGS
from lmaa_backend.config import DEFAULT_OPENAI_MODEL, Settings
from lmaa_backend.main import create_app

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OPENAI_KEY_FILE = REPOSITORY_ROOT / "OpenAI API KEY.txt"
DEFAULT_VIDEO_ID = "dQw4w9WgXcQ"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Vollständiger In-Process-Smoke für den LMAA-API-Vertrag.",
    )
    parser.add_argument("--video-id", default=DEFAULT_VIDEO_ID)
    args = parser.parse_args()

    try:
        result = smoke_api_pipeline(args.video_id)
    except Exception as error:  # noqa: BLE001 - CLI-Grenze redigiert Providerfehler.
        print(
            json.dumps({"status": "failed", "error_type": type(error).__name__}),
            file=sys.stderr,
        )
        return 1

    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


def smoke_api_pipeline(video_id: str) -> dict[str, object]:
    settings = Settings(
        environment="test",
        openai_model=DEFAULT_OPENAI_MODEL,
        openai_api_key=_read_secret(OPENAI_KEY_FILE),
    )
    client = TestClient(create_app(settings))
    response = client.post(
        "/v1/briefings",
        json={
            "url": f"https://youtu.be/{video_id}",
            "style": {
                "name": "Standard",
                "instructions": "Erstelle ein sachliches, informationsdichtes Briefing.",
                "output_language": "de",
            },
            "client_request_id": str(uuid4()),
            "preferred_languages": ["de", "en"],
        },
    )
    if response.status_code != 200:
        error_code = response.json().get("error", {}).get("code", "unknown")
        raise RuntimeError(f"API-Smoke fehlgeschlagen: {error_code}")

    payload = response.json()
    markdown = payload["briefing"]["markdown"]
    if not all(heading in markdown for heading in DEFAULT_HEADINGS):
        raise RuntimeError("Briefing enthält nicht alle Pflichtüberschriften")

    return {
        "status": "ok",
        "model": payload["briefing"]["model"],
        "video_id": payload["video"]["video_id"],
        "metadata_complete": all(
            payload["video"].get(field)
            for field in (
                "title",
                "channel_title",
                "thumbnail_url",
            )
        ),
        "transcript_provider": payload["transcript"]["provider"],
        "transcript_segment_count": len(payload["transcript"]["segments"]),
        "map_chunk_count": payload["briefing"]["map_chunk_count"],
        "markdown_character_count": len(markdown),
        "required_headings_present": True,
    }


def _read_secret(path: Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(path.name)
    value = path.read_text(encoding="utf-8-sig").strip()
    if "=" in value and "\n" not in value:
        value = value.split("=", maxsplit=1)[1].strip()
    if not value or "\n" in value or "\r" in value:
        raise ValueError(f"{path.name} enthält keinen einzelnen gültigen Wert")
    return value


if __name__ == "__main__":
    raise SystemExit(main())
