from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class StrictResponseModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class HealthResponse(StrictResponseModel):
    status: Literal["ok"]
    service: Literal["lmaa-backend"]
    version: str


class ReadinessChecks(StrictResponseModel):
    openai_api_key_configured: bool
    openai_model_configured: bool
    provider_access_verified: bool


class ReadinessResponse(StrictResponseModel):
    status: Literal["ready", "not_ready"]
    model: str
    checks: ReadinessChecks


class YoutubeVideoReference(StrictResponseModel):
    video_id: str
    canonical_url: str


class BriefingStyleRequest(StrictResponseModel):
    name: str = Field(min_length=1, max_length=80)
    instructions: str = Field(min_length=1, max_length=8_000)
    output_language: str = Field(default="de", pattern=r"^[a-z]{2,3}(?:-[A-Z]{2})?$")


LanguageCode = Annotated[
    str,
    Field(min_length=2, max_length=6, pattern=r"^[a-z]{2,3}(?:-[A-Z]{2})?$"),
]


class BriefingRequest(StrictResponseModel):
    url: str = Field(min_length=1, max_length=2_048)
    style: BriefingStyleRequest
    client_request_id: UUID
    preferred_languages: list[LanguageCode] = Field(
        default_factory=lambda: ["de", "en"], min_length=1, max_length=5
    )


class VideoMetadataResponse(StrictResponseModel):
    video_id: str
    canonical_url: str
    title: str
    channel_id: str | None
    channel_title: str
    published_at: datetime | None
    duration_iso8601: str | None
    duration_seconds: int | None
    thumbnail_url: str
    fetched_at: datetime


class TranscriptSegmentResponse(StrictResponseModel):
    text: str
    start_seconds: float
    duration_seconds: float


class TranscriptResponse(StrictResponseModel):
    language_code: str
    is_generated: bool
    provider: str
    segments: list[TranscriptSegmentResponse]


class BriefingPayloadResponse(StrictResponseModel):
    model: str
    markdown: str
    style_name_snapshot: str
    style_instructions_snapshot: str
    output_language_snapshot: str
    map_chunk_count: int


class BriefingResponse(StrictResponseModel):
    job_id: UUID
    status: Literal["completed"]
    video: VideoMetadataResponse
    transcript: TranscriptResponse
    briefing: BriefingPayloadResponse


class ErrorBody(StrictResponseModel):
    code: str
    message: str
    retryable: bool
    details: dict[str, str] = Field(default_factory=dict)


class ErrorResponse(StrictResponseModel):
    error: ErrorBody
