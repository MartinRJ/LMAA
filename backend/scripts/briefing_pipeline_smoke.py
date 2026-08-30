from __future__ import annotations

import argparse
import json
import sys
from typing import Any

import provider_smoke

from lmaa_backend.briefings import DEFAULT_HEADINGS, BriefingService, OpenAITextGenerator
from lmaa_backend.transcripts import PrimaryTranscriptProvider, RapidApiTranscriptProvider
from lmaa_backend.youtube_url import normalize_youtube_url


def main() -> int:
    args = _parse_args()
    reference = normalize_youtube_url(f"https://youtu.be/{args.video_id}")
    rapid_attempted_at = None

    try:
        if args.transcript_provider == "primary":
            transcript = PrimaryTranscriptProvider().fetch(
                reference.video_id,
                ("de", "en"),
            )
        else:
            rapid_attempted_at = provider_smoke._utc_now()
            rapid_key = provider_smoke._read_secret(provider_smoke.RAPIDAPI_KEY_FILE)
            transcript = RapidApiTranscriptProvider(rapid_key).fetch(
                reference.video_id,
                ("de", "en"),
            )
            provider_smoke._record_usage(
                provider="rapidapi",
                attempted_at=rapid_attempted_at,
                success=True,
                details={"http_status": 200, "purpose": "briefing_pipeline_smoke"},
            )

        openai_key = provider_smoke._read_secret(provider_smoke.OPENAI_KEY_FILE)
        service = BriefingService(
            OpenAITextGenerator(openai_key),
            chunk_character_limit=args.chunk_character_limit,
        )
        result = service.create(
            transcript,
            canonical_url=reference.canonical_url,
        )
    except Exception as error:  # noqa: BLE001 - Ausgabe muss Providerdetails redigieren.
        if rapid_attempted_at is not None and "transcript" not in locals():
            provider_smoke._record_usage(
                provider="rapidapi",
                attempted_at=rapid_attempted_at,
                success=False,
                details={
                    "error_type": type(error).__name__,
                    "purpose": "briefing_pipeline_smoke",
                },
            )
        print(
            json.dumps(
                {
                    "status": "failed",
                    "error_type": type(error).__name__,
                    "transcript_provider": args.transcript_provider,
                }
            ),
            file=sys.stderr,
        )
        return 1

    summary: dict[str, Any] = {
        "status": "ok",
        "video_id": reference.video_id,
        "transcript_provider": transcript.provider,
        "language_code": transcript.language_code,
        "is_generated": transcript.is_generated,
        "segment_count": len(transcript.segments),
        "map_chunk_count": result.map_chunk_count,
        "model": result.model,
        "markdown_character_count": len(result.markdown),
        "validated_headings": list(DEFAULT_HEADINGS),
    }
    if args.transcript_provider == "rapidapi":
        usage = json.loads(provider_smoke.USAGE_FILE.read_text(encoding="utf-8"))["rapidapi"]
        summary["rapidapi_attempted_requests_this_month"] = usage["attempted_requests_this_month"]
        summary["rapidapi_estimated_remaining_requests"] = usage["estimated_remaining_requests"]
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 0


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Live-Test des vollständigen Transkript-zu-Briefing-Pfads.",
    )
    parser.add_argument("--video-id", required=True)
    parser.add_argument(
        "--transcript-provider",
        choices=("primary", "rapidapi"),
        default="primary",
    )
    parser.add_argument("--chunk-character-limit", type=int, default=80_000)
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
