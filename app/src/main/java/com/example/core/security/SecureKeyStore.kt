package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM credential vault for API keys and search secrets.
 * Keys are never stored in Room, never written to logs, and never included in exports.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val isRobolectricTest: Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private val testMemoryVault = mutableMapOf<String, String>()

    fun putSecret(keyId: String, secretValue: String) {
        val trimmed = secretValue.trim()
        if (trimmed.isEmpty()) {
            deleteSecret(keyId)
            return
        }
        if (isRobolectricTest) {
            testMemoryVault[keyId] = trimmed
            return
        }
        val secretKey = getOrCreateAesKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        prefs.edit().putString(PREFIX + keyId, encoded).apply()
    }

    fun getSecret(keyId: String): String {
        if (isRobolectricTest) {
            return testMemoryVault[keyId].orEmpty()
        }
        val encoded = prefs.getString(PREFIX + keyId, null) ?: return ""
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.isEmpty()) return ""
            val ivLen = payload[0].toInt() and 0xFF
            if (payload.size <= 1 + ivLen) return ""
            val iv = payload.copyOfRange(1, 1 + ivLen)
            val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
            val secretKey = getOrCreateAesKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun hasSecret(keyId: String): Boolean = getSecret(keyId).isNotEmpty()

    fun deleteSecret(keyId: String) {
        if (isRobolectricTest) {
            testMemoryVault.remove(keyId)
            return
        }
        prefs.edit().remove(PREFIX + keyId).apply()
    }

    fun clearAll() {
        testMemoryVault.clear()
        prefs.edit().clear().apply()
    }

    fun maskedPreview(keyId: String): String {
        val raw = getSecret(keyId)
        if (raw.isEmpty()) return ""
        return if (raw.length <= 8) "••••••••"
        else "${raw.take(4)}••••${raw.takeLast(4)}"
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_studio_master_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "ai_studio_secure_vault"
        private const val PREFIX = "enc_"

        /**
         * Redacts potential secret-like tokens before exporting or displaying diagnostic strings.
         */
        fun redactSecrets(input: String, knownSecrets: List<String>): String {
            var output = input
            for (secret in knownSecrets) {
                if (secret.length >= 6) {
                    output = output.replace(secret, "[REDACTED]")
                }
            }
            return output
        }
    }
}
