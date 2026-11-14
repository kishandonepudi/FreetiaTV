package com.freetia.tv.core.safety

import com.freetia.tv.FreetiaApp
import com.freetia.tv.core.security.EncryptionManager
import com.freetia.tv.data.database.ChannelDao
import com.freetia.tv.data.database.LockedChannelDao
import com.freetia.tv.data.model.CategoryLockEntity
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.LockedChannelEntity
import com.freetia.tv.data.settings.LockBehavior
import com.freetia.tv.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Child Safe Mode, channel locks and session unlock (§3.7, §3.8). App-scoped: `app.safety`.
 * UI goes through `ui/safety` (PIN dialogs, [com.freetia.tv.ui.safety.SafetyActions], LockGate).
 */
class SafetyManager(
    private val settings: SettingsRepository,
    private val lockDao: LockedChannelDao,
    private val channelDao: ChannelDao,
    val pin: PinManager,
    val session: SessionUnlock = SessionUnlock(),
) {
    val childSafeMode: Flow<Boolean> get() = settings.childSafeMode.flow

    /** Individually and bulk-locked channel ids of a playlist (padlocks, Up/Down skipping). */
    fun lockedIds(playlistId: Long): Flow<Set<Long>> =
        lockDao.observeLockedIds(playlistId).map { it.toSet() }.distinctUntilChanged()

    fun categoryLocks(): Flow<List<CategoryLockEntity>> = lockDao.observeCategoryLocks()

    suspend fun isLocked(channelId: Long): Boolean = lockDao.isChannelLocked(channelId)

    suspend fun isIndividuallyLocked(channelId: Long): Boolean = lockDao.isIndividuallyLocked(channelId)

    /** True when tuning [channel] must ask for the PIN first. */
    suspend fun needsPinToTune(channel: ChannelEntity): Boolean = !session.isUnlocked() && isLocked(channel.id)

    /** After a correct PIN for a locked channel: start the session unlock unless the user chose "Ask every time". */
    suspend fun onTunePinAccepted() {
        if (settings.defaultLockBehavior.get() == LockBehavior.SESSION_UNLOCK) {
            session.unlock(settings.sessionUnlockMinutes.get())
        }
    }

    suspend fun setChildSafeMode(on: Boolean) = settings.childSafeMode.set(on)

    suspend fun setChannelLocked(channelId: Long, locked: Boolean) {
        if (locked) lockDao.insert(LockedChannelEntity(channelId = channelId)) else lockDao.unlockChannel(channelId)
    }

    suspend fun setCategoryLocked(scope: String, value: String, locked: Boolean) {
        val v = value.trim()
        if (v.isEmpty()) return
        if (locked) lockDao.insertCategoryLock(CategoryLockEntity(scope = scope, value = v))
        else lockDao.removeCategoryLock(scope, v)
    }

    suspend fun setMarkedAdult(channelId: Long, marked: Boolean) = channelDao.markAsAdult(channelId, marked)

    /** Older debug builds kept the hashes in plain DataStore; move them into the encrypted store once. */
    internal suspend fun migrateLegacySecrets() {
        val legacy = listOf(settings.pinHash, settings.recoveryCodeHash, settings.recoveryQuestion, settings.recoveryAnswerHash)
        val values = legacy.map { it.get() }
        if (values.all { it.isEmpty() }) return
        pin.adoptLegacy(values[0], values[1], values[2], values[3])
        legacy.forEach { it.reset() }
    }

    companion object {
        fun create(app: FreetiaApp, scope: CoroutineScope): SafetyManager {
            val secrets = EncryptedSecrets(app.encryptionManager)
            val manager = SafetyManager(
                settings = app.settings,
                lockDao = app.database.lockedChannelDao(),
                channelDao = app.database.channelDao(),
                pin = PinManager(secrets, secrets),
            )
            scope.launch {
                try {
                    manager.migrateLegacySecrets()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keystore trouble: legacy values stay where they are and the move is retried next start.
                }
            }
            return manager
        }
    }
}

private class EncryptedSecrets(private val em: EncryptionManager) : SecretStore, SecretHasher {
    override fun get(key: String): String? = em.getSecret(key)
    override fun put(key: String, value: String) = em.putSecret(key, value)
    override fun remove(key: String) = em.removeSecret(key)
    override fun hash(secret: String): String = em.hash(secret)
    override fun verify(secret: String, stored: String): Boolean = em.verify(secret, stored)
    override fun newRecoveryCode(): String = em.generateRecoveryCode()
}
