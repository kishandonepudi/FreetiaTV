package com.freetia.tv.data.backup

import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The text of a backup file. Without a passphrase it is plain JSON with every link and secret removed. With one it
 * is an envelope: PBKDF2-HMAC-SHA256 derives an AES-256-GCM key (same KDF and cost as EncryptionManager's hashes),
 * and the whole document, links and PIN hash included, sits inside the ciphertext.
 */
object BackupFiles {
    const val ITERATIONS = 120_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val AAD = "freetia-backup:1"
    private val random = SecureRandom()

    fun write(data: BackupData, passphrase: CharArray?, iterations: Int = ITERATIONS): String {
        if (passphrase == null || passphrase.isEmpty()) return BackupCodec.encode(data.withoutSecrets())
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray())
        val sealed = cipher.doFinal(BackupCodec.encode(data).toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("schema", BACKUP_SCHEMA_VERSION)
            .put("encrypted", JSONObject()
                .put("kdf", "pbkdf2-hmac-sha256")
                .put("iterations", iterations)
                .put("salt", enc.encodeToString(salt))
                .put("iv", enc.encodeToString(iv)))
            .put("payload", enc.encodeToString(sealed))
            .toString()
    }

    /** The text of [input], or null when it is longer than [MAX_IMPORT_CHARS] bytes. Never reads more than the cap. */
    fun readBounded(input: java.io.InputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_IMPORT_CHARS) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun isEncrypted(text: String): Boolean = text.length <= MAX_IMPORT_CHARS && try {
        JSONObject(text).has("encrypted")
    } catch (e: JSONException) {
        false
    }

    fun open(text: String, passphrase: CharArray?): BackupOpen {
        if (text.length > MAX_IMPORT_CHARS) return BackupOpen.Invalid("This file is too large to be a Freetia backup.")
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return BackupOpen.Invalid("This is not a Freetia backup file.")
        }
        if (root.optString("format") != BACKUP_FORMAT) return BackupOpen.Invalid("This is not a Freetia backup file.")
        val header = root.optJSONObject("encrypted") ?: return BackupCodec.parse(text)
        val schema = root.optInt("schema", -1)
        if (schema != BACKUP_SCHEMA_VERSION) {
            return BackupOpen.Invalid(
                if (schema > BACKUP_SCHEMA_VERSION) "This backup was made by a newer version of Freetia TV."
                else "This backup uses a format Freetia TV cannot read."
            )
        }
        if (passphrase == null || passphrase.isEmpty()) return BackupOpen.NeedsPassphrase
        val damaged = BackupOpen.Invalid("This backup is damaged.")
        val iterations = header.optInt("iterations", -1)
        if (header.optString("kdf") != "pbkdf2-hmac-sha256" || iterations !in MIN_ITERATIONS..MAX_ITERATIONS || iterations > 2 * ITERATIONS) return damaged
        return try {
            val dec = Base64.getDecoder()
            val salt = dec.decode(header.getString("salt"))
            val iv = dec.decode(header.getString("iv"))
            val sealed = dec.decode(root.getString("payload"))
            if (salt.size != 16 || iv.size != 12) return damaged
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD.toByteArray())
            BackupCodec.parse(String(cipher.doFinal(sealed), Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            BackupOpen.Invalid("Wrong passphrase, or the file is damaged.")
        } catch (e: IllegalArgumentException) {
            damaged
        } catch (e: JSONException) {
            damaged
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
