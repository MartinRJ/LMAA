package de.lmaa.app.history

import de.lmaa.app.CompletedAnalysis
import de.lmaa.app.DEFAULT_STYLE_INSTRUCTIONS
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

internal data class BriefingHistoryItem(
    val briefingId: Long,
    val title: String,
    val channelTitle: String,
    val model: String,
    val createdAtEpochMillis: Long,
)

internal data class StoredBriefing(
    val briefingId: Long,
    val canonicalUrl: String,
    val title: String,
    val channelTitle: String,
    val model: String,
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
                createdAtEpochMillis = row.createdAtEpochMillis,
            )
        }
    }

    suspend fun save(analysis: CompletedAnalysis): StoredBriefing {
        val now = System.currentTimeMillis()
        val briefingId = dao.persistCompletedAnalysis(
            video = VideoEntity(
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
            ),
            transcript = TranscriptEntity(
                videoId = analysis.transcript.videoId,
                provider = analysis.transcript.provider,
                languageCode = analysis.transcript.languageCode,
                isGenerated = analysis.transcript.isGenerated,
                segmentsJson = encodeSegments(analysis),
                plainText = analysis.transcript.segments.joinToString("\n") { it.text },
                fetchedAtEpochMillis = now,
            ),
            briefing = BriefingEntity(
                videoId = analysis.transcript.videoId,
                transcriptId = 0,
                styleNameSnapshot = "Standard",
                styleInstructionsSnapshot = DEFAULT_STYLE_INSTRUCTIONS,
                modelSnapshot = analysis.briefing.model,
                markdown = analysis.briefing.markdown,
                mapChunkCount = analysis.briefing.mapChunkCount,
                status = "COMPLETED",
                errorCode = null,
                createdAtEpochMillis = now,
            ),
        )
        return requireNotNull(dao.findBriefing(briefingId)) {
            "Gespeichertes Briefing ist nicht lesbar"
        }.toModel()
    }

    suspend fun find(briefingId: Long): StoredBriefing? =
        dao.findBriefing(briefingId)?.toModel()

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
        transcriptLanguage = transcriptLanguage,
        transcriptProvider = transcriptProvider,
        markdown = markdown,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
