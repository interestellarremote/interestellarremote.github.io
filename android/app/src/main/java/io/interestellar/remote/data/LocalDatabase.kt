package io.interestellar.remote.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations", primaryKeys = ["id"])
data class ConversationEntity(val id: String, val deviceId: String, val projectId: String, val title: String, val updatedAt: Long, val archived: Boolean = false)

@Entity(tableName = "messages", primaryKeys = ["id"])
data class MessageEntity(val id: String, val conversationId: String, val role: String, val content: String, val createdAt: Long, val status: String)

@Entity(tableName = "drafts", primaryKeys = ["conversationId"])
data class DraftEntity(val conversationId: String, val text: String)

@Entity(tableName = "tasks", primaryKeys = ["id"])
data class TaskEntity(
    val id: String,
    val conversationId: String,
    val deviceId: String,
    val projectId: String,
    val prompt: String,
    val status: String,
    val phase: String,
    val createdAt: Long,
    val updatedAt: Long,
    val elapsedSeconds: Int = 0,
    val error: String? = null,
    val retryOf: String? = null,
    val unread: Boolean = false,
)

@Entity(tableName = "audit_log", primaryKeys = ["id"])
data class AuditEntity(
    val id: String,
    val taskId: String?,
    val conversationId: String?,
    val kind: String,
    val description: String,
    val createdAt: Long,
)

@Entity(tableName = "processed_events", primaryKeys = ["id"])
data class ProcessedEventEntity(val id: String, val processedAt: Long)

@Dao
interface RemoteDao {
    @Query("SELECT * FROM conversations WHERE deviceId=:deviceId AND projectId=:projectId AND archived=0 ORDER BY updatedAt DESC")
    fun conversations(deviceId: String, projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id=:conversationId LIMIT 1")
    suspend fun conversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE deviceId=:deviceId ORDER BY updatedAt DESC")
    suspend fun conversationsForDevice(deviceId: String): List<ConversationEntity>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId ORDER BY createdAt")
    suspend fun messagesOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId=:conversationId ORDER BY createdAt")
    fun messages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id=:id LIMIT 1")
    suspend fun message(id: String): MessageEntity?

    @Query("SELECT * FROM drafts WHERE conversationId=:conversationId LIMIT 1")
    suspend fun draft(conversationId: String): DraftEntity?

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun tasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE conversationId=:conversationId ORDER BY createdAt DESC")
    fun tasksForConversation(conversationId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id=:id LIMIT 1")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deviceId=:deviceId AND status IN ('QUEUED','STARTING','RUNNING','CANCELLING')")
    suspend fun activeTasks(deviceId: String): List<TaskEntity>

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC LIMIT :limit")
    fun audit(limit: Int = 200): Flow<List<AuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConversation(value: ConversationEntity)

    @Query("UPDATE conversations SET title=:title, updatedAt=:updatedAt WHERE id=:id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET archived=1, updatedAt=:updatedAt WHERE id=:id")
    suspend fun archiveConversation(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id=:id")
    suspend fun deleteConversation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessage(value: MessageEntity)

    @Query("UPDATE messages SET status=:status WHERE id=:id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("SELECT EXISTS(SELECT 1 FROM processed_events WHERE id=:id)")
    suspend fun isEventProcessed(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun saveProcessedEvent(value: ProcessedEventEntity)

    @Query("DELETE FROM processed_events WHERE processedAt < :before")
    suspend fun pruneProcessedEvents(before: Long)

    @Transaction
    suspend fun appendAgentDeltaOnce(
        eventId: String,
        messageId: String,
        conversationId: String,
        text: String,
        createdAt: Long,
        processedAt: Long,
    ): Boolean {
        if (isEventProcessed(eventId)) return false
        val existing = message(messageId)
        saveMessage(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                role = "agent",
                content = existing?.content.orEmpty() + text,
                createdAt = existing?.createdAt ?: createdAt,
                status = "streaming",
            )
        )
        saveProcessedEvent(ProcessedEventEntity(eventId, processedAt))
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(value: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(value: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAudit(value: AuditEntity)

    @Query("UPDATE tasks SET unread=0 WHERE id=:id")
    suspend fun markTaskRead(id: String)

    @Query("UPDATE tasks SET status='ERROR', phase='reconcile_missing', error=:error, updatedAt=:updatedAt, unread=1 WHERE id=:id")
    suspend fun markTaskMissing(id: String, error: String, updatedAt: Long)
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, DraftEntity::class, TaskEntity::class, AuditEntity::class, ProcessedEventEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() { abstract fun dao(): RemoteDao }

