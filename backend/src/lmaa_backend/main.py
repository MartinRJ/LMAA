from dataclasses import dataclass

import httpx
from fastapi import FastAPI, Header, Response, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from openai import OpenAIError
from youtube_transcript_api._errors import CouldNotRetrieveTranscript

from lmaa_backend import __version__
from lmaa_backend.briefings import BriefingService, OpenAITextGenerator, TextGenerator
from lmaa_backend.config import Settings
from lmaa_backend.metadata import (
    MetadataProvider,
    MetadataProviderError,
    VideoNotFound,
    YoutubeOEmbedMetadataProvider,
)
from lmaa_backend.schemas import (
    BriefingPayloadResponse,
    BriefingRequest,
    BriefingResponse,
    ErrorBody,
    ErrorResponse,
    HealthResponse,
    ReadinessChecks,
    ReadinessResponse,
    TranscriptResponse,
    TranscriptSegmentResponse,
    VideoMetadataResponse,
)
from lmaa_backend.transcripts import (
    PrimaryTranscriptProvider,
    RapidApiTranscriptProvider,
    TranscriptProvider,
    TranscriptResolver,
)
from lmaa_backend.youtube_url import InvalidYoutubeUrl, normalize_youtube_url


@dataclass(frozen=True, slots=True)
class ApiFailure(Exception):
    status_code: int
    code: str
    message: str
    retryable: bool = False
    details: dict[str, str] | None = None


