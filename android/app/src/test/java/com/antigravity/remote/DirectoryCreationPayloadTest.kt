package com.antigravity.remote

import com.antigravity.remote.data.MessageType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryCreationPayloadTest {
    @Test
    fun createDirectoryPayloadFormatsMetadataCorrectly() {
        val parent = "C:/Users/test/Projects"
        val folderName = "NovoProjeto"
        val navigate = true
        val useAsProject = true
        val projectName = "Novo Projeto"

        val metadata = JSONObject()
            .put("parent", parent)
            .put("name", folderName)
            .put("navigate", navigate)
            .put("use_as_project", useAsProject)
            .put("projectName", projectName)

        val payload = JSONObject().put("metadata", metadata)

        val meta = payload.getJSONObject("metadata")
        assertEquals(parent, meta.getString("parent"))
        assertEquals(folderName, meta.getString("name"))
        assertTrue(meta.getBoolean("navigate"))
        assertTrue(meta.getBoolean("use_as_project"))
        assertEquals(projectName, meta.getString("projectName"))
    }
}
