package de.lmaa.app.history

import de.lmaa.app.AnalysisStage
import de.lmaa.app.BriefingStyleSnapshot
import de.lmaa.app.DEFAULT_BRIEFING_STYLE
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal enum class AnalysisJobStatus {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

internal data class AnalysisJob(
    val jobId: UUID,
    val canonicalUrl: String,
    val status: AnalysisJobStatus,
    val stage: AnalysisStage?,
    val briefingId: Long?,
    val errorCode: String?,
    val styleId: Long?,
    val style: BriefingStyleSnapshot,
)

internal class AnalysisJobRepository(
    private val dao: AnalysisJobDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val current: Flow<AnalysisJob?> = dao.observeCurrent().map { it?.toModel() }

    suspend fun create(
        canonicalUrl: String,
        style: BriefingStyleSnapshot = DEFAULT_BRIEFING_STYLE,
        styleId: Long? = null,
    ): AnalysisJob {
        val now = clock()
        val job = AnalysisJobEntity(
            jobId = UUID.randomUUID().toString(),
            canonicalUrl = canonicalUrl,
            status = AnalysisJobStatus.ENQUEUED.name,
            stage = AnalysisStage.TRANSCRIPT.name,
            briefingId = null,
            errorCode = null,
            styleId = styleId,
            styleNameSnapshot = style.name,
            styleInstructionsSnapshot = style.instructions,
            styleOutputLanguageSnapshot = style.outputLanguage,
            resultConsumedAtEpochMillis = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        dao.insert(job)
        return job.toModel()
    }

    suspend fun find(jobId: UUID): AnalysisJob? = dao.find(jobId.toString())?.toModel()

    suspend fun findRecoverable(): List<AnalysisJob> =
        dao.findRecoverable().map { it.toModel() }

    suspend fun markRunning(jobId: UUID, stage: AnalysisStage): Boolean =
        dao.markRunning(jobId.toString(), stage.name, clock()) == 1

    suspend fun markEnqueuedIfRunning(jobId: UUID) {
        dao.markEnqueuedIfRunning(jobId.toString(), clock())
    }

    suspend fun markFailed(jobId: UUID, errorCode: String): Boolean =
        dao.markFailed(jobId.toString(), errorCode, clock()) == 1

    suspend fun cancel(jobId: UUID): Boolean =
        dao.cancel(jobId.toString(), clock()) == 1

    suspend fun consumeResult(jobId: UUID) {
        dao.consumeResult(jobId.toString(), clock())
    }

    private fun AnalysisJobEntity.toModel() = AnalysisJob(
        jobId = UUID.fromString(jobId),
        canonicalUrl = canonicalUrl,
        status = AnalysisJobStatus.valueOf(status),
        stage = stage?.let(AnalysisStage::valueOf),
        briefingId = briefingId,
        errorCode = errorCode,
        styleId = styleId,
        style = BriefingStyleSnapshot(
            name = styleNameSnapshot.ifBlank { DEFAULT_BRIEFING_STYLE.name },
            instructions = styleInstructionsSnapshot.ifBlank {
                DEFAULT_BRIEFING_STYLE.instructions
            },
            outputLanguage = styleOutputLanguageSnapshot.ifBlank {
                DEFAULT_BRIEFING_STYLE.outputLanguage
            },
        ),
    )
}
