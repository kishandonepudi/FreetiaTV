package com.freetia.tv.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * State-based back stack; [Route.Home] is always the root and is never removed.
 *
 * Rules (unit-tested in NavigatorTest):
 * - [push] of a route already on the stack pops back to it instead of stacking a duplicate, which keeps every
 *   screen within two levels of Home (§2).
 * - [play] keeps a single player: it pops anything above and including an existing player, then pushes the new one.
 * - [openTopLevel] (colour buttons, menus) pops back to the nearest base (Home or player) and pushes there.
 * - [pop] returns false at Home; the host then runs the double-Back exit.
 */
@Stable
class Navigator(initial: List<Route> = listOf(Route.Home)) {
    private val entries = mutableStateListOf<Route>().apply {
        add(Route.Home)
        addAll(initial.filter { it != Route.Home })
    }

    val stack: List<Route> get() = entries
    val current: Route get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: Route) {
        val existing = entries.indexOf(route)
        if (existing >= 0) popAbove(existing) else entries.add(route)
    }

    /** Swaps the top route, e.g. channel up/down in the player. At Home it pushes instead. */
    fun replace(route: Route) {
        if (entries.size == 1) push(route) else entries[entries.lastIndex] = route
    }

    fun play(channelId: Long) {
        val player = entries.indexOfFirst { it is Route.Player }
        if (player > 0) popAbove(player - 1)
        entries.add(Route.Player(channelId))
    }

    fun openTopLevel(route: Route) {
        if (current == route) return
        while (!current.isBase) entries.removeAt(entries.lastIndex)
        if (current != route) push(route)
    }

    fun pop(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    /** Pops back to the newest [T] on the stack, or to Home if there is none. */
    inline fun <reified T : Route> popTo() {
        while (current !is T && pop()) Unit
    }

    fun popToHome() {
        popAbove(0)
    }

    private fun popAbove(index: Int) {
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
    }
}

/** §2/§3.12: Back at Home never exits on its own; a second Back within [windowMs] does. */
class DoubleBackExit(private val windowMs: Long = 2_000L) {
    private var lastBackAt: Long? = null

    /** Returns true if this Back should exit; otherwise the caller shows "Press Back again to exit". */
    fun onBack(nowMs: Long): Boolean {
        val last = lastBackAt
        if (last != null && nowMs - last in 0..windowMs) {
            lastBackAt = null
            return true
        }
        lastBackAt = nowMs
        return false
    }
}

val LocalNavigator =staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
