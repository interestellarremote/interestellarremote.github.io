package com.antigravity.remote

import com.antigravity.remote.ui.presentAgentMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMessageSanitizerTest {
    @Test
    fun hidesRuntimeProtocolButKeepsHumanAnswer() {
        val result = presentAgentMessage(
            """assistant/analysis
                |<thinking>
                |verificando arquivos
                |</thinking>
                |A alteração foi concluída com sucesso.
                |{"event":"usage_metadata","delta":12}
            """.trimMargin()
        )

        assertEquals("A alteração foi concluída com sucesso.", result.visible)
        assertTrue(result.technical.contains("verificando arquivos"))
        assertTrue(result.technical.contains("usage_metadata"))
    }

    @Test
    fun keepsJsonAndTagsInsideMarkdownCodeBlocks() {
        val result = presentAgentMessage("""Exemplo:
            |```json
            |{"event":"created"}
            |```
        """.trimMargin())

        assertTrue(result.visible.contains("{\"event\":\"created\"}"))
        assertTrue(result.technical.isEmpty())
    }
}
