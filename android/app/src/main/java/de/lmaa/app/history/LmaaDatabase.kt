package de.lmaa.app.history

import android.content.Context
import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "'Deutsch'")
    val styleOutputLanguageSnapshot: String,
    val modelSnapshot: String,
    val markdown: String,
    val mapChunkCount: Int,
    val status: String,
    val errorCode: String?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "briefing_styles",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index("isActive"),
    ],
)
internal data class BriefingStyleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val instructions: String,
    val outputLanguage: String,
    val isActive: Boolean,
    val isBuiltIn: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
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
    val styleId: Long?,
    @ColumnInfo(defaultValue = "'Standard'")
    val styleNameSnapshot: String,
    @ColumnInfo(defaultValue = "''")
    val styleInstructionsSnapshot: String,
    @ColumnInfo(defaultValue = "'Deutsch'")
    val styleOutputLanguageSnapshot: String,
    val resultConsumedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "provider_usage",
    primaryKeys = ["provider", "month"],
)
internal data class ProviderUsageEntity(
    val provider: String,
    val month: String,
    val attempts: Int,
    val successes: Int,
    val lastStatus: String,
    val updatedAtEpochMillis: Long,
)

internal data class BriefingHistoryRow(
    val briefingId: Long,
    val title: String,
    val channelTitle: String,
    val model: String,
    val styleName: String,
    val createdAtEpochMillis: Long,
)

