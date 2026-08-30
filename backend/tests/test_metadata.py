from unittest.mock import Mock

import httpx
import pytest

from lmaa_backend.metadata import (
    MetadataProviderError,
    YoutubeDataApiMetadataProvider,
    YoutubeOEmbedMetadataProvider,
    parse_iso8601_duration,
)


def test_oembed_metadata_is_keyless_and_missing_fields_are_null() -> None:
    response = httpx.Response(
        200,
        json={
            "title": "Synthetic title",
            "author_name": "Synthetic channel",
            "thumbnail_url": "https://i.ytimg.com/synthetic.jpg",
            "html": "<iframe src='https://www.youtube.com/embed/ABCDEFGHIJK'></iframe>",
        },
        request=httpx.Request("GET", YoutubeOEmbedMetadataProvider.ENDPOINT),
    )
    client = Mock()
    client.get.return_value = response

    metadata = YoutubeOEmbedMetadataProvider(client=client).fetch("ABCDEFGHIJK")

    assert metadata.title == "Synthetic title"
    assert metadata.channel_title == "Synthetic channel"
    assert metadata.channel_id is None
    assert metadata.published_at is None
    assert metadata.duration_iso8601 is None
    assert metadata.duration_seconds is None
    _, kwargs = client.get.call_args
    assert kwargs["params"] == {
        "url": "https://www.youtube.com/watch?v=ABCDEFGHIJK",
        "format": "json",
    }
    assert "html" not in metadata.__dataclass_fields__


def test_oembed_rejects_non_https_thumbnail() -> None:
    response = httpx.Response(
        200,
        json={
            "title": "Synthetic title",
            "author_name": "Synthetic channel",
            "thumbnail_url": "http://example.com/synthetic.jpg",
        },
        request=httpx.Request("GET", YoutubeOEmbedMetadataProvider.ENDPOINT),
    )
    client = Mock()
    client.get.return_value = response

    with pytest.raises(MetadataProviderError):
        YoutubeOEmbedMetadataProvider(client=client).fetch("ABCDEFGHIJK")


def test_youtube_metadata_is_normalized_without_exposing_key() -> None:
    response = httpx.Response(
        200,
        json={
            "items": [
                {
                    "id": "ABCDEFGHIJK",
                    "snippet": {
                        "title": "Synthetic title",
                        "channelId": "synthetic-channel",
                        "channelTitle": "Synthetic channel",
                        "publishedAt": "2025-01-02T03:04:05Z",
                        "thumbnails": {
                            "default": {
                                "url": "https://i.ytimg.com/default.jpg",
                                "width": 120,
                            },
                            "high": {
                                "url": "https://i.ytimg.com/high.jpg",
                                "width": 480,
                            },
                        },
                    },
                    "contentDetails": {"duration": "PT1H2M3S"},
                }
            ]
        },
        request=httpx.Request("GET", YoutubeDataApiMetadataProvider.ENDPOINT),
    )
    client = Mock()
    client.get.return_value = response
    provider = YoutubeDataApiMetadataProvider("synthetic-secret", client=client)

    metadata = provider.fetch("ABCDEFGHIJK")

    assert metadata.duration_seconds == 3_723
    assert metadata.thumbnail_url == "https://i.ytimg.com/high.jpg"
    _, kwargs = client.get.call_args
    assert kwargs["params"]["key"] == "synthetic-secret"
    assert "synthetic-secret" not in repr(provider)


def test_metadata_http_error_is_redacted() -> None:
    request = httpx.Request(
        "GET",
        f"{YoutubeDataApiMetadataProvider.ENDPOINT}?key=synthetic-secret",
    )
    client = Mock()
    client.get.return_value = httpx.Response(403, request=request)
    provider = YoutubeDataApiMetadataProvider("synthetic-secret", client=client)

    with pytest.raises(MetadataProviderError) as caught:
        provider.fetch("ABCDEFGHIJK")

    assert "synthetic-secret" not in str(caught.value)
    assert "HTTP 403" in str(caught.value)


@pytest.mark.parametrize(
    ("value", "seconds"),
    [("PT45S", 45), ("PT2M", 120), ("PT1H2M3S", 3_723), ("P1DT1S", 86_401)],
)
def test_iso8601_duration(value: str, seconds: int) -> None:
    assert parse_iso8601_duration(value) == seconds


def test_invalid_iso8601_duration_is_rejected() -> None:
    with pytest.raises(ValueError):
        parse_iso8601_duration("1:23")
