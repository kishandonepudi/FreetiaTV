package com.freetia.tv.core.safety

/**
 * Session unlock for locked channels (§3.8). Memory only by design: it must never survive a restart, so a full
 * exit always locks again. [clock] is monotonic so changing the device time can't extend an unlock.
 */
class SessionUnlock(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    @Volatile private var until: Long? = null

    /** [minutes] 0 or less = until exit. */
    fun unlock(minutes: Int) {
        until = if (minutes <= 0) UNTIL_EXIT else clock() + minutes * 60_000L
    }

    fun lock() {
        until = null
    }

    fun isUnlocked(): Boolean {
        val end = until ?: return false
        if (end == UNTIL_EXIT || clock() < end) return true
        until = null
        return false
    }

    /** null when locked, [Long.MAX_VALUE] when unlocked until exit. */
    fun remainingMs(): Long? {
        if (!isUnlocked()) return null
        val end = until ?: return null
        return if (end == UNTIL_EXIT) Long.MAX_VALUE else end - clock()
    }

    private companion object {
        const val UNTIL_EXIT = Long.MAX_VALUE
    }
}
