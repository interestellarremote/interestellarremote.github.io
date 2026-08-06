package io.interestellar.remote

import io.interestellar.remote.data.Envelope
import io.interestellar.remote.data.MessageType
import io.interestellar.remote.security.CryptoEngine
import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test

class CryptoInteropTest {
    @Test fun decryptsPythonVector() {
        val envelope = Envelope(
            deviceId = "d".repeat(32), conversationId = "conversation-1", sequence = 7,
            type = MessageType.SEND_PROMPT, createdAt = 0, expiresAt = Long.MAX_VALUE,
            nonce = "AAECAwQFBgcICQoL",
            ciphertext = "yX67_VD7rVPF3zmUZjsawd1CQ2jmkrn_WSpHw6hl7FM"
        )
        val root = ByteArray(32) { it.toByte() }
        assertEquals("ola", CryptoEngine.decrypt(root, envelope).getString("prompt"))
    }

    @Test fun encryptedHistoryRoundTrips() {
        val root = ByteArray(32) { (it + 1).toByte() }
        val payload = JSONObject().put("title", "Conversa de teste").put("count", 2)
        val encrypted = CryptoEngine.encryptBackup(root, "device-1", "conversation-1", 1, payload)
        val restored = CryptoEngine.decryptBackup(
            root, "device-1", "conversation-1", 1, encrypted.nonce, encrypted.ciphertext,
        )
        assertEquals(payload.toString(), restored.toString())
    }
}

