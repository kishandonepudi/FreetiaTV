package com.freetia.tv.core.safety

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {
    private class MapStore : SecretStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    /** Salted like the real one, so equal secrets give different stored values. */
    private class FakeHasher : SecretHasher {
        private var salt = 0
        var codes = 0
        override fun hash(secret: String) = "${salt++}:${secret.reversed()}"
        override fun verify(secret: String, stored: String) = stored.substringAfter(':') == secret.reversed()
        override fun newRecoveryCode() = "ABCD-EFGH-JK${codes++}M"
    }

    private val store = MapStore()
    private val hasher = FakeHasher()
    private var now = 1_000_000L
    private fun manager() = PinManager(store, hasher, clock = { now }, io = Dispatchers.Unconfined)

    @Test fun setAndVerifyPin() = runTest {
        val pm = manager()
        assertFalse(pm.hasPin())
        val code = pm.setPin("1234", newRecoveryCode = true)
        assertEquals("ABCD-EFGH-JK0M", code)
        assertTrue(pm.hasPin())
        assertEquals(PinCheck.Ok, pm.verifyPin("1234"))
        assertEquals(PinCheck.Wrong(4), pm.verifyPin("4321"))
    }

    @Test fun pinIsNeverStoredInPlain() = runTest {
        manager().setPin("246810", newRecoveryCode = true)
        assertTrue(store.map.values.none { it == "246810" || it.contains("ABCD") })
    }

    @Test fun pinMustBeFourToSixDigits() {
        assertTrue(PinManager.isValidPin("1234"))
        assertTrue(PinManager.isValidPin("123456"))
        assertFalse(PinManager.isValidPin("123"))
        assertFalse(PinManager.isValidPin("1234567"))
        assertFalse(PinManager.isValidPin("12a4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPinRejectsInvalid() = runTest { manager().setPin("12", newRecoveryCode = false); Unit }

    @Test fun changePinKeepsRecoveryCode() = runTest {
        val pm = manager()
        val code = requireNotNull(pm.setPin("1111", newRecoveryCode = true))
        assertNull(pm.setPin("2222", newRecoveryCode = false))
        assertEquals(PinCheck.Wrong(4), pm.verifyPin("1111"))
        assertEquals(PinCheck.Ok, pm.verifyPin("2222"))
        assertEquals(PinCheck.Ok, pm.verifyRecoveryCode(code))
    }

    @Test fun verifyWithoutPinIsNotSet() = runTest {
        assertEquals(PinCheck.NotSet, manager().verifyPin("1234"))
        assertEquals(PinCheck.NotSet, manager().verifyAnswer("x"))
    }

    @Test fun fiveWrongAttemptsLockFor30Seconds() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = false)
        repeat(4) { assertEquals(PinCheck.Wrong(4 - it), pm.verifyPin("0000")) }
        assertEquals(PinCheck.LockedOut(30_000), pm.verifyPin("0000"))
        // Even the right PIN is refused while locked out.
        assertTrue(pm.verifyPin("1234") is PinCheck.LockedOut)
        now += 10_000
        assertEquals(20_000L, pm.lockoutRemainingMs())
        now += 20_000
        assertEquals(0L, pm.lockoutRemainingMs())
        assertEquals(PinCheck.Ok, pm.verifyPin("1234"))
    }

    @Test fun repeatedLockoutsBackOff() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = false)
        repeat(5) { pm.verifyPin("0000") }
        now += 30_000
        repeat(4) { pm.verifyPin("0000") }
        assertEquals(PinCheck.LockedOut(60_000), pm.verifyPin("0000"))
        now += 60_000
        repeat(4) { pm.verifyPin("0000") }
        assertEquals(PinCheck.LockedOut(120_000), pm.verifyPin("0000"))
    }

    @Test fun successResetsCounterAndBackoff() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = false)
        repeat(5) { pm.verifyPin("0000") }
        now += 30_000
        assertEquals(PinCheck.Ok, pm.verifyPin("1234"))
        repeat(4) { pm.verifyPin("0000") }
        assertEquals(PinCheck.LockedOut(30_000), pm.verifyPin("0000"))
    }

    @Test fun lockoutSurvivesRestart() = runTest {
        manager().apply {
            setPin("1234", newRecoveryCode = false)
            repeat(3) { verifyPin("0000") }
        }
        // A new instance over the same persisted store, as after an app restart.
        val restarted = manager()
        assertEquals(PinCheck.Wrong(1), restarted.verifyPin("0000"))
        assertTrue(restarted.verifyPin("0000") is PinCheck.LockedOut)
        assertEquals(30_000L, manager().lockoutRemainingMs())
    }

    @Test fun clockSetBackCannotExtendLockoutPastCap() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = false)
        repeat(5) { pm.verifyPin("0000") }
        now -= 10L * 24 * 3_600_000
        assertEquals(PinManager.MAX_LOCKOUT_MS, pm.lockoutRemainingMs())
    }

    @Test fun recoveryCodeIgnoresDashesAndCase() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = true)
        assertEquals(PinCheck.Ok, pm.verifyRecoveryCode("abcd efgh jk0m"))
        assertEquals(PinCheck.Wrong(4), pm.verifyRecoveryCode("ABCD-EFGH-JKLM"))
    }

    @Test fun regeneratedRecoveryCodeReplacesOld() = runTest {
        val pm = manager()
        val old = requireNotNull(pm.setPin("1234", newRecoveryCode = true))
        val fresh = pm.regenerateRecoveryCode()
        assertNotEquals(old, fresh)
        assertTrue(pm.verifyRecoveryCode(old) is PinCheck.Wrong)
        assertEquals(PinCheck.Ok, pm.verifyRecoveryCode(fresh))
    }

    @Test fun securityAnswerIsNormalised() = runTest {
        val pm = manager()
        pm.setSecurityQuestion("What city were you born in?", "  New   Delhi ")
        assertEquals("What city were you born in?", pm.securityQuestion())
        assertEquals(PinCheck.Ok, pm.verifyAnswer("new delhi"))
        assertEquals(PinCheck.Wrong(4), pm.verifyAnswer("delhi"))
        assertTrue(store.map.values.none { it.contains("delhi", ignoreCase = true) && !it.contains(':') })
    }

    @Test fun recoveryAttemptsShareTheLockout() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = true)
        pm.setSecurityQuestion("Q?", "answer")
        repeat(2) { pm.verifyAnswer("nope") }
        repeat(2) { pm.verifyRecoveryCode("nope") }
        assertTrue(pm.verifyPin("0000") is PinCheck.LockedOut)
    }

    @Test fun legacyHashesAreAdoptedOnce() = runTest {
        val pm = manager()
        pm.adoptLegacy(pinHash = "9:4321", recoveryCodeHash = "", question = "Q?", answerHash = "")
        assertEquals(PinCheck.Ok, pm.verifyPin("1234"))
        assertEquals("Q?", pm.securityQuestion())
        pm.adoptLegacy(pinHash = "9:8765", recoveryCodeHash = "", question = "", answerHash = "")
        assertEquals(PinCheck.Ok, pm.verifyPin("1234"))
    }

    @Test fun exportImportRoundTripsOnlyKnownKeys() = runTest {
        val pm = manager()
        pm.setPin("1234", newRecoveryCode = true)
        val exported = pm.exportSecrets()
        assertTrue(exported.keys.none { it.contains("failures") || it.contains("locked") })
        val other = PinManager(MapStore(), hasher, clock = { now }, io = Dispatchers.Unconfined)
        other.importSecrets(exported + ("evil" to "x"))
        assertEquals(PinCheck.Ok, other.verifyPin("1234"))
        assertEquals(exported, other.exportSecrets())
    }
}
