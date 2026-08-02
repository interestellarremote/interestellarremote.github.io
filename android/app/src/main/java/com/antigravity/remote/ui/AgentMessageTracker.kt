package com.antigravity.remote.ui

import java.util.UUID

internal data class PendingAgentMessage(
    val id: String,
    val conversationId: String,
    val content: String,
    val createdAt: Long,
)

internal class AgentMessageTracker(
    private val idFactory: () -> String = { "agent-${UUID.randomUUID()}" },
) {
    private val pending = mutableMapOf<String, PendingAgentMessage>()

    fun append(conversationId: String, text: String, createdAt: Long): PendingAgentMessage {
        val current = pending[conversationId]
        val updated = if (current == null) {
            PendingAgentMessage(idFactory(), conversationId, text, createdAt)
        } else {
            current.copy(content = current.content + text)
        }
        pending[conversationId] = updated
        return updated
    }

    fun finish(conversationId: String): PendingAgentMessage? = pending.remove(conversationId)
}
