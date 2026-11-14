package com.freetia.tv.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class EncryptionManager(context: Context) {
    private val random = SecureRandom()

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "encrypted_preferences",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putSecret(key: String, value: String) = encryptedPrefs.edit().putString(key, value).apply()

    fun getSecret(key: String): String? = encryptedPrefs.getString(key, null)

    fun removeSecret(key: String) = encryptedPrefs.edit().remove(key).apply()

    /** Format: base64(salt):base64(hash). Used for the PIN, recovery code and security answer. */
    fun hash(secret: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return encode(salt) + ":" + encode(derive(secret, salt))
    }

    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = runCatching { decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { decode(parts[1]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(secret, salt), expected)
    }

    fun normalizeAnswer(answer: String): String = answer.trim().lowercase()

    fun generateRecoveryCode(): String {
        // No 0/O/1/I so the code can be read off a TV screen without ambiguity.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..12).map { alphabet[random.nextInt(alphabet.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        private const val ITERATIONS = 120_000
    }
}
