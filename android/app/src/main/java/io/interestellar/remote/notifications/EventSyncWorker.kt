package io.interestellar.remote.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.interestellar.remote.data.DecryptedEvent
import io.interestellar.remote.data.MessageEntity
import io.interestellar.remote.data.MessageType
import io.interestellar.remote.data.ProcessedEventEntity
import io.interestellar.remote.data.RemoteDao
import io.interestellar.remote.data.RemoteRepository
import io.interestellar.remote.data.TaskEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: RemoteRepository,
    private val dao: RemoteDao,
    private val auth: FirebaseAuth,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val deviceId = inputData.getString(KEY_DEVICE_ID) ?: return Result.failure()
        if (auth.currentUser == null) return Result.success()

        return runCatching {
            val acknowledgements = mutableListOf<String>()
            val conversationsToBackup = linkedSetOf<String>()
            repository.pendingEvents(deviceId).forEach { event ->
                if (persistChatEvent(event)) {
                    acknowledgements += event.envelope.messageId
                    conversationsToBackup += event.envelope.conversationId
                }
            }
            conversationsToBackup.forEach { conversationId ->
                dao.conversation(conversationId)?.let { conversation ->
                    repository.backupConversation(conversation, dao.messagesOnce(conversationId))
                }
            }
            repository.acknowledgeEvents(deviceId, acknowledgements)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun persistChatEvent(event: DecryptedEvent): Boolean {
        val eventId = event.envelope.messageId
        val taskId = event.payload.optString("taskId")
            .ifBlank { "legacy-${event.envelope.conversationId}" }
        val now = System.currentTimeMillis()

        return when (event.envelope.type) {
            MessageType.TEXT_DELTA -> {
                dao.appendAgentDeltaOnce(
                    eventId = eventId,
                    messageId = "agent-$taskId",
                    conversationId = event.envelope.conversationId,
                    text = event.payload.optString("text"),
                    createdAt = event.envelope.createdAt,
                    processedAt = now,
                )
                true
            }
            MessageType.THOUGHT_DELTA, MessageType.TOOL_CALL -> {
                if (!dao.isEventProcessed(eventId)) {
                    dao.saveMessage(
                        MessageEntity(
                            id = eventId,
                            conversationId = event.envelope.conversationId,
                            role = "system",
                            content = technicalEventText(event),
                            createdAt = event.envelope.createdAt,
                            status = event.envelope.type.name,
                        )
                    )
                    dao.saveProcessedEvent(ProcessedEventEntity(eventId, now))
                }
                true
            }
            MessageType.TURN_COMPLETE, MessageType.ERROR -> {
                if (!dao.isEventProcessed(eventId)) {
                    val errorCode = event.payload.optString("code")
                    val rawMessage = event.payload.optString("message", "Erro remoto")
                    val humanError = if (event.envelope.type == MessageType.ERROR) {
                        humanizeError(rawMessage)
                    } else null
                    val messageId = "agent-$taskId"
                    val existingMessage = dao.message(messageId)
                    if (
                        event.envelope.type == MessageType.ERROR &&
                        isSilentSystemError(errorCode, rawMessage)
                    ) {
                        dao.updateMessageStatus(messageId, "complete")
                    } else if (existingMessage == null && !humanError.isNullOrBlank()) {
                        dao.saveMessage(
                            MessageEntity(
                                id = messageId,
                                conversationId = event.envelope.conversationId,
                                role = "agent",
                                content = humanError,
                                createdAt = event.envelope.createdAt,
                                status = "complete",
                            )
                        )
                    } else {
                        dao.updateMessageStatus(messageId, "complete")
                    }
                    val existing = dao.task(taskId)
                    val cancelled = event.envelope.type == MessageType.ERROR &&
                        isCancelledErrorCode(errorCode)
                    val status = when {
                        event.envelope.type == MessageType.TURN_COMPLETE -> "COMPLETE"
                        cancelled -> "CANCELLED"
                        else -> "ERROR"
                    }
                    val error = if (status == "ERROR") humanError else null
                    dao.saveTask(
                        (existing ?: TaskEntity(
                            id = taskId,
                            conversationId = event.envelope.conversationId,
                            deviceId = event.envelope.deviceId,
                            projectId = "",
                            prompt = "",
                            status = status,
                            phase = status.lowercase(),
                            createdAt = event.envelope.createdAt,
                            updatedAt = now,
                        )).copy(
                            status = status,
                            phase = if (event.envelope.type == MessageType.ERROR) errorPhase(errorCode, rawMessage) else status.lowercase(),
                            updatedAt = now,
                            elapsedSeconds = maxOf(existing?.elapsedSeconds ?: 0, event.payload.optInt("elapsedSeconds", 0)),
                            error = error,
                            unread = true,
                        )
                    )
                    dao.saveProcessedEvent(ProcessedEventEntity(eventId, now))
                }
                true
            }
            else -> false
        }
    }

    private fun technicalEventText(event: DecryptedEvent): String = when (event.envelope.type) {
        MessageType.THOUGHT_DELTA -> event.payload.optString("text")
            .ifBlank { event.payload.optString("thought") }
            .ifBlank { event.payload.toString(2) }
        MessageType.TOOL_CALL -> {
            val name = event.payload.optString("name")
                .ifBlank { event.payload.optString("tool") }
                .ifBlank { "Ferramenta" }
            val detail = event.payload.optString("description")
                .ifBlank { event.payload.optString("text") }
            if (detail.isBlank()) name else "$name\n$detail"
        }
        else -> event.payload.toString(2)
    }

    private fun humanizeError(message: String): String = when {
        "model" in message.lowercase() -> "O Antigravity nao conseguiu carregar o modelo. Verifique a conexao e tente novamente."
        "not logged" in message.lowercase() || "auth" in message.lowercase() -> "A sessao do Antigravity no computador precisa ser renovada."
        "timeout" in message.lowercase() -> "A tarefa excedeu o tempo permitido. Voce pode tentar novamente."
        "offline" in message.lowercase() -> "O computador esta offline. A instrucao nao foi executada."
        "quota" in message.lowercase() || "rate limit" in message.lowercase() || "usage limit" in message.lowercase() -> "A cota da IA foi atingida no computador. Aguarde a renovacao do limite e tente novamente."
        else -> message
    }

    private fun isCancelledErrorCode(code: String): Boolean =
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true)

    private fun isSilentSystemError(code: String, message: String): Boolean =
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true) ||
            code.equals("TURN_TIMEOUT", ignoreCase = true) ||
            code.equals("SIGNAL_TIMEOUT", ignoreCase = true) ||
            code.equals("EXECUTION_INTERRUPTED", ignoreCase = true) ||
            isTimeoutLikeMessage(message) ||
            "perdeu contato com o processo" in message.lowercase()

    private fun errorPhase(code: String, message: String): String = when {
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true) -> "cancelled"
        code.equals("TURN_TIMEOUT", ignoreCase = true) ||
            isTimeoutLikeMessage(message) -> "timeout"
        code.equals("SIGNAL_TIMEOUT", ignoreCase = true) -> "signal_timeout"
        code.equals("EXECUTION_INTERRUPTED", ignoreCase = true) -> "interrupted"
        else -> "error"
    }

    private fun isTimeoutLikeMessage(message: String): Boolean {
        val normalized = message.lowercase()
        return "timeout" in normalized || "timed out" in normalized || "tempo permitido" in normalized
    }

    companion object {
        const val KEY_DEVICE_ID = "deviceId"
    }
}

