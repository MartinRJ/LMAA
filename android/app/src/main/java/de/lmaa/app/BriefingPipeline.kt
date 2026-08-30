package de.lmaa.app

internal enum class AnalysisStage {
    TRANSCRIPT,
    METADATA,
    BRIEFING,
    PERSISTING,
}

internal data class BriefingStyleSnapshot(
    val name: String,
    val instructions: String,
    val outputLanguage: String,
) {
    val promptInstructions: String
        get() = "$instructions\nAusgabesprache: $outputLanguage"
}

internal val DEFAULT_BRIEFING_STYLE = BriefingStyleSnapshot(
    name = "Standard",
    instructions = DEFAULT_STYLE_INSTRUCTIONS,
    outputLanguage = "Deutsch",
)

internal data class CompletedAnalysis(
    val canonicalUrl: String,
    val transcript: TranscriptDocument,
    val metadata: VideoMetadata,
    val briefing: BriefingDocument,
    val style: BriefingStyleSnapshot = DEFAULT_BRIEFING_STYLE,
)

internal sealed interface AnalysisResult {
    data class Success(val analysis: CompletedAnalysis) : AnalysisResult
    data class Failure(val code: String) : AnalysisResult
}

internal interface BriefingCreator {
    suspend fun create(
        transcript: TranscriptDocument,
        metadata: VideoMetadata,
        canonicalUrl: String,
        styleName: String = "Standard",
        styleInstructions: String = DEFAULT_STYLE_INSTRUCTIONS,
    ): BriefingGenerationResult
}

/** Executes the complete user-visible analysis action in a fixed order. */
internal class BriefingPipeline(
    private val transcriptProvider: TranscriptProvider,
    private val metadataProvider: MetadataProvider,
    private val briefingCreator: BriefingCreator,
) {
    suspend fun analyze(
        input: String,
        style: BriefingStyleSnapshot = DEFAULT_BRIEFING_STYLE,
        onStageChanged: suspend (AnalysisStage) -> Unit = {},
    ): AnalysisResult {
        val parsed = when (val result = YoutubeUrlParser.parse(input)) {
            is YoutubeUrlParseResult.Success -> result
            YoutubeUrlParseResult.Error.EMPTY -> return AnalysisResult.Failure("URL_EMPTY")
            YoutubeUrlParseResult.Error.INVALID -> return AnalysisResult.Failure("URL_INVALID")
            YoutubeUrlParseResult.Error.AMBIGUOUS -> return AnalysisResult.Failure("URL_AMBIGUOUS")
        }

        onStageChanged(AnalysisStage.TRANSCRIPT)
        val transcript = when (val result = transcriptProvider.fetch(parsed.videoId)) {
            is TranscriptFetchResult.Success -> result.document
            is TranscriptFetchResult.Failure -> return AnalysisResult.Failure(result.code)
        }

        onStageChanged(AnalysisStage.METADATA)
        val metadata = when (val result = metadataProvider.fetch(parsed.videoId)) {
            is MetadataFetchResult.Success -> result.metadata
            is MetadataFetchResult.Failure -> return AnalysisResult.Failure(result.code)
        }

        onStageChanged(AnalysisStage.BRIEFING)
        val briefing = when (
            val result = briefingCreator.create(
                transcript = transcript,
                metadata = metadata,
                canonicalUrl = parsed.canonicalUrl,
                styleName = style.name,
                styleInstructions = style.promptInstructions,
            )
        ) {
            is BriefingGenerationResult.Success -> result.document
            is BriefingGenerationResult.Failure -> return AnalysisResult.Failure(result.code)
        }

        return AnalysisResult.Success(
            CompletedAnalysis(
                canonicalUrl = parsed.canonicalUrl,
                transcript = transcript,
                metadata = metadata,
                briefing = briefing,
                style = style,
            ),
        )
    }
}
