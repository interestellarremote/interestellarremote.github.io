package io.interestellar.remote.security

import io.interestellar.remote.data.Envelope
import io.interestellar.remote.data.MessageType
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private val random = SecureRandom()

    data class EncryptedBackup(val nonce: String, val ciphertext: String)

    fun decode(value: String): ByteArray = java.util.Base64.getUrlDecoder().decode(value)
    fun encode(value: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    fun deriveKey(rootKey: ByteArray, deviceId: String, conversationId: String, keyVersion: Int): ByteArray {
        val info = "agy-remote/v1/$deviceId/$conversationId/$keyVersion".toByteArray()
        // RFC 5869 HKDF-SHA256 with an all-zero salt, matching cryptography.HKDF(salt=None).
        val extract = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        }.doFinal(rootKey)
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }
        return expand.doFinal(info + byteArrayOf(1)).copyOf(32)
    }

    private fun aad(deviceId: String, conversationId: String, sequence: Long, type: MessageType, keyVersion: Int) =
        "1|$deviceId|$conversationId|$sequence|${type.name}|$keyVersion".toByteArray()

    fun encrypt(
        rootKey: ByteArray,
        deviceId: String,
        conversationId: String,
        sequence: Long,
        type: MessageType,
        payload: JSONObject,
        ttlMillis: Long = 5 * 60 * 1000,
        keyVersion: Int = 1,
    ): Envelope {
        val now = System.currentTimeMillis()
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(rootKey, deviceId, conversationId, keyVersion), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(deviceId, conversationId, sequence, type, keyVersion))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(StandardCharsets.UTF_8))
        return Envelope(
            deviceId = deviceId, conversationId = conversationId, sequence = sequence, type = type,
            createdAt = now, expiresAt = now + ttlMillis, keyVersion = keyVersion,
            nonce = encode(nonce), ciphertext = encode(encrypted)
        )
    }

    fun decrypt(rootKey: ByteArray, envelope: Envelope): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = deriveKey(rootKey, envelope.deviceId, envelope.conversationId, envelope.keyVersion)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, decode(envelope.nonce)))
        cipher.updateAAD(aad(envelope.deviceId, envelope.conversationId, envelope.sequence, envelope.type, envelope.keyVersion))
        return JSONObject(String(cipher.doFinal(decode(envelope.ciphertext)), StandardCharsets.UTF_8))
    }

    fun encryptBackup(
        rootKey: ByteArray,
        deviceId: String,
        conversationId: String,
        keyVersion: Int,
        payload: JSONObject,
    ): EncryptedBackup {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(deriveKey(rootKey, deviceId, "history:$conversationId", keyVersion), "AES"),
            GCMParameterSpec(128, nonce),
        )
        cipher.updateAAD(backupAad(deviceId, conversationId, keyVersion))
        return EncryptedBackup(encode(nonce), encode(cipher.doFinal(payload.toString().toByteArray(StandardCharsets.UTF_8))))
    }

    fun decryptBackup(
        rootKey: ByteArray,
        deviceId: String,
        conversationId: String,
        keyVersion: Int,
        nonce: String,
        ciphertext: String,
    ): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveKey(rootKey, deviceId, "history:$conversationId", keyVersion), "AES"),
            GCMParameterSpec(128, decode(nonce)),
        )
        cipher.updateAAD(backupAad(deviceId, conversationId, keyVersion))
        return JSONObject(String(cipher.doFinal(decode(ciphertext)), StandardCharsets.UTF_8))
    }

    private fun backupAad(deviceId: String, conversationId: String, keyVersion: Int) =
        "history|1|$deviceId|$conversationId|$keyVersion".toByteArray(StandardCharsets.UTF_8)

    fun decryptArtifact(
        rootKey: ByteArray,
        deviceId: String,
        conversationId: String,
        remoteName: String,
        packed: ByteArray,
        keyVersion: Int = 1,
    ): ByteArray {
        require(packed.size > 33 && String(packed.copyOfRange(0, 5)) == "AGYR1") { "Artefato cifrado inválido" }
        val nonce = packed.copyOfRange(5, 17)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(rootKey, deviceId, conversationId, keyVersion), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("artifact|$deviceId|$conversationId|$remoteName|$keyVersion".toByteArray())
        return cipher.doFinal(packed.copyOfRange(17, packed.size))
    }
    fun decryptArtifactStream(
        rootKey: ByteArray,
        deviceId: String,
        conversationId: String,
        remoteName: String,
        input: java.io.InputStream,
        output: java.io.OutputStream,
        keyVersion: Int = 1,
    ) {
        val magic = ByteArray(5)
        var read = 0
        while (read < 5) {
            val res = input.read(magic, read, 5 - read)
            if (res == -1) error("EOF ao ler cabeçalho do artefato")
            read += res
        }
        require(String(magic) == "AGYR1") { "Artefato cifrado inválido" }

        val nonce = ByteArray(12)
        read = 0
        while (read < 12) {
            val res = input.read(nonce, read, 12 - read)
            if (res == -1) error("EOF ao ler nonce do artefato")
            read += res
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(rootKey, deviceId, conversationId, keyVersion), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("artifact|$deviceId|$conversationId|$remoteName|$keyVersion".toByteArray())

        javax.crypto.CipherInputStream(input, cipher).use { cis ->
            cis.copyTo(output)
        }
    }
}

