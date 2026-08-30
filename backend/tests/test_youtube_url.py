import pytest

from lmaa_backend.youtube_url import InvalidYoutubeUrl, normalize_youtube_url

VIDEO_ID = "dQw4w9WgXcQ"
CANONICAL_URL = f"https://www.youtube.com/watch?v={VIDEO_ID}"


@pytest.mark.parametrize(
    "value",
    [
        f"https://www.youtube.com/watch?v={VIDEO_ID}",
        f"https://youtube.com/watch?v={VIDEO_ID}&t=12",
        f"http://m.youtube.com/watch?feature=share&v={VIDEO_ID}",
        f"https://youtu.be/{VIDEO_ID}?si=synthetic",
        f"https://www.youtube.com/shorts/{VIDEO_ID}",
        f"https://www.youtube.com/live/{VIDEO_ID}?feature=share",
        f"Schau dir das an: https://youtu.be/{VIDEO_ID}?si=synthetic Danke!",
    ],
)
def test_supported_urls_are_canonicalized(value: str) -> None:
    reference = normalize_youtube_url(value)

    assert reference.video_id == VIDEO_ID
    assert reference.canonical_url == CANONICAL_URL


@pytest.mark.parametrize(
    "value",
    [
        "",
        "kein Link",
        f"https://youtube.com.evil.example/watch?v={VIDEO_ID}",
        f"https://youtube.com@evil.example/watch?v={VIDEO_ID}",
        f"https://www.youtube.com:8443/watch?v={VIDEO_ID}",
        "https://www.youtube.com/watch?v=short",
        f"https://www.youtube.com/embed/{VIDEO_ID}",
        f"https://youtu.be/{VIDEO_ID}/extra",
        f"https://www.youtube.com/watch?v={VIDEO_ID}&v=AAAAAAAAAAA",
        f"ftp://www.youtube.com/watch?v={VIDEO_ID}",
    ],
)
def test_invalid_or_unsafe_urls_are_rejected(value: str) -> None:
    with pytest.raises(InvalidYoutubeUrl):
        normalize_youtube_url(value)


def test_two_different_valid_video_urls_are_rejected() -> None:
    with pytest.raises(InvalidYoutubeUrl, match="nur einen"):
        normalize_youtube_url(f"https://youtu.be/{VIDEO_ID} https://youtu.be/AAAAAAAAAAA")
