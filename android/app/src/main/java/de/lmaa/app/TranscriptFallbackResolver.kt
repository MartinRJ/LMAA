package de.lmaa.app

internal class TranscriptFallbackResolver(
    private val primary: TranscriptProvider,
    private val fallback: TranscriptProvider? = null,
) {
    suspend fun fetch(
        videoId: String,
        preferredLanguages: List<String> = listOf("de", "de-DE", "en", "en-US", "en-GB"),
        fallbackEnabled: Boolean = false,
    ): TranscriptFetchResult {
        val primaryResult = primary.fetch(videoId, preferredLanguages)
        if (primaryResult is TranscriptFetchResult.Success) return primaryResult

        val primaryFailure = primaryResult as TranscriptFetchResult.Failure
        if (!fallbackEnabled || primaryFailure.code !in ALLOWED_FALLBACK_ERRORS) {
            return primaryFailure
        }
        val configuredFallback = fallback
            ?: return TranscriptFetchResult.Failure("RAPIDAPI_KEY_MISSING")
        return configuredFallback.fetch(videoId, preferredLanguages)
    }

    private companion object {
        val ALLOWED_FALLBACK_ERRORS = setOf("REQUEST_BLOCKED", "REQUEST_FAILED")
    }
}
