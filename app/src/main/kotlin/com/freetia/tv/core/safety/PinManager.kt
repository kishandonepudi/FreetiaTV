package com.freetia.tv.core.safety

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/** Key/value secret storage; production is EncryptionManager's encrypted preferences. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Salted hashing with a constant-time verify; production is EncryptionManager. */
interface SecretHasher {
    fun hash(secret: String): String
    fun verify(secret: String, stored: String): Boolean
    fun newRecoveryCode(): String
}

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsLeft: Int) : PinCheck
    data class LockedOut(val remainingMs: Long) : PinCheck
    /** Nothing to check against (no PIN, recovery code or security question stored). */
    data object NotSet : PinCheck
}

/**
 * PIN, recovery code and security answer (§3.7, §5). Only salted hashes are stored, all in the encrypted store.
 * Wrong attempts are shared by the PIN and both recovery routes and persisted, so restarting the app does not
 * reset them: 5 wrong attempts lock for 30 s, and each later lockout doubles, up to 15 min.
 * Never log any value passing through this class.
 */
class PinManager(
    private val store: SecretStore,
    private val hasher: SecretHasher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun hasPin(): Boolean = onIo { store.get(PIN).isNullOrEmpty().not() }

    suspend fun hasRecoveryCode(): Boolean = onIo { store.get(RECOVERY_CODE).isNullOrEmpty().not() }

    suspend fun securityQuestion(): String? = onIo { store.get(QUESTION)?.takeIf { it.isNotBlank() } }

    /**
     * Stores [pin]. With [newRecoveryCode], also replaces the recovery code and returns it so the UI can show it
     * once; it is never retrievable again.
     */
    suspend fun setPin(pin: String, newRecoveryCode: Boolean): String? {
        require(isValidPin(pin)) { "PIN must be 4 to 6 digits" }
        return mutex.withLock {
            onIo {
                store.put(PIN, hasher.hash(pin))
                resetAttempts()
                if (newRecoveryCode) issueRecoveryCode() else null
            }
        }
    }

    /** Replaces the recovery code; returns the new code to show once. */
    suspend fun regenerateRecoveryCode(): String = mutex.withLock { onIo { issueRecoveryCode() } }

    suspend fun setSecurityQuestion(question: String, answer: String) {
        val normalized = normalizeAnswer(answer)
        require(question.isNotBlank() && normalized.isNotEmpty()) { "Question and answer are required" }
        mutex.withLock {
            onIo {
                store.put(QUESTION, question.trim())
                store.put(ANSWER, hasher.hash(normalized))
            }
        }
    }

    suspend fun verifyPin(pin: String): PinCheck = check(PIN) { pin }

    suspend fun verifyRecoveryCode(code: String): PinCheck = check(RECOVERY_CODE) { normalizeCode(code) }

    suspend fun verifyAnswer(answer: String): PinCheck = check(ANSWER) { normalizeAnswer(answer) }

    /** 0 when not locked out. */
    suspend fun lockoutRemainingMs(): Long = mutex.withLock { onIo { remainingLockout() } }

    /** Removes the PIN and every recovery secret (used only by a full reset). */
    suspend fun clear() = mutex.withLock {
        onIo { (EXPORTABLE + ATTEMPT_KEYS).forEach(store::remove) }
    }

    /** Hashes and the question for a settings export the user chose to include them in (§5). */
    suspend fun exportSecrets(): Map<String, String> =
        onIo { EXPORTABLE.mapNotNull { k -> store.get(k)?.takeIf { it.isNotEmpty() }?.let { k to it } }.toMap() }

    /** Restores [exportSecrets] output; unknown keys are ignored. */
    suspend fun importSecrets(secrets: Map<String, String>) = mutex.withLock {
        onIo { secrets.filterKeys { it in EXPORTABLE }.filterValues { it.isNotBlank() }.forEach { (k, v) -> store.put(k, v) } }
    }

    /** One-time move of hashes that older builds kept in plain DataStore. Existing secrets win. */
    suspend fun adoptLegacy(pinHash: String, recoveryCodeHash: String, question: String, answerHash: String) =
        mutex.withLock {
            onIo {
                listOf(PIN to pinHash, RECOVERY_CODE to recoveryCodeHash, QUESTION to question, ANSWER to answerHash)
                    .filter { (k, v) -> v.isNotBlank() && store.get(k).isNullOrEmpty() }
                    .forEach { (k, v) -> store.put(k, v) }
            }
        }

    private suspend fun check(key: String, input: () -> String): PinCheck = mutex.withLock {
        onIo {
            val locked = remainingLockout()
            if (locked > 0) return@onIo PinCheck.LockedOut(locked)
            val stored = store.get(key)
            if (stored.isNullOrEmpty()) return@onIo PinCheck.NotSet
            if (hasher.verify(input(), stored)) {
                resetAttempts()
                PinCheck.Ok
            } else {
                registerFailure()
            }
        }
    }

    private fun registerFailure(): PinCheck {
        val failures = (store.get(FAILURES)?.toIntOrNull() ?: 0) + 1
        if (failures < MAX_ATTEMPTS) {
            store.put(FAILURES, failures.toString())
            return PinCheck.Wrong(MAX_ATTEMPTS - failures)
        }
        val lockouts = (store.get(LOCKOUTS)?.toIntOrNull() ?: 0)
        val duration = (BASE_LOCKOUT_MS shl lockouts.coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_LOCKOUT_MS)
        store.put(LOCKED_UNTIL, (clock() + duration).toString())
        store.put(LOCKOUTS, (lockouts + 1).toString())
        store.put(FAILURES, "0")
        return PinCheck.LockedOut(duration)
    }

    private fun remainingLockout(): Long {
        val until = store.get(LOCKED_UNTIL)?.toLongOrNull() ?: return 0
        val now = clock()
        val remaining = until - now
        // The wall clock persists across restarts but can be set back; never stay locked longer than the cap.
        if (remaining > MAX_LOCKOUT_MS) {
            store.put(LOCKED_UNTIL, (now + MAX_LOCKOUT_MS).toString())
            return MAX_LOCKOUT_MS
        }
        return remaining.coerceAtLeast(0)
    }

    private fun resetAttempts() = ATTEMPT_KEYS.forEach(store::remove)

    private fun issueRecoveryCode(): String {
        val code = hasher.newRecoveryCode()
        store.put(RECOVERY_CODE, hasher.hash(normalizeCode(code)))
        return code
    }

    private suspend fun <T> onIo(block: () -> T): T = withContext(io) { block() }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 6
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
        private const val MAX_DOUBLINGS = 10

        private const val PIN = "safety_pin_hash"
        private const val RECOVERY_CODE = "safety_recovery_code_hash"
        private const val QUESTION = "safety_recovery_question"
        private const val ANSWER = "safety_recovery_answer_hash"
        private const val FAILURES = "safety_pin_failures"
        private const val LOCKOUTS = "safety_pin_lockouts"
        private const val LOCKED_UNTIL = "safety_pin_locked_until"
        private val EXPORTABLE = listOf(PIN, RECOVERY_CODE, QUESTION, ANSWER)
        private val ATTEMPT_KEYS = listOf(FAILURES, LOCKOUTS, LOCKED_UNTIL)

        fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

        /** Codes are shown grouped ("ABCD-EFGH-JKLM"); dashes, spaces and case are ignored when typed back. */
        fun normalizeCode(code: String): String = code.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        /** Case, outer spaces and repeated inner spaces don't matter ("  New  Delhi " == "new delhi"). */
        fun normalizeAnswer(answer: String): String =
            answer.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
