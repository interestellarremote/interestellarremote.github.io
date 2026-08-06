package io.interestellar.remote

import io.interestellar.remote.ui.AgentMessageTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AgentMessageTrackerTest {
    @Test
    fun responsesFromTwoTurnsUseDifferentIdsAndKeepTheirContent() {
        var sequence = 0
        val tracker = AgentMessageTracker { "agent-${++sequence}" }

        tracker.append("conversation", "primeira ", 10)
        val first = tracker.append("conversation", "resposta", 11)
        assertEquals(first, tracker.finish("conversation"))

        val second = tracker.append("conversation", "segunda resposta", 20)
        assertEquals(second, tracker.finish("conversation"))

        assertEquals("primeira resposta", first.content)
        assertEquals("segunda resposta", second.content)
        assertNotEquals(first.id, second.id)
    }
}

