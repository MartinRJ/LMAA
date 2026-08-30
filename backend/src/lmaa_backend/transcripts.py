from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

import httpx
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import IpBlocked, RequestBlocked, YouTubeRequestFailed


@dataclass(frozen=True, slots=True)
class TranscriptSegment:
    text: str
    start_seconds: float
    duration_seconds: float


@dataclass(frozen=True, slots=True)
class TranscriptDocument:
    video_id: str
    language_code: str
    is_generated: bool
    provider: str
    segments: tuple[TranscriptSegment, ...]


class TranscriptProvider(Protocol):
    def fetch(self, video_id: str, languages: Sequence[str]) -> TranscriptDocument: ...


class PrimaryTranscriptProvider:
    def __init__(self, api: YouTubeTranscriptApi | None = None) -> None:
        self._api = api or YouTubeTranscriptApi()

    def fetch(self, video_id: str, languages: Sequence[str]) -> TranscriptDocument:
        fetched = self._api.fetch(video_id, languages=languages)
        return TranscriptDocument(
            video_id=video_id,
            language_code=fetched.language_code,
            is_generated=fetched.is_generated,
            provider="youtube-transcript-api",
            segments=tuple(
                TranscriptSegment(
                    text=snippet.text,
                    start_seconds=snippet.start,
                    duration_seconds=snippet.duration,
                )
                for snippet in fetched
            ),
        )


class RapidApiTranscriptProvider:
    HOST = "youtube-transcripts.p.rapidapi.com"
    ENDPOINT = f"https://{HOST}/youtube/transcript"

    def __init__(self, api_key: str, client: httpx.Client | None = None) -> None:
        if not api_key.strip():
            raise ValueError("RapidAPI-Key darf nicht leer sein")
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=30.0, follow_redirects=False)

    def fetch(self, video_id: str, languages: Sequence[str]) -> TranscriptDocument:
        language = next(iter(languages), "en")
        response = self._client.get(
            self.ENDPOINT,
            params={
                "url": f"https://www.youtube.com/watch?v={video_id}",
                "videoId": video_id,
                "chunkSize": 100,
                "text": "false",
                "lang": language,
            },
            headers={
                "x-rapidapi-host": self.HOST,
                "x-rapidapi-key": self._api_key,
            },
        )
        response.raise_for_status()
        payload = response.json()
        content = payload.get("content")
        if not isinstance(content, list) or not content:
            raise ValueError("RapidAPI-Antwort enthält kein Transkript")

        return TranscriptDocument(
            video_id=video_id,
            language_code=str(payload.get("lang") or language),
            is_generated=bool(payload.get("isGenerated", False)),
            provider="rapidapi",
            segments=tuple(_rapidapi_segment(item) for item in content),
        )


class TranscriptResolver:
    def __init__(
        self,
        primary: TranscriptProvider,
        fallback: TranscriptProvider | None = None,
    ) -> None:
        self._primary = primary
        self._fallback = fallback

    def fetch(
        self,
        video_id: str,
        languages: Sequence[str] = ("de", "en"),
        *,
        fallback_enabled: bool = False,
    ) -> TranscriptDocument:
        try:
            return self._primary.fetch(video_id, languages)
        except (RequestBlocked, IpBlocked, YouTubeRequestFailed):
            if not fallback_enabled or self._fallback is None:
                raise
            return self._fallback.fetch(video_id, languages)


def _rapidapi_segment(item: object) -> TranscriptSegment:
    if not isinstance(item, dict):
        raise ValueError("Ungültiges RapidAPI-Segment")
    text = item.get("text")
    offset = item.get("offset", item.get("start", 0))
    duration = item.get("duration", 0)
    if not isinstance(text, str) or not text.strip():
        raise ValueError("RapidAPI-Segment ohne Text")
    if not isinstance(offset, (int, float)) or not isinstance(duration, (int, float)):
        raise ValueError("RapidAPI-Segment mit ungültiger Zeitangabe")

    # Der Provider liefert offset/duration in Millisekunden.
    return TranscriptSegment(
        text=text,
        start_seconds=max(0.0, float(offset) / 1000.0),
        duration_seconds=max(0.0, float(duration) / 1000.0),
    )
