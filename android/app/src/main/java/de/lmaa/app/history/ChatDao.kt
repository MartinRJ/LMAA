package de.lmaa.app.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages", foreignKeys = [ForeignKey(
    entity = BriefingEntity::class, parentColumns = ["id"], childColumns = ["briefingId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("briefingId")])
internal data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val briefingId: Long,
    val role: String,
    val content: String,
    val ts: Long,
    val pending: Boolean = false,
    val errorCode: String? = null,
)

@Dao
internal interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE briefingId = :id ORDER BY ts, id")
    fun observe(id: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE briefingId = :id ORDER BY ts, id")
    suspend fun messages(id: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun find(id: String): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET pending = :pending, errorCode = :error WHERE id = :id")
    suspend fun state(id: String, pending: Boolean, error: String?)

    @Transaction
    suspend fun finish(request: ChatMessageEntity, answer: String) {
        if (find(request.id) == null || find(request.id + "-reply") != null) return
        insert(ChatMessageEntity(request.id + "-reply", request.briefingId, "assistant", answer,
            maxOf(System.currentTimeMillis(), request.ts + 1)))
        state(request.id, false, null)
    }
}
