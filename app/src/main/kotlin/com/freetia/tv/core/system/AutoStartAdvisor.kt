package com.freetia.tv.core.system

import android.content.Context

/** How the last boot launch ended; written by AutoStartWorker, read by the setup screen. */
enum class AutoStartResult { NEVER_TRIED, LAUNCHED, BLOCKED }

/** Pure rules for the one-time auto-start setup screen (§3.11). */
object AutoStartAdvisor {
    /**
     * Android 10+ blocks activity launches from the background unless the user grants an exemption, and no TV
     * build documents a reliable way to be exempt, so API 29+ is treated as blocked until a real launch succeeded.
     */
    fun launchLikelyBlocked(sdk: Int, last: AutoStartResult): Boolean = when (last) {
        AutoStartResult.LAUNCHED -> false
        AutoStartResult.BLOCKED -> true
        AutoStartResult.NEVER_TRIED -> sdk >= 29
    }

    fun shouldShowSetup(optedIn: Boolean, setupShown: Boolean, isTv: Boolean, sdk: Int, last: AutoStartResult): Boolean =
        optedIn && !setupShown && isTv && launchLikelyBlocked(sdk, last)
}

/** Last boot-launch outcome. SharedPreferences, because the worker runs before any UI exists. */
object AutoStartStatus {
    private const val FILE = "autostart_status"
    private const val KEY = "last_result"

    fun get(context: Context): AutoStartResult {
        val name = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        return AutoStartResult.entries.firstOrNull { it.name == name } ?: AutoStartResult.NEVER_TRIED
    }

    fun set(context: Context, result: AutoStartResult) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, result.name).apply()
    }
}
