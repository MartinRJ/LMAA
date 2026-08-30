from __future__ import annotations

import re
from urllib.parse import parse_qs, urlsplit

from lmaa_backend.schemas import YoutubeVideoReference

_VIDEO_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{11}$")
_URL_PATTERN = re.compile(r"https?://[^\s<>\"']+", re.IGNORECASE)
_TRAILING_SHARE_PUNCTUATION = ".,;:!?)]}"
_LONG_HOSTS = frozenset({"youtube.com", "www.youtube.com", "m.youtube.com"})
_SHORT_HOST = "youtu.be"


class InvalidYoutubeUrl(ValueError):
    """Die Eingabe enthält nicht genau eine unterstützte, gültige YouTube-URL."""


def normalize_youtube_url(shared_text: str) -> YoutubeVideoReference:
    if not shared_text or not shared_text.strip():
        raise InvalidYoutubeUrl("Bitte einen YouTube-Link eingeben.")

    candidates = [
        match.group(0).rstrip(_TRAILING_SHARE_PUNCTUATION)
        for match in _URL_PATTERN.finditer(shared_text)
    ]
    valid_references: list[YoutubeVideoReference] = []

    for candidate in candidates:
        try:
            valid_references.append(_normalize_candidate(candidate))
        except (InvalidYoutubeUrl, ValueError):
            continue

    unique_references = {reference.video_id: reference for reference in valid_references}
    if len(unique_references) == 1:
        return next(iter(unique_references.values()))
    if len(unique_references) > 1:
        raise InvalidYoutubeUrl("Bitte nur einen YouTube-Link auf einmal eingeben.")
    raise InvalidYoutubeUrl("Kein unterstützter YouTube-Link erkannt.")


def _normalize_candidate(candidate: str) -> YoutubeVideoReference:
    parsed = urlsplit(candidate)
    if parsed.scheme.lower() not in {"http", "https"}:
        raise InvalidYoutubeUrl("Nicht unterstütztes URL-Schema.")
    if parsed.username or parsed.password:
        raise InvalidYoutubeUrl("URLs mit Zugangsdaten sind nicht erlaubt.")
    if parsed.port is not None:
        raise InvalidYoutubeUrl("Explizite Ports sind nicht erlaubt.")

    host = (parsed.hostname or "").lower()
    if host == _SHORT_HOST:
        video_id = _video_id_from_short_path(parsed.path)
    elif host in _LONG_HOSTS:
        video_id = _video_id_from_youtube_path(parsed.path, parsed.query)
    else:
        raise InvalidYoutubeUrl("Nicht erlaubter YouTube-Host.")

    if not _VIDEO_ID_PATTERN.fullmatch(video_id):
        raise InvalidYoutubeUrl("Ungültige YouTube-Video-ID.")

    return YoutubeVideoReference(
        video_id=video_id,
        canonical_url=f"https://www.youtube.com/watch?v={video_id}",
    )


def _video_id_from_short_path(path: str) -> str:
    if "%" in path:
        raise InvalidYoutubeUrl("Kodierte Pfadsegmente sind nicht erlaubt.")
    segments = [segment for segment in path.split("/") if segment]
    if len(segments) != 1:
        raise InvalidYoutubeUrl("Ungültiger youtu.be-Pfad.")
    return segments[0]


def _video_id_from_youtube_path(path: str, query: str) -> str:
    if "%" in path:
        raise InvalidYoutubeUrl("Kodierte Pfadsegmente sind nicht erlaubt.")
    segments = [segment for segment in path.split("/") if segment]

    if segments == ["watch"]:
        values = parse_qs(query, keep_blank_values=True).get("v", [])
        if len(values) != 1:
            raise InvalidYoutubeUrl("Es muss genau eine Video-ID vorhanden sein.")
        return values[0]

    if len(segments) == 2 and segments[0] in {"shorts", "live"}:
        return segments[1]

    raise InvalidYoutubeUrl("Nicht unterstützter YouTube-Pfad.")
