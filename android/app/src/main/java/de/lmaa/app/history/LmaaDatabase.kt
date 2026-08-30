package de.lmaa.app.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "videos")
internal data class VideoEntity(
    @PrimaryKey val videoId: String,
    val canonicalUrl: String,
    val title: String,
    val channelId: String?,
    val channelTitle: String,
    val publishedAtEpochMillis: Long?,
    val durationIso8601: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String,
    val fetchedAtEpochMillis: Long,
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("videoId")],
)
internal data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val provider: String,
    val languageCode: String,
    val isGenerated: Boolean,
    val segmentsJson: String,
    val plainText: String,
    val fetchedAtEpochMillis: Long,
)

@Entity(
    tableName = "briefings",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("videoId"), Index("transcriptId"), Index("createdAtEpochMillis")],
)
internal data class BriefingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val transcriptId: Long,
    val styleNameSnapshot: String,
    val styleInstructionsSnapshot: String,
    val modelSnapshot: String,
    val markdown: String,
    val mapChunkCount: Int,
    val status: String,
    val errorCode: String?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "analysis_jobs",
    indices = [Index("status"), Index("createdAtEpochMillis")],
)
internal data class AnalysisJobEntity(
    @PrimaryKey val jobId: String,
    val canonicalUrl: String,
    val status: String,
    val stage: String?,
    val briefingId: Long?,
    val errorCode: String?,
    val resultConsumedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

internal data class BriefingHistoryRow(
    val briefingId: Long,
    val title: String,
    val channelTitle: String,
    val model: String,
    val createdAtEpochMillis: Long,
)

internal data class StoredBriefingRow(
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

@Dao
internal interface BriefingDao {
    @Upsert
    suspend fun upsertVideo(video: VideoEntity)

    @Insert
    suspend fun insertTranscript(transcript: TranscriptEntity): Long

    @Insert
    suspend fun insertBriefing(briefing: BriefingEntity): Long

    @Transaction
    suspend fun persistCompletedAnalysis(
        video: VideoEntity,
        transcript: TranscriptEntity,
        briefing: BriefingEntity,
    ): Long {
        upsertVideo(video)
        val transcriptId = insertTranscript(transcript)
        return insertBriefing(briefing.copy(transcriptId = transcriptId))
    }

    @Query(
        """
        UPDATE analysis_jobs
           SET status = 'SUCCEEDED', stage = 'PERSISTING', briefingId = :briefingId,
               errorCode = NULL, updatedAtEpochMillis = :updatedAt
         WHERE jobId = :jobId AND status IN ('ENQUEUED', 'RUNNING')
        """,
    )
    suspend fun markAnalysisJobSucceeded(
        jobId: String,
        briefingId: Long,
        updatedAt: Long,
    ): Int

    @Transaction
    suspend fun persistCompletedAnalysisForJob(
        jobId: String,
        video: VideoEntity,
        transcript: TranscriptEntity,
        briefing: BriefingEntity,
        completedAt: Long,
    ): Long {
        upsertVideo(video)
        val transcriptId = insertTranscript(transcript)
        val briefingId = insertBriefing(briefing.copy(transcriptId = transcriptId))
        check(markAnalysisJobSucceeded(jobId, briefingId, completedAt) == 1) {
            "Analyseauftrag ist nicht mehr aktiv"
        }
        return briefingId
    }

    @Query(
        """
        SELECT b.id AS briefingId,
               v.title AS title,
               v.channelTitle AS channelTitle,
               b.modelSnapshot AS model,
               b.createdAtEpochMillis AS createdAtEpochMillis
          FROM briefings b
          JOIN videos v ON v.videoId = b.videoId
         WHERE b.status = 'COMPLETED'
         ORDER BY b.createdAtEpochMillis DESC, b.id DESC
        """,
    )
    fun observeHistory(): Flow<List<BriefingHistoryRow>>

    @Query(
        """
        SELECT b.id AS briefingId,
               v.canonicalUrl AS canonicalUrl,
               v.title AS title,
               v.channelTitle AS channelTitle,
               b.modelSnapshot AS model,
               t.languageCode AS transcriptLanguage,
               t.provider AS transcriptProvider,
               b.markdown AS markdown,
               b.createdAtEpochMillis AS createdAtEpochMillis
          FROM briefings b
          JOIN videos v ON v.videoId = b.videoId
          JOIN transcripts t ON t.id = b.transcriptId
         WHERE b.id = :briefingId AND b.status = 'COMPLETED'
        """,
    )
    suspend fun findBriefing(briefingId: Long): StoredBriefingRow?
}

@Dao
internal interface AnalysisJobDao {
    @Insert
    suspend fun insert(job: AnalysisJobEntity)

    @Query(
        """
        SELECT *
          FROM analysis_jobs
         WHERE status IN ('ENQUEUED', 'RUNNING')
            OR resultConsumedAtEpochMillis IS NULL
         ORDER BY createdAtEpochMillis DESC
         LIMIT 1
        """,
    )
    fun observeCurrent(): Flow<AnalysisJobEntity?>

    @Query("SELECT * FROM analysis_jobs WHERE jobId = :jobId")
    suspend fun find(jobId: String): AnalysisJobEntity?

    @Query(
        """
        SELECT *
          FROM analysis_jobs
         WHERE status IN ('ENQUEUED', 'RUNNING')
         ORDER BY createdAtEpochMillis ASC
        """,
    )
    suspend fun findRecoverable(): List<AnalysisJobEntity>

    @Query(
        """
        UPDATE analysis_jobs
           SET status = 'RUNNING', stage = :stage, updatedAtEpochMillis = :updatedAt
         WHERE jobId = :jobId AND status IN ('ENQUEUED', 'RUNNING')
        """,
    )
    suspend fun markRunning(jobId: String, stage: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE analysis_jobs
           SET status = 'ENQUEUED', updatedAtEpochMillis = :updatedAt
         WHERE jobId = :jobId AND status = 'RUNNING'
        """,
    )
    suspend fun markEnqueuedIfRunning(jobId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE analysis_jobs
           SET status = 'FAILED', errorCode = :errorCode, updatedAtEpochMillis = :updatedAt
         WHERE jobId = :jobId AND status IN ('ENQUEUED', 'RUNNING')
        """,
    )
    suspend fun markFailed(jobId: String, errorCode: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE analysis_jobs
           SET status = 'CANCELLED', resultConsumedAtEpochMillis = :updatedAt,
               updatedAtEpochMillis = :updatedAt
         WHERE jobId = :jobId AND status IN ('ENQUEUED', 'RUNNING')
        """,
    )
    suspend fun cancel(jobId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE analysis_jobs
           SET resultConsumedAtEpochMillis = :consumedAt, updatedAtEpochMillis = :consumedAt
         WHERE jobId = :jobId AND status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
        """,
    )
    suspend fun consumeResult(jobId: String, consumedAt: Long): Int
}

@Database(
    entities = [
        VideoEntity::class,
        TranscriptEntity::class,
        BriefingEntity::class,
        AnalysisJobEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class LmaaDatabase : RoomDatabase() {
    abstract fun briefingDao(): BriefingDao
    abstract fun analysisJobDao(): AnalysisJobDao

    companion object {
        private const val DATABASE_NAME = "lmaa-history.db"

        @Volatile
        private var instance: LmaaDatabase? = null

        fun getInstance(context: Context): LmaaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LmaaDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_jobs` (
                        `jobId` TEXT NOT NULL,
                        `canonicalUrl` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `stage` TEXT,
                        `briefingId` INTEGER,
                        `errorCode` TEXT,
                        `resultConsumedAtEpochMillis` INTEGER,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`jobId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_jobs_status` " +
                        "ON `analysis_jobs` (`status`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_jobs_createdAtEpochMillis` " +
                        "ON `analysis_jobs` (`createdAtEpochMillis`)",
                )
            }
        }
    }
}
