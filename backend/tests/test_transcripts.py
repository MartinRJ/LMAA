from unittest.mock import Mock

import httpx
import pytest
from youtube_transcript_api._errors import RequestBlocked, TranscriptsDisabled

from lmaa_backend.transcripts import (
    RapidApiTranscriptProvider,
    TranscriptDocument,
    TranscriptResolver,
    TranscriptSegment,
)


def test_rapidapi_response_is_normalized_without_persisting_key() -> None:
    response = httpx.Response(
        200,
        json={
            "lang": "en",
            "content": [{"text": "Synthetic", "offset": 1_500, "duration": 2_000}],
        },
        request=httpx.Request("GET", RapidApiTranscriptProvider.ENDPOINT),
    )
    client = Mock()
    client.get.return_value = response
    provider = RapidApiTranscriptProvider("synthetic-secret", client=client)

    document = provider.fetch("ABCDEFGHIJK", ("en",))

    assert document.provider == "rapidapi"
    assert document.segments == (TranscriptSegment("Synthetic", 1.5, 2.0),)
    _, kwargs = client.get.call_args
    assert kwargs["headers"]["x-rapidapi-key"] == "synthetic-secret"
    assert "synthetic-secret" not in repr(provider)


def test_resolver_uses_fallback_only_for_allowed_technical_error() -> None:
    primary = Mock()
    fallback = Mock()
    primary.fetch.side_effect = RequestBlocked("ABCDEFGHIJK")
    fallback.fetch.return_value = _document("fallback")

    result = TranscriptResolver(primary, fallback).fetch(
        "ABCDEFGHIJK",
        fallback_enabled=True,
    )

    assert result.provider == "fallback"
    fallback.fetch.assert_called_once()


def test_resolver_does_not_fallback_for_disabled_captions() -> None:
    primary = Mock()
    fallback = Mock()
    primary.fetch.side_effect = TranscriptsDisabled("ABCDEFGHIJK")

    with pytest.raises(TranscriptsDisabled):
        TranscriptResolver(primary, fallback).fetch(
            "ABCDEFGHIJK",
            fallback_enabled=True,
        )

    fallback.fetch.assert_not_called()


def _document(provider: str) -> TranscriptDocument:
    return TranscriptDocument(
        "ABCDEFGHIJK",
        "en",
        False,
        provider,
        (TranscriptSegment("Synthetic", 0, 1),),
    )