def create_app(
    settings: Settings | None = None,
    *,
    metadata_provider: MetadataProvider | None = None,
    transcript_provider: TranscriptProvider | None = None,
    text_generator: TextGenerator | None = None,
) -> FastAPI:
    current_settings = settings or Settings.from_environment()
    app = FastAPI(
        title="LMAA Backend",
        version=__version__,
        docs_url="/docs" if current_settings.environment == "development" else None,
        redoc_url=None,
    )

    @app.exception_handler(ApiFailure)
    async def api_failure_handler(_request: object, exc: ApiFailure) -> JSONResponse:
        payload = ErrorResponse(
            error=ErrorBody(
                code=exc.code,
                message=exc.message,
                retryable=exc.retryable,
                details=exc.details or {},
            )
        )
        return JSONResponse(status_code=exc.status_code, content=payload.model_dump(mode="json"))

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(
        _request: object, _exc: RequestValidationError
    ) -> JSONResponse:
        payload = ErrorResponse(
            error=ErrorBody(
                code="invalid_request",
                message="Der Auftrag entspricht nicht dem erwarteten API-Format.",
                retryable=False,
            )
        )
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content=payload.model_dump(mode="json"),
        )

    @app.get("/healthz", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(status="ok", service="lmaa-backend", version=__version__)

    @app.get(
        "/readyz",
        response_model=ReadinessResponse,
        responses={status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ReadinessResponse}},
    )
    def readiness(response: Response) -> ReadinessResponse:
        configured = current_settings.readiness
        is_ready = all(configured.values())
        if not is_ready:
            response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

        return ReadinessResponse(
            status="ready" if is_ready else "not_ready",
            model=current_settings.openai_model,
            checks=ReadinessChecks(
                **configured,
                provider_access_verified=False,
            ),
        )

    @app.post(
        "/v1/briefings",
        response_model=BriefingResponse,
        responses={
            status.HTTP_400_BAD_REQUEST: {"model": ErrorResponse},
            status.HTTP_404_NOT_FOUND: {"model": ErrorResponse},
            status.HTTP_422_UNPROCESSABLE_CONTENT: {"model": ErrorResponse},
            status.HTTP_502_BAD_GATEWAY: {"model": ErrorResponse},
            status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ErrorResponse},
        },
    )
    def create_briefing(
        request: BriefingRequest,
        rapidapi_fallback: str | None = Header(default=None, alias="X-LMAA-RapidAPI-Fallback"),
        rapidapi_key: str | None = Header(default=None, alias="X-LMAA-RapidAPI-Key"),
    ) -> BriefingResponse:
        try:
            reference = normalize_youtube_url(request.url)
        except InvalidYoutubeUrl as exc:
            raise ApiFailure(
                status.HTTP_422_UNPROCESSABLE_CONTENT,
                "invalid_youtube_url",
                str(exc),
            ) from None

        if not current_settings.openai_api_key and text_generator is None:
            raise ApiFailure(
                status.HTTP_503_SERVICE_UNAVAILABLE,
                "openai_not_configured",
                "OpenAI ist serverseitig noch nicht konfiguriert.",
            )

        fallback_enabled = rapidapi_fallback == "enabled"
        if fallback_enabled and not (rapidapi_key or "").strip():
            raise ApiFailure(
                status.HTTP_400_BAD_REQUEST,
                "rapidapi_key_missing",
                "Der RapidAPI-Fallback wurde ohne Key angefordert.",
            )

        try:
            effective_metadata_provider = metadata_provider or YoutubeOEmbedMetadataProvider()
            metadata = effective_metadata_provider.fetch(reference.video_id)

            primary = transcript_provider or PrimaryTranscriptProvider()
            fallback = RapidApiTranscriptProvider(rapidapi_key or "") if fallback_enabled else None
            transcript = TranscriptResolver(primary, fallback).fetch(
                reference.video_id,
                tuple(request.preferred_languages),
                fallback_enabled=fallback_enabled,
            )

            generator = text_generator or OpenAITextGenerator(
                current_settings.openai_api_key,
                current_settings.openai_model,
            )
            effective_style = (
                f"Ausgabesprache: {request.style.output_language}.\n{request.style.instructions}"
            )
            briefing = BriefingService(generator).create(
                transcript,
                canonical_url=reference.canonical_url,
                title=metadata.title,
                channel_title=metadata.channel_title,
                style_name=request.style.name,
                style_instructions=effective_style,
            )
        except VideoNotFound:
            raise ApiFailure(
                status.HTTP_404_NOT_FOUND,
                "video_not_found",
                "Das Video wurde nicht gefunden oder ist nicht öffentlich verfügbar.",
            ) from None
        except MetadataProviderError:
            raise ApiFailure(
                status.HTTP_502_BAD_GATEWAY,
                "metadata_provider_error",
                "YouTube-Metadaten konnten nicht abgerufen werden.",
                retryable=True,
            ) from None
        except CouldNotRetrieveTranscript:
            raise ApiFailure(
                status.HTTP_502_BAD_GATEWAY,
                "transcript_unavailable",
                "Für dieses Video konnte kein Transkript abgerufen werden.",
                retryable=False,
            ) from None
        except (httpx.HTTPError, OpenAIError):
            raise ApiFailure(
                status.HTTP_502_BAD_GATEWAY,
                "provider_error",
                "Ein externer Provider konnte den Auftrag nicht abschließen.",
                retryable=True,
            ) from None

        return BriefingResponse(
            job_id=request.client_request_id,
            status="completed",
            video=VideoMetadataResponse(
                video_id=metadata.video_id,
                canonical_url=reference.canonical_url,
                title=metadata.title,
                channel_id=metadata.channel_id,
                channel_title=metadata.channel_title,
                published_at=metadata.published_at,
                duration_iso8601=metadata.duration_iso8601,
                duration_seconds=metadata.duration_seconds,
                thumbnail_url=metadata.thumbnail_url,
                fetched_at=metadata.fetched_at,
            ),
            transcript=TranscriptResponse(
                language_code=transcript.language_code,
                is_generated=transcript.is_generated,
                provider=transcript.provider,
                segments=[
                    TranscriptSegmentResponse(
                        text=segment.text,
                        start_seconds=segment.start_seconds,
                        duration_seconds=segment.duration_seconds,
                    )
                    for segment in transcript.segments
                ],
            ),
            briefing=BriefingPayloadResponse(
                model=briefing.model,
                markdown=briefing.markdown,
                style_name_snapshot=request.style.name,
                style_instructions_snapshot=request.style.instructions,
                output_language_snapshot=request.style.output_language,
                map_chunk_count=briefing.map_chunk_count,
            ),
        )

    return app


app = create_app()