internal data class StoredBriefingRow(
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

internal data class BriefingDeleteTarget(
    val transcriptId: Long,
    val videoId: String,
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
               b.styleNameSnapshot AS styleName,
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
               b.styleNameSnapshot AS styleName,
               b.styleInstructionsSnapshot AS styleInstructions,
               b.styleOutputLanguageSnapshot AS styleOutputLanguage,
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

    @Query(
        """
        SELECT b.id AS briefingId,
               v.canonicalUrl AS canonicalUrl,
               v.title AS title,
               v.channelTitle AS channelTitle,
               b.modelSnapshot AS model,
               b.styleNameSnapshot AS styleName,
               b.styleInstructionsSnapshot AS styleInstructions,
               b.styleOutputLanguageSnapshot AS styleOutputLanguage,
               t.languageCode AS transcriptLanguage,
               t.provider AS transcriptProvider,
               b.markdown AS markdown,
               b.createdAtEpochMillis AS createdAtEpochMillis
          FROM briefings b
          JOIN videos v ON v.videoId = b.videoId
          JOIN transcripts t ON t.id = b.transcriptId
         WHERE v.canonicalUrl = :canonicalUrl AND b.status = 'COMPLETED'
         ORDER BY b.createdAtEpochMillis DESC, b.id DESC
         LIMIT 1
        """,
    )
    suspend fun findLatestBriefing(canonicalUrl: String): StoredBriefingRow?

    @Query("SELECT transcriptId, videoId FROM briefings WHERE id = :briefingId")
    suspend fun findDeleteTarget(briefingId: Long): BriefingDeleteTarget?

    @Query(
        """
        UPDATE analysis_jobs
           SET briefingId = NULL,
               resultConsumedAtEpochMillis = COALESCE(resultConsumedAtEpochMillis, :deletedAt),
               updatedAtEpochMillis = :deletedAt
         WHERE briefingId = :briefingId
        """,
    )
    suspend fun detachAnalysisJobs(briefingId: Long, deletedAt: Long)

    @Query("DELETE FROM briefings WHERE id = :briefingId")
    suspend fun deleteBriefing(briefingId: Long): Int

    @Query(
        """
        DELETE FROM transcripts
         WHERE id = :transcriptId
           AND NOT EXISTS (SELECT 1 FROM briefings WHERE transcriptId = :transcriptId)
        """,
    )
    suspend fun deleteOrphanTranscript(transcriptId: Long)

    @Query(
        """
        DELETE FROM videos
         WHERE videoId = :videoId
           AND NOT EXISTS (SELECT 1 FROM transcripts WHERE videoId = :videoId)
           AND NOT EXISTS (SELECT 1 FROM briefings WHERE videoId = :videoId)
        """,
    )
    suspend fun deleteOrphanVideo(videoId: String)

    @Transaction
    suspend fun deleteBriefingWithOwnedData(briefingId: Long, deletedAt: Long): Boolean {
        val target = findDeleteTarget(briefingId) ?: return false
        detachAnalysisJobs(briefingId, deletedAt)
        check(deleteBriefing(briefingId) == 1) { "Briefing konnte nicht gelöscht werden" }
        deleteOrphanTranscript(target.transcriptId)
        deleteOrphanVideo(target.videoId)
        return true
    }
}

@Dao
internal interface BriefingStyleDao {
    @Query("SELECT * FROM briefing_styles ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BriefingStyleEntity>>

    @Query("SELECT * FROM briefing_styles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<BriefingStyleEntity?>

    @Query("SELECT * FROM briefing_styles WHERE isActive = 1 LIMIT 1")
    suspend fun findActive(): BriefingStyleEntity?

    @Query("SELECT * FROM briefing_styles WHERE id = :id")
    suspend fun find(id: Long): BriefingStyleEntity?

    @Query("SELECT * FROM briefing_styles WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): BriefingStyleEntity?

    @Insert
    suspend fun insert(style: BriefingStyleEntity): Long

    @Query(
        """
        UPDATE briefing_styles
           SET name = :name, normalizedName = :normalizedName,
               instructions = :instructions, outputLanguage = :outputLanguage,
               updatedAtEpochMillis = :updatedAt
         WHERE id = :id AND isBuiltIn = 0
        """,
    )
    suspend fun updateCustom(
        id: Long,
        name: String,
        normalizedName: String,
        instructions: String,
        outputLanguage: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE briefing_styles SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query(
        "UPDATE briefing_styles SET isActive = 1, updatedAtEpochMillis = :updatedAt WHERE id = :id",
    )
    suspend fun activate(id: Long, updatedAt: Long): Int

    @Transaction
    suspend fun setActive(id: Long, updatedAt: Long) {
        check(find(id) != null) { "Briefing-Stil existiert nicht" }
        deactivateAll()
        check(activate(id, updatedAt) == 1) { "Briefing-Stil konnte nicht aktiviert werden" }
    }

    @Query("DELETE FROM briefing_styles WHERE id = :id AND isBuiltIn = 0 AND isActive = 0")
    suspend fun deleteInactiveCustom(id: Long): Int
}

@Dao
internal interface ProviderUsageDao {
    @Query("SELECT * FROM provider_usage WHERE provider = :provider AND month = :month")
    fun observe(provider: String, month: String): Flow<ProviderUsageEntity?>

    @Insert
    suspend fun insert(usage: ProviderUsageEntity)

    @Query(
        """
        INSERT OR IGNORE INTO provider_usage (
            provider, month, attempts, successes, lastStatus, updatedAtEpochMillis
        ) VALUES (:provider, :month, :attempts, :successes, :lastStatus, :updatedAt)
        """,
    )
    suspend fun insertBaselineIfMissing(
        provider: String,
        month: String,
        attempts: Int,
        successes: Int,
        lastStatus: String,
        updatedAt: Long,
    )

    @Query(
        """
        INSERT INTO provider_usage (
            provider, month, attempts, successes, lastStatus, updatedAtEpochMillis
        ) VALUES (:provider, :month, 1, :successIncrement, :status, :updatedAt)
        ON CONFLICT(provider, month) DO UPDATE SET
            attempts = attempts + 1,
            successes = successes + :successIncrement,
            lastStatus = :status,
            updatedAtEpochMillis = :updatedAt
        """,
    )
    suspend fun recordAttempt(
        provider: String,
        month: String,
        successIncrement: Int,
        status: String,
        updatedAt: Long,
    )
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
        BriefingStyleEntity::class,
        AnalysisJobEntity::class,
        ProviderUsageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class LmaaDatabase : RoomDatabase() {
    abstract fun briefingDao(): BriefingDao
    abstract fun analysisJobDao(): AnalysisJobDao
    abstract fun briefingStyleDao(): BriefingStyleDao
    abstract fun providerUsageDao(): ProviderUsageDao

    companion object {
        private const val DATABASE_NAME = "lmaa-history.db"

        @Volatile
        private var instance: LmaaDatabase? = null

        fun getInstance(context: Context): LmaaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LmaaDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `briefings` ADD COLUMN " +
                        "`styleOutputLanguageSnapshot` TEXT NOT NULL DEFAULT 'Deutsch'",
                )
                db.execSQL("ALTER TABLE `analysis_jobs` ADD COLUMN `styleId` INTEGER")
                db.execSQL(
                    "ALTER TABLE `analysis_jobs` ADD COLUMN " +
                        "`styleNameSnapshot` TEXT NOT NULL DEFAULT 'Standard'",
                )
                db.execSQL(
                    "ALTER TABLE `analysis_jobs` ADD COLUMN " +
                        "`styleInstructionsSnapshot` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE `analysis_jobs` ADD COLUMN " +
                        "`styleOutputLanguageSnapshot` TEXT NOT NULL DEFAULT 'Deutsch'",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `briefing_styles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `outputLanguage` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_briefing_styles_normalizedName` " +
                        "ON `briefing_styles` (`normalizedName`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_briefing_styles_isActive` " +
                        "ON `briefing_styles` (`isActive`)",
                )
                db.execSQL(
                    """
                    INSERT INTO `briefing_styles` (
                        `name`, `normalizedName`, `instructions`, `outputLanguage`,
                        `isActive`, `isBuiltIn`, `createdAtEpochMillis`, `updatedAtEpochMillis`
                    ) VALUES (?, ?, ?, ?, 1, 1, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        "Standard",
                        "standard",
                        de.lmaa.app.DEFAULT_STYLE_INSTRUCTIONS,
                        "Deutsch",
                        0L,
                        0L,
                    ),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `provider_usage` (
                        `provider` TEXT NOT NULL,
                        `month` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `successes` INTEGER NOT NULL,
                        `lastStatus` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`provider`, `month`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
