from __future__ import annotations

import json
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).parents[2] / "main" / "python"))

import lmaa_transcript


def test_fetch_transcript_json_normalizes_synthetic_segments(monkeypatch) -> None:
    fetched = [SimpleNamespace(text="Synthetic", start=1.5, duration=2.0)]

    class FakeFetched(list):
        language_code = "de"
        is_generated = False

    selected = Mock()
    selected.fetch.return_value = FakeFetched(fetched)
    transcript_list = Mock()
    transcript_list.find_transcript.return_value = selected
    api = Mock()
    api.list.return_value = transcript_list
    monkeypatch.setattr(lmaa_transcript, "YouTubeTranscriptApi", lambda: api)

    payload = json.loads(lmaa_transcript.fetch_transcript_json("ABCDEFGHIJK", "de,en"))

    assert payload == {
        "status": "ok",
        "videoId": "ABCDEFGHIJK",
        "languageCode": "de",
        "isGenerated": False,
        "provider": "youtube-transcript-api",
        "segments": [
            {"text": "Synthetic", "startSeconds": 1.5, "durationSeconds": 2.0}
        ],
    }
    transcript_list.find_transcript.assert_called_once_with(("de", "en"))


def test_invalid_video_id_is_rejected_before_network(monkeypatch) -> None:
    api = Mock()
    monkeypatch.setattr(lmaa_transcript, "YouTubeTranscriptApi", lambda: api)

    payload = json.loads(lmaa_transcript.fetch_transcript_json("invalid", "de,en"))

    assert payload == {"status": "error", "error": "INVALID_VIDEO_ID"}
    api.list.assert_not_called()


def test_language_codes_are_filtered_and_defaulted() -> None:
    assert lmaa_transcript._parse_language_codes("de, en-US,../../bad") == (
        "de",
        "en-US",
    )
    assert lmaa_transcript._parse_language_codes("../../bad") == (
        "de",
        "de-DE",
        "en",
        "en-US",
        "en-GB",
    )
