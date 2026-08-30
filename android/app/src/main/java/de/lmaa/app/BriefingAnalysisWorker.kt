package de.lmaa.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.lmaa.app.history.AnalysisJobRepository
import de.lmaa.app.history.AnalysisJobStatus
import de.lmaa.app.history.BriefingHistoryRepository
import de.lmaa.app.history.LmaaDatabase
import de.lmaa.app.secrets.ProviderSecretStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class BriefingAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(INPUT_JOB_ID)?.let(::parseUuid)
            ?: return Result.failure()
        val database = LmaaDatabase.getInstance(applicationContext)
        val jobs = AnalysisJobRepository(database.analysisJobDao())
        val job = jobs.find(jobId) ?: return Result.failure()
        when (job.status) {
            AnalysisJobStatus.SUCCEEDED -> return job.briefingId
                ?.let { Result.success(outputData(it)) }
                ?: Result.failure()
            AnalysisJobStatus.FAILED,
            AnalysisJobStatus.CANCELLED,
            -> return Result.success()
            AnalysisJobStatus.ENQUEUED,
            AnalysisJobStatus.RUNNING,
            -> Unit
        }

        try {
            setForeground(foregroundInfo(jobId, AnalysisStage.TRANSCRIPT))
            val store = try {
                ProviderSecretStore.getInstance(applicationContext)
            } catch (_: Exception) {
                jobs.markFailed(jobId, "SECRET_STORE_ERROR")
                return Result.success()
            }
            if (!store.status.first().hasOpenAiKey) {
                jobs.markFailed(jobId, "OPENAI_KEY_MISSING")
                return Result.success()
            }

            val pipeline = BriefingPipeline(
                transcriptProvider = LocalTranscriptProvider(applicationContext),
                metadataProvider = YoutubeOEmbedMetadataProvider(),
                briefingCreator = BriefingService(OpenAiBriefingTextGenerator(store)),
            )
            return when (
                val result = pipeline.analyze(job.canonicalUrl) { stage ->
                    setForeground(foregroundInfo(jobId, stage))
                    check(jobs.markRunning(jobId, stage)) { "Analyseauftrag wurde abgebrochen" }
                }
            ) {
                is AnalysisResult.Success -> {
                    setForeground(foregroundInfo(jobId, AnalysisStage.PERSISTING))
                    check(jobs.markRunning(jobId, AnalysisStage.PERSISTING)) {
                        "Analyseauftrag wurde abgebrochen"
                    }
                    val briefing = BriefingHistoryRepository(database.briefingDao())
                        .saveForJob(jobId, result.analysis)
                    Result.success(outputData(briefing.briefingId))
                }
                is AnalysisResult.Failure -> {
                    jobs.markFailed(jobId, result.code)
                    Result.success()
                }
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { jobs.markEnqueuedIfRunning(jobId) }
            throw exception
        } catch (_: Exception) {
            jobs.markFailed(jobId, "BRIEFING_PIPELINE_ERROR")
            return Result.success()
        }
    }

    private fun parseUuid(value: String): UUID? = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun outputData(briefingId: Long): Data = Data.Builder()
        .putLong(OUTPUT_BRIEFING_ID, briefingId)
        .build()

    private fun foregroundInfo(jobId: UUID, stage: AnalysisStage): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.analysis_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApp = PendingIntent.getActivity(
            applicationContext,
            jobId.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lmaa_notification)
            .setContentTitle(applicationContext.getString(R.string.analysis_notification_title))
            .setContentText(applicationContext.getString(stage.messageResource))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(
            jobId.hashCode() and Int.MAX_VALUE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        internal const val INPUT_JOB_ID = "analysis_job_id"
        internal const val OUTPUT_BRIEFING_ID = "briefing_id"
        private const val NOTIFICATION_CHANNEL_ID = "lmaa-analysis"
    }
}

private val AnalysisStage.messageResource: Int
    get() = when (this) {
        AnalysisStage.TRANSCRIPT -> R.string.analysis_transcript
        AnalysisStage.METADATA -> R.string.analysis_metadata
        AnalysisStage.BRIEFING -> R.string.analysis_briefing
        AnalysisStage.PERSISTING -> R.string.analysis_persisting
    }

internal class AnalysisWorkScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    suspend fun enqueue(jobId: UUID) {
        withContext(Dispatchers.IO) {
            val name = uniqueName(jobId)
            val existing = workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            if (existing.any { !it.state.isFinished }) return@withContext
            workManager.enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(BriefingAnalysisWorker::class.java)
                    .setInputData(Data.Builder().putString(BriefingAnalysisWorker.INPUT_JOB_ID, jobId.toString()).build())
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .addTag(WORK_TAG)
                    .build(),
            )
        }
    }

    suspend fun reconcile(repository: AnalysisJobRepository) {
        repository.findRecoverable().forEach { enqueue(it.jobId) }
    }

    suspend fun cancel(repository: AnalysisJobRepository, jobId: UUID) {
        repository.cancel(jobId)
        workManager.cancelUniqueWork(uniqueName(jobId))
    }

    private fun uniqueName(jobId: UUID) = "lmaa-analysis-$jobId"

    companion object {
        private const val WORK_TAG = "lmaa-analysis"
    }
}
