"""Local youtube-transcript-api adapter used through the Chaquopy bridge."""

from __future__ import annotations

import json
import re
from collections.abc import Iterable
from typing import Any

from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import (
    AgeRestricted,
    InvalidVideoId,
    IpBlocked,
    NoTranscriptFound,
    RequestBlocked,
    TranscriptsDisabled,
    VideoUnavailable,
    VideoUnplayable,
    YouTubeDataUnparsable,
    YouTubeRequestFailed,
)

_VIDEO_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{11}$")
_LANGUAGE_CODE_PATTERN = re.compile(r"^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$")
_DEFAULT_LANGUAGES = ("de", "de-DE", "en", "en-US", "en-GB")


def fetch_transcript_json(video_id: str, language_codes_csv: str = "de,en") -> str:
    """Fetch and normalize a transcript without exposing exception text."""
    try:
        language_codes = _parse_language_codes(language_codes_csv)
        document = _fetch_transcript(video_id, language_codes, YouTubeTranscriptApi())
        payload: dict[str, Any] = {"status": "ok", **document}
    except Exception as exc:  # noqa: BLE001
        # No Python exception or provider text may cross the Kotlin boundary.
        payload = {"status": "error", "error": _error_code(exc)}
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def _fetch_transcript(
    video_id: str,
    language_codes: Iterable[str],
    api: Any,
) -> dict[str, Any]:
    if not isinstance(video_id, str) or not _VIDEO_ID_PATTERN.fullmatch(video_id):
        raise InvalidVideoId(video_id)

    preferred_languages = tuple(language_codes) or _DEFAULT_LANGUAGES
    available_transcripts = api.list(video_id)
    transcript = available_transcripts.find_transcript(preferred_languages)
    fetched = transcript.fetch()
    segments = [
        {
            "text": snippet.text,
            "startSeconds": float(snippet.start),
            "durationSeconds": float(snippet.duration),
        }
        for snippet in fetched
    ]
    if not segments:
        raise NoTranscriptFound(video_id, preferred_languages, available_transcripts)

    return {
        "videoId": video_id,
        "languageCode": fetched.language_code,
        "isGenerated": bool(fetched.is_generated),
        "provider": "youtube-transcript-api",
        "segments": segments,
    }


def _parse_language_codes(value: str) -> tuple[str, ...]:
    if not isinstance(value, str):
        return _DEFAULT_LANGUAGES
    parsed = tuple(
        code
        for raw_code in value.split(",")
        if (code := raw_code.strip()) and _LANGUAGE_CODE_PATTERN.fullmatch(code)
    )
    return parsed or _DEFAULT_LANGUAGES


def _error_code(exc: Exception) -> str:
    if isinstance(exc, InvalidVideoId):
        return "INVALID_VIDEO_ID"
    if isinstance(exc, TranscriptsDisabled):
        return "TRANSCRIPTS_DISABLED"
    if isinstance(exc, NoTranscriptFound):
        return "NO_TRANSCRIPT"
    if isinstance(exc, (AgeRestricted, VideoUnavailable, VideoUnplayable)):
        return "VIDEO_UNAVAILABLE"
    if isinstance(exc, (RequestBlocked, IpBlocked)):
        return "REQUEST_BLOCKED"
    if isinstance(exc, (YouTubeRequestFailed, YouTubeDataUnparsable)):
        return "REQUEST_FAILED"
    return "INTERNAL_ERROR"
