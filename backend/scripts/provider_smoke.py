from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from lmaa_backend.config import DEFAULT_OPENAI_MODEL
from lmaa_backend.youtube_url import normalize_youtube_url

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OPENAI_KEY_FILE = REPOSITORY_ROOT / "OpenAI API KEY.txt"
RAPIDAPI_KEY_FILE = REPOSITORY_ROOT / "youtube-transcripts Key.txt"
USAGE_FILE = Path(__file__).resolve().parents[1] / "provider-smoke-usage.json"
RAPIDAPI_HOST = "youtube-transcripts.p.rapidapi.com"
RAPIDAPI_URL = f"https://{RAPIDAPI_HOST}/youtube/transcript"
DEFAULT_VIDEO_ID = "dQw4w9WgXcQ"


def main() -> int:
    args = _parse_args()
    try:
        if args.provider == "openai":
            result = smoke_openai()
        elif args.provider == "primary-transcript":
            result = smoke_primary_transcript(args.video_id)
        elif args.provider == "youtube-metadata":
            result = smoke_youtube_metadata(args.video_id)
        else:
            result = smoke_rapidapi(args.video_id)
    except Exception as error:  # noqa: BLE001 - CLI-Grenze redigiert Providerfehler.
        print(
            json.dumps(
                {
                    "status": "failed",
                    "provider": args.provider,
                    "error_type": type(error).__name__,
                }
            ),
            file=sys.stderr,
        )
        return 1

    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


def smoke_openai() -> dict[str, Any]:
    from openai import OpenAI

    api_key = _read_secret(OPENAI_KEY_FILE)
    response = OpenAI(api_key=api_key, timeout=180.0, max_retries=0).responses.create(
        model=DEFAULT_OPENAI_MODEL,
        instructions=(
            "Dies ist ein technischer Verfügbarkeitstest. Antworte ausschließlich mit LMAA_OK."
        ),
        input="Bestätige die Erreichbarkeit.",
        max_output_tokens=32,
        reasoning={"effort": "none"},
        store=False,
        tools=[],
    )
    if response.output_text.strip() != "LMAA_OK":
        raise RuntimeError("Unerwartete Modellantwort")
    return {"status": "ok", "provider": "openai", "model": response.model}


def smoke_primary_transcript(video_id: str) -> dict[str, Any]:
    from youtube_transcript_api import YouTubeTranscriptApi

    reference = normalize_youtube_url(f"https://youtu.be/{video_id}")
    transcript = YouTubeTranscriptApi().fetch(reference.video_id, languages=["de", "en"])
    return {
        "status": "ok",
        "provider": "youtube-transcript-api",
        "video_id": reference.video_id,
        "language_code": transcript.language_code,
        "is_generated": transcript.is_generated,
        "segment_count": len(transcript),
    }


def smoke_youtube_metadata(video_id: str) -> dict[str, Any]:
    from lmaa_backend.metadata import YoutubeOEmbedMetadataProvider

    reference = normalize_youtube_url(f"https://youtu.be/{video_id}")
    provider = YoutubeOEmbedMetadataProvider()
    metadata = provider.fetch(reference.video_id)
    return {
        "status": "ok",
        "provider": "youtube-oembed",
        "video_id": metadata.video_id,
        "published_at": metadata.published_at,
        "channel_id": metadata.channel_id,
        "duration_seconds": metadata.duration_seconds,
        "thumbnail_is_https": metadata.thumbnail_url.startswith("https://"),
    }


def smoke_rapidapi(video_id: str) -> dict[str, Any]:
    import httpx

    reference = normalize_youtube_url(f"https://youtu.be/{video_id}")
    api_key = _read_secret(RAPIDAPI_KEY_FILE)
    attempted_at = _utc_now()
    response = None
    try:
        response = httpx.get(
            RAPIDAPI_URL,
            params={
                "url": reference.canonical_url,
                "videoId": reference.video_id,
                "chunkSize": 100,
                "text": "false",
                "lang": "en",
            },
            headers={
                "x-rapidapi-host": RAPIDAPI_HOST,
                "x-rapidapi-key": api_key,
            },
            timeout=30.0,
            follow_redirects=False,
        )
        response.raise_for_status()
        payload = response.json()
        content = payload.get("content")
        if not isinstance(content, list) or not content:
            raise RuntimeError("RapidAPI-Antwort enthält kein Transkript")
    except Exception as error:
        status_code = response.status_code if response is not None else None
        _record_usage(
            provider="rapidapi",
            attempted_at=attempted_at,
            success=False,
            details={
                "http_status": status_code,
                "error_type": type(error).__name__,
            },
        )
        raise

    details = {
        "http_status": response.status_code,
        "language_code": payload.get("lang"),
        "segment_count": len(content),
    }
    usage = _record_usage(
        provider="rapidapi",
        attempted_at=attempted_at,
        success=True,
        details=details,
    )
    return {
        "status": "ok",
        "provider": "rapidapi",
        **details,
        "attempted_requests_this_month": usage["attempted_requests_this_month"],
        "estimated_remaining_requests": usage["estimated_remaining_requests"],
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


def _record_usage(
    *,
    provider: str,
    attempted_at: str,
    success: bool,
    details: dict[str, Any],
) -> dict[str, Any]:
    usage = json.loads(USAGE_FILE.read_text(encoding="utf-8"))
    event = {
        "attempted_at": attempted_at,
        "success": success,
        **details,
    }
    usage.setdefault("events", {}).setdefault(provider, []).append(event)

    if provider == "rapidapi":
        month = attempted_at[:7]
        monthly_events = [
            item for item in usage["events"][provider] if item["attempted_at"].startswith(month)
        ]
        limit = usage["rapidapi"]["monthly_limit"]
        usage["rapidapi"].update(
            {
                "month": month,
                "attempted_requests_this_month": len(monthly_events),
                "successful_requests_this_month": sum(
                    1 for item in monthly_events if item["success"]
                ),
                "estimated_remaining_requests": max(0, limit - len(monthly_events)),
            }
        )

    USAGE_FILE.write_text(
        json.dumps(usage, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return usage.get(provider, {})


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Kostenbegrenzte, secret-freie Live-Smokes für LMAA-Provider.",
    )
    parser.add_argument(
        "provider",
        choices=("openai", "primary-transcript", "rapidapi", "youtube-metadata"),
    )
    parser.add_argument("--video-id", default=DEFAULT_VIDEO_ID)
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
