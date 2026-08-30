from datetime import datetime, timezone
from unittest.mock import Mock

from fastapi.testclient import TestClient

from lmaa_backend.config import Settings
from lmaa_backend.main import create_app
from lmaa_backend.metadata import VideoMetadata
from lmaa_backend.transcripts import TranscriptDocument, TranscriptSegment

VALID_MARKDOWN = """# Kernaussage
Kern.
## Kurzfassung
Kurz.
## Wichtigste Punkte
- Punkt
## Argumentation und Belege
Beleg.
## Genannte Personen, Organisationen und Quellen
Keine.
## Offene Fragen / Unsicherheiten
Keine.
## Kapitel mit Zeitmarken
- [00:00](https://www.youtube.com/watch?v=ABCDEFGHIJK&t=0s)
"""


class FakeGenerator:
    model = "gpt-5.6-sol"

    def generate(self, *, instructions: str, input_text: str, max_output_tokens: int) -> str:
        assert "Ausgabesprache: de." in instructions
        assert "Synthetic title" in input_text
        assert max_output_tokens == 6_000
        return VALID_MARKDOWN


def test_complete_briefing_contract_uses_canonical_url_and_snapshots() -> None:
    metadata_provider = Mock()
    metadata_provider.fetch.return_value = _metadata()
    transcript_provider = Mock()
    transcript_provider.fetch.return_value = _transcript()
    client = TestClient(
        create_app(
            _settings(),
            metadata_provider=metadata_provider,
            transcript_provider=transcript_provider,
            text_generator=FakeGenerator(),
        )
    )

    response = client.post("/v1/briefings", json=_request())

    assert response.status_code == 200
    body = response.json()
    assert body["job_id"] == "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
    assert body["video"]["canonical_url"] == ("https://www.youtube.com/watch?v=ABCDEFGHIJK")
    assert body["video"]["channel_id"] is None
    assert body["video"]["published_at"] is None
    assert body["video"]["duration_iso8601"] is None
    assert body["video"]["duration_seconds"] is None
    assert body["transcript"]["segments"][0]["text"] == "Synthetic transcript"
    assert body["briefing"] == {
        "model": "gpt-5.6-sol",
        "markdown": VALID_MARKDOWN,
        "style_name_snapshot": "Standard",
        "style_instructions_snapshot": "Sachlich und kompakt.",
        "output_language_snapshot": "de",
        "map_chunk_count": 1,
    }
    metadata_provider.fetch.assert_called_once_with("ABCDEFGHIJK")
    transcript_provider.fetch.assert_called_once_with("ABCDEFGHIJK", ("de", "en"))


def test_invalid_url_returns_stable_error_without_calling_providers() -> None:
    metadata_provider = Mock()
    transcript_provider = Mock()
    client = TestClient(
        create_app(
            _settings(),
            metadata_provider=metadata_provider,
            transcript_provider=transcript_provider,
            text_generator=FakeGenerator(),
        )
    )
    request = _request()
    request["url"] = "https://youtube.example/watch?v=ABCDEFGHIJK"

    response = client.post("/v1/briefings", json=request)

    assert response.status_code == 422
    assert response.json() == {
        "error": {
            "code": "invalid_youtube_url",
            "message": "Kein unterstützter YouTube-Link erkannt.",
            "retryable": False,
            "details": {},
        }
    }
    metadata_provider.fetch.assert_not_called()
    transcript_provider.fetch.assert_not_called()


def test_enabled_rapidapi_fallback_requires_key() -> None:
    client = TestClient(
        create_app(
            _settings(),
            metadata_provider=Mock(),
            transcript_provider=Mock(),
            text_generator=FakeGenerator(),
        )
    )

    response = client.post(
        "/v1/briefings",
        json=_request(),
        headers={"X-LMAA-RapidAPI-Fallback": "enabled"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "rapidapi_key_missing"


def test_schema_validation_uses_stable_error_contract() -> None:
    client = TestClient(create_app(_settings()))

    response = client.post("/v1/briefings", json={"url": "missing-fields"})

    assert response.status_code == 422
    assert response.json() == {
        "error": {
            "code": "invalid_request",
            "message": "Der Auftrag entspricht nicht dem erwarteten API-Format.",
            "retryable": False,
            "details": {},
        }
    }


def _settings() -> Settings:
    return Settings(
        environment="test",
        openai_model="gpt-5.6-sol",
        openai_api_key="",
    )


def _request() -> dict[str, object]:
    return {
        "url": "https://youtu.be/ABCDEFGHIJK",
        "style": {
            "name": "Standard",
            "instructions": "Sachlich und kompakt.",
            "output_language": "de",
        },
        "client_request_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "preferred_languages": ["de", "en"],
    }


def _metadata() -> VideoMetadata:
    timestamp = datetime(2025, 1, 2, 3, 4, 5, tzinfo=timezone.utc)
    return VideoMetadata(
        video_id="ABCDEFGHIJK",
        title="Synthetic title",
        channel_id=None,
        channel_title="Synthetic channel",
        published_at=None,
        duration_iso8601=None,
        duration_seconds=None,
        thumbnail_url="https://i.ytimg.com/synthetic.jpg",
        fetched_at=timestamp,
    )


def _transcript() -> TranscriptDocument:
    return TranscriptDocument(
        video_id="ABCDEFGHIJK",
        language_code="en",
        is_generated=False,
        provider="synthetic",
        segments=(TranscriptSegment("Synthetic transcript", 0, 1),),
    )
