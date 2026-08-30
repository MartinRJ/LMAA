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

@Database(
    entities = [VideoEntity::class, TranscriptEntity::class, BriefingEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class LmaaDatabase : RoomDatabase() {
    abstract fun briefingDao(): BriefingDao

    companion object {
        private const val DATABASE_NAME = "lmaa-history.db"

        @Volatile
        private var instance: LmaaDatabase? = null

        fun getInstance(context: Context): LmaaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LmaaDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
