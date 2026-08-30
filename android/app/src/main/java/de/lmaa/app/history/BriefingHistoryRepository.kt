package de.lmaa.app.history

import de.lmaa.app.CompletedAnalysis
import java.util.UUID
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

internal data class BriefingHistoryItem(
    val briefingId: Long,
    val title: String,
    val channelTitle: String,
    val model: String,
    val styleName: String,
    val createdAtEpochMillis: Long,
)

internal data class StoredBriefing(
    val briefingId: Long,
    val canonicalUrl: String,
    val title: String,
    val channelTitle: String,
    val model: String,
    val styleName: String,
    val styleInstructions: String,
    val styleOutputLanguage: String,
    val transcriptLanguage: String,
    val transcriptProvider: String,
    val markdown: String,
    val createdAtEpochMillis: Long,
)

internal class BriefingHistoryRepository(
    private val dao: BriefingDao,
) {
    val history = dao.observeHistory().map { rows ->
        rows.map { row ->
            BriefingHistoryItem(
                briefingId = row.briefingId,
                title = row.title,
                channelTitle = row.channelTitle,
                model = row.model,
                styleName = row.styleName,
                createdAtEpochMillis = row.createdAtEpochMillis,
            )
        }
    }

    suspend fun save(analysis: CompletedAnalysis): StoredBriefing {
        val now = System.currentTimeMillis()
        val briefingId = dao.persistCompletedAnalysis(
            video = videoEntity(analysis),
            transcript = transcriptEntity(analysis, now),
            briefing = briefingEntity(analysis, now),
        )
        return requireStoredBriefing(briefingId)
    }

    suspend fun saveForJob(jobId: UUID, analysis: CompletedAnalysis): StoredBriefing {
        val now = System.currentTimeMillis()
        val briefingId = dao.persistCompletedAnalysisForJob(
            jobId = jobId.toString(),
            video = videoEntity(analysis),
            transcript = transcriptEntity(analysis, now),
            briefing = briefingEntity(analysis, now),
            completedAt = now,
        )
        return requireStoredBriefing(briefingId)
    }

    suspend fun find(briefingId: Long): StoredBriefing? =
        dao.findBriefing(briefingId)?.toModel()

    suspend fun findLatest(canonicalUrl: String): StoredBriefing? =
        dao.findLatestBriefing(canonicalUrl)?.toModel()

    suspend fun delete(briefingId: Long): Boolean =
        dao.deleteBriefingWithOwnedData(briefingId, System.currentTimeMillis())

    private suspend fun requireStoredBriefing(briefingId: Long): StoredBriefing =
        requireNotNull(dao.findBriefing(briefingId)) {
            "Gespeichertes Briefing ist nicht lesbar"
        }.toModel()

    private fun videoEntity(analysis: CompletedAnalysis) = VideoEntity(
        videoId = analysis.transcript.videoId,
        canonicalUrl = analysis.canonicalUrl,
        title = analysis.metadata.title,
        channelId = analysis.metadata.channelId,
        channelTitle = analysis.metadata.channelTitle,
        publishedAtEpochMillis = analysis.metadata.publishedAt?.toEpochMilli(),
        durationIso8601 = analysis.metadata.durationIso8601,
        durationSeconds = analysis.metadata.durationSeconds,
        thumbnailUrl = analysis.metadata.thumbnailUrl,
        fetchedAtEpochMillis = analysis.metadata.fetchedAt.toEpochMilli(),
    )

    private fun transcriptEntity(analysis: CompletedAnalysis, now: Long) = TranscriptEntity(
        videoId = analysis.transcript.videoId,
        provider = analysis.transcript.provider,
        languageCode = analysis.transcript.languageCode,
        isGenerated = analysis.transcript.isGenerated,
        segmentsJson = encodeSegments(analysis),
        plainText = analysis.transcript.segments.joinToString("\n") { it.text },
        fetchedAtEpochMillis = now,
    )

    private fun briefingEntity(analysis: CompletedAnalysis, now: Long) = BriefingEntity(
        videoId = analysis.transcript.videoId,
        transcriptId = 0,
        styleNameSnapshot = analysis.style.name,
        styleInstructionsSnapshot = analysis.style.instructions,
        styleOutputLanguageSnapshot = analysis.style.outputLanguage,
        modelSnapshot = analysis.briefing.model,
        markdown = analysis.briefing.markdown,
        mapChunkCount = analysis.briefing.mapChunkCount,
        status = "COMPLETED",
        errorCode = null,
        createdAtEpochMillis = now,
    )

    private fun encodeSegments(analysis: CompletedAnalysis): String = JSONArray().apply {
        analysis.transcript.segments.forEach { segment ->
            put(
                JSONObject()
                    .put("text", segment.text)
                    .put("startSeconds", segment.startSeconds)
                    .put("durationSeconds", segment.durationSeconds),
            )
        }
    }.toString()

    private fun StoredBriefingRow.toModel() = StoredBriefing(
        briefingId = briefingId,
        canonicalUrl = canonicalUrl,
        title = title,
        channelTitle = channelTitle,
        model = model,
        styleName = styleName,
        styleInstructions = styleInstructions,
        styleOutputLanguage = styleOutputLanguage,
        transcriptLanguage = transcriptLanguage,
        transcriptProvider = transcriptProvider,
        markdown = markdown,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
