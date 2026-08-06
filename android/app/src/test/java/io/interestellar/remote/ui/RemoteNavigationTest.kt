package io.interestellar.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteNavigationTest {
    @Test
    fun restoredChatWalksBackThroughTheNavigationHierarchy() {
        assertEquals("conversations", navigationParentRoute("chat", true, true))
        assertEquals("project_dashboard", navigationParentRoute("conversations", true, true))
        assertEquals("projects", navigationParentRoute("project_dashboard", true, true))
        assertEquals("devices", navigationParentRoute("projects", true, false))
        assertNull(navigationParentRoute("devices", false, false))
    }

    @Test
    fun globalScreensReturnToTheBestAvailableContext() {
        assertEquals("project_dashboard", navigationParentRoute("settings", true, true))
        assertEquals("project_dashboard", navigationParentRoute("subscription", true, true))
        assertEquals("projects", navigationParentRoute("inbox", true, false))
        assertEquals("devices", navigationParentRoute("audit", false, false))
    }
}

