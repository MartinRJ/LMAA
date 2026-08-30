package de.lmaa.app

internal class TranscriptFallbackResolver(
    private val primary: TranscriptProvider,
    private val fallback: TranscriptProvider? = null,
) {
    suspend fun fetch(
        videoId: String,
        preferredLanguages: List<String> = listOf("de", "de-DE", "en", "en-US", "en-GB"),
        routingMode: RapidApiRoutingMode = RapidApiRoutingMode.OFF,
    ): TranscriptFetchResult = when (routingMode) {
        RapidApiRoutingMode.OFF -> primary.fetch(videoId, preferredLanguages)
        RapidApiRoutingMode.FALLBACK -> {
            val primaryResult = primary.fetch(videoId, preferredLanguages)
            if (primaryResult is TranscriptFetchResult.Success) {
                primaryResult
            } else {
                val failure = primaryResult as TranscriptFetchResult.Failure
                if (failure.code !in ALLOWED_PRIMARY_FALLBACK_ERRORS) {
                    failure
                } else {
                    fallback?.fetch(videoId, preferredLanguages)
                        ?: TranscriptFetchResult.Failure("RAPIDAPI_KEY_MISSING")
                }
            }
        }
        RapidApiRoutingMode.PREFERRED -> {
            val configuredRapidApi = fallback
            if (configuredRapidApi == null) {
                TranscriptFetchResult.Failure("RAPIDAPI_KEY_MISSING")
            } else {
                val rapidApiResult = configuredRapidApi.fetch(videoId, preferredLanguages)
                if (rapidApiResult is TranscriptFetchResult.Success) {
                    rapidApiResult
                } else {
                    val failure = rapidApiResult as TranscriptFetchResult.Failure
                    if (isTechnicalRapidApiFailure(failure.code)) {
                        primary.fetch(videoId, preferredLanguages)
                    } else {
                        failure
                    }
                }
            }
        }
    }

    private companion object {
        val ALLOWED_PRIMARY_FALLBACK_ERRORS = setOf("REQUEST_BLOCKED", "REQUEST_FAILED")

        fun isTechnicalRapidApiFailure(code: String): Boolean =
            code in setOf(
                "RAPIDAPI_QUOTA_EXCEEDED",
                "RAPIDAPI_TIMEOUT",
                "RAPIDAPI_NETWORK_ERROR",
                "RAPIDAPI_CONTENT_TYPE_MISSING",
                "RAPIDAPI_CONTENT_TYPE_NOT_ALLOWED",
                "RAPIDAPI_CHARSET_NOT_UTF8",
                "RAPIDAPI_RESPONSE_TOO_LARGE",
                "RAPIDAPI_RESPONSE_INVALID_UTF8",
                "RAPIDAPI_EMPTY_RESPONSE",
            ) || code.startsWith("RAPIDAPI_HTTP_")
    }
}
