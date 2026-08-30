from dataclasses import dataclass, field

import pytest

from lmaa_backend.briefings import (
    REQUIRED_HEADINGS,
    BriefingService,
    InvalidBriefingOutput,
    chunk_transcript,
)
from lmaa_backend.transcripts import TranscriptDocument, TranscriptSegment


@dataclass
class FakeGenerator:
    responses: list[str]
    model: str = "gpt-5.6-sol"
    calls: list[dict[str, object]] = field(default_factory=list)

    def generate(
        self,
        *,
        instructions: str,
        input_text: str,
        max_output_tokens: int,
    ) -> str:
        self.calls.append(
            {
                "instructions": instructions,
                "input_text": input_text,
                "max_output_tokens": max_output_tokens,
            }
        )
        return self.responses.pop(0)


def test_single_chunk_creates_validated_briefing() -> None:
    generator = FakeGenerator([_valid_markdown()])
    service = BriefingService(generator)

    result = service.create(
        _transcript(), canonical_url="https://www.youtube.com/watch?v=ABCDEFGHIJK"
    )

    assert result.model == "gpt-5.6-sol"
    assert result.map_chunk_count == 1
    assert len(generator.calls) == 1
    assert "UNTRUSTED_TRANSKRIPT" in str(generator.calls[0]["input_text"])
    assert "keine Tools" in str(generator.calls[0]["instructions"])


def test_long_transcript_uses_deterministic_map_reduce() -> None:
    segments = tuple(
        TranscriptSegment(
            text=f"Segment {index} " + "x" * 600, start_seconds=index, duration_seconds=1
        )
        for index in range(6)
    )
    transcript = TranscriptDocument("ABCDEFGHIJK", "de", True, "primary", segments)
    chunks = chunk_transcript(segments, 1_000)
    generator = FakeGenerator(["Teilzusammenfassung"] * len(chunks) + [_valid_markdown()])

    result = BriefingService(generator, chunk_character_limit=1_000).create(
        transcript,
        canonical_url="https://www.youtube.com/watch?v=ABCDEFGHIJK",
    )

    assert result.map_chunk_count == len(chunks)
    assert len(chunks) > 1
    assert len(generator.calls) == len(chunks) + 1
    reduced_input = str(generator.calls[-1]["input_text"])
    assert "Teil 1/" in reduced_input
    assert f"Teil {len(chunks)}/{len(chunks)}" in reduced_input


def test_transcript_control_characters_and_line_breaks_are_neutralized() -> None:
    segment = TranscriptSegment("erste\nzweite\x00 Zeile", 65, 1)

    assert chunk_transcript((segment,), 1_000) == (("[00:01:05] erste zweite Zeile"),)


def test_missing_required_heading_is_rejected() -> None:
    generator = FakeGenerator(["# Kernaussage\nUnvollständig"])

    with pytest.raises(InvalidBriefingOutput):
        BriefingService(generator).create(
            _transcript(),
            canonical_url="https://www.youtube.com/watch?v=ABCDEFGHIJK",
        )


def _transcript() -> TranscriptDocument:
    return TranscriptDocument(
        video_id="ABCDEFGHIJK",
        language_code="de",
        is_generated=False,
        provider="synthetic",
        segments=(TranscriptSegment("Rein synthetischer Testinhalt.", 0, 2),),
    )


def _valid_markdown() -> str:
    return "\n\n".join(f"{heading}\nTest" for heading in REQUIRED_HEADINGS)
