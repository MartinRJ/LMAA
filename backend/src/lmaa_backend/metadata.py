from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Protocol

import httpx


class MetadataProviderError(RuntimeError):
    """Der Metadatenprovider konnte keine verwertbare Antwort liefern."""


class VideoNotFound(MetadataProviderError):
    """Die validierte Video-ID ist beim Metadatenprovider nicht verfügbar."""


@dataclass(frozen=True, slots=True)
class VideoMetadata:
    video_id: str
    title: str
    channel_id: str | None
    channel_title: str
    published_at: datetime | None
    duration_iso8601: str | None
    duration_seconds: int | None
    thumbnail_url: str
    fetched_at: datetime


class MetadataProvider(Protocol):
    def fetch(self, video_id: str) -> VideoMetadata: ...


class YoutubeOEmbedMetadataProvider:
    ENDPOINT = "https://www.youtube.com/oembed"

    def __init__(self, client: httpx.Client | None = None) -> None:
        self._client = client or httpx.Client(timeout=30.0, follow_redirects=False)

    def fetch(self, video_id: str) -> VideoMetadata:
        canonical_url = f"https://www.youtube.com/watch?v={video_id}"
        try:
            response = self._client.get(
                self.ENDPOINT,
                params={"url": canonical_url, "format": "json"},
            )
            if response.status_code == 404:
                raise VideoNotFound("Video wurde über YouTube-oEmbed nicht gefunden")
            response.raise_for_status()
            payload = response.json()
        except VideoNotFound:
            raise
        except (httpx.HTTPError, ValueError) as exc:
            status_code = getattr(getattr(exc, "response", None), "status_code", None)
            suffix = f" (HTTP {status_code})" if status_code else ""
            raise MetadataProviderError(f"YouTube-oEmbed-Abruf fehlgeschlagen{suffix}") from None

        if not isinstance(payload, dict):
            raise MetadataProviderError("Ungültige YouTube-oEmbed-Antwort")
        try:
            return VideoMetadata(
                video_id=video_id,
                title=_required_text(payload.get("title"), "Titel"),
                channel_id=None,
                channel_title=_required_text(payload.get("author_name"), "Kanalname"),
                published_at=None,
                duration_iso8601=None,
                duration_seconds=None,
                thumbnail_url=_required_https_url(payload.get("thumbnail_url"), "Thumbnail"),
                fetched_at=datetime.now(timezone.utc),
            )
        except ValueError as exc:
            raise MetadataProviderError(f"Ungültige YouTube-oEmbed-Metadaten: {exc}") from None


class YoutubeDataApiMetadataProvider:
    """Inaktiver Pro-Adapter; im MVP nicht verdrahtet und ohne Laufzeitkonfiguration."""

    ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"

    def __init__(self, api_key: str, client: httpx.Client | None = None) -> None:
        if not api_key.strip():
            raise ValueError("YouTube-Data-API-Key darf nicht leer sein")
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=30.0, follow_redirects=False)

    def fetch(self, video_id: str) -> VideoMetadata:
        try:
            response = self._client.get(
                self.ENDPOINT,
                params={
                    "part": "snippet,contentDetails",
                    "id": video_id,
                    "key": self._api_key,
                    "fields": (
                        "items(id,snippet(title,channelId,channelTitle,publishedAt,thumbnails),"
                        "contentDetails(duration))"
                    ),
                },
            )
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            status_code = getattr(getattr(exc, "response", None), "status_code", None)
            suffix = f" (HTTP {status_code})" if status_code else ""
            raise MetadataProviderError(f"YouTube-Metadatenabruf fehlgeschlagen{suffix}") from None

        items = payload.get("items") if isinstance(payload, dict) else None
        if not isinstance(items, list) or not items:
            raise VideoNotFound("Video wurde über die YouTube Data API nicht gefunden")

        try:
            item = items[0]
            snippet = item["snippet"]
            content_details = item["contentDetails"]
            published_at = _parse_datetime(snippet["publishedAt"])
            duration_iso8601 = str(content_details["duration"])
            return VideoMetadata(
                video_id=str(item["id"]),
                title=_required_text(snippet["title"], "Titel"),
                channel_id=_required_text(snippet["channelId"], "Kanal-ID"),
                channel_title=_required_text(snippet["channelTitle"], "Kanalname"),
                published_at=published_at,
                duration_iso8601=duration_iso8601,
                duration_seconds=parse_iso8601_duration(duration_iso8601),
                thumbnail_url=_select_thumbnail(snippet.get("thumbnails")),
                fetched_at=datetime.now(timezone.utc),
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise MetadataProviderError(f"Ungültige YouTube-Metadaten: {exc}") from None


_DURATION_PATTERN = re.compile(
    r"^P(?:(?P<days>\d+)D)?T(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?$"
)


def parse_iso8601_duration(value: str) -> int:
    match = _DURATION_PATTERN.fullmatch(value)
    if match is None:
        raise ValueError("Ungültige ISO-8601-Videodauer")
    values = {name: int(raw or 0) for name, raw in match.groupdict().items()}
    return (
        values["days"] * 86_400
        + values["hours"] * 3_600
        + values["minutes"] * 60
        + values["seconds"]
    )


def _parse_datetime(value: object) -> datetime:
    if not isinstance(value, str):
        raise ValueError("Ungültiges Veröffentlichungsdatum")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Veröffentlichungsdatum ohne Zeitzone")
    return parsed


def _required_text(value: object, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field_name} fehlt")
    return value.strip()


def _select_thumbnail(value: object) -> str:
    if not isinstance(value, dict):
        raise ValueError("Thumbnail fehlt")
    candidates: list[tuple[int, str]] = []
    for thumbnail in value.values():
        if not isinstance(thumbnail, dict):
            continue
        url = thumbnail.get("url")
        width = thumbnail.get("width", 0)
        if isinstance(url, str) and url.startswith("https://"):
            candidates.append((int(width) if isinstance(width, int) else 0, url))
    if not candidates:
        raise ValueError("Kein erlaubtes HTTPS-Thumbnail vorhanden")
    return max(candidates)[1]


def _required_https_url(value: object, field_name: str) -> str:
    text = _required_text(value, field_name)
    parsed = httpx.URL(text)
    if parsed.scheme != "https" or not parsed.host or parsed.userinfo or parsed.port is not None:
        raise ValueError(f"{field_name} ist keine erlaubte HTTPS-URL")
    return text
