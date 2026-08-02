package com.antigravity.remote.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("protected_device_keys", Context.MODE_PRIVATE)
    private val alias = "antigravity_remote_wrapping_key"

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    fun save(deviceId: String, keyVersion: Int, rootKey: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val packed = cipher.iv + cipher.doFinal(rootKey)
        preferences.edit()
            .putString("$deviceId:$keyVersion", Base64.encodeToString(packed, Base64.NO_WRAP))
            .putInt("$deviceId:current", keyVersion).apply()
    }

    fun currentVersion(deviceId: String): Int = preferences.getInt("$deviceId:current", 1)

    fun load(deviceId: String, keyVersion: Int = currentVersion(deviceId)): ByteArray? {
        val packed = preferences.getString("$deviceId:$keyVersion", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return cipher.doFinal(packed.copyOfRange(12, packed.size))
    }

    fun remove(deviceId: String) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("$deviceId:") }.forEach(editor::remove)
        editor.apply()
    }
}
