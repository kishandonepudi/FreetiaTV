package com.freetia.tv.ui.settings

import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.ProfileEntity
import com.freetia.tv.data.model.SafetyLevel
import com.freetia.tv.data.settings.ColorAction
import com.freetia.tv.data.settings.ColorButton

/** Remote colour buttons (§2): which actions are offered and which assignments clash. */
object ColorButtonRules {
    /** The six choices of §3.14; an older saved Recents or Groups value is still shown as it is. */
    val options = listOf(
        ColorAction.FAVOURITES, ColorAction.SEARCH, ColorAction.EPG,
        ColorAction.SETTINGS, ColorAction.PLAYLISTS, ColorAction.NONE,
    )

    fun optionsFor(current: ColorAction): List<ColorAction> = if (current in options) options else options + current

    fun label(action: ColorAction): String = when (action) {
        ColorAction.FAVOURITES -> "Favourites"
        ColorAction.SEARCH -> "Search"
        ColorAction.EPG -> "TV guide"
        ColorAction.SETTINGS -> "Settings"
        ColorAction.PLAYLISTS -> "Playlists"
        ColorAction.RECENTS -> "Recently watched"
        ColorAction.GROUPS -> "Groups"
        ColorAction.NONE -> "Off"
    }

    fun buttonLabel(button: ColorButton): String = when (button) {
        ColorButton.RED -> "Red button"
        ColorButton.GREEN -> "Green button"
        ColorButton.YELLOW -> "Yellow button"
        ColorButton.BLUE -> "Blue button"
    }

    /** Actions that more than one button opens. Off may repeat. */
    fun duplicates(mapping: Map<ColorButton, ColorAction>): Set<ColorAction> =
        mapping.values.filter { it != ColorAction.NONE }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}

/** Profile rules (§3.15). */
object ProfileRules {
    const val MAX_PROFILES = 8
    const val MAX_NAME = 24

    fun canDelete(profile: ProfileEntity): Boolean = profile.id != DEFAULT_PROFILE_ID

    /** A child profile always has Child Safe Mode; the stored level is forced to match. */
    fun normalized(profile: ProfileEntity): ProfileEntity =
        if (profile.isChild) profile.copy(safetyLevel = SafetyLevel.CHILD_SAFE) else profile

    fun isChildSafe(profile: ProfileEntity): Boolean = profile.isChild || profile.safetyLevel == SafetyLevel.CHILD_SAFE

    /** Leaving a child profile (or changing profiles from inside one) needs the PIN, once one exists. */
    fun needsPin(active: ProfileEntity?, hasPin: Boolean): Boolean = hasPin && active?.isChild == true

    /** Whether Child Safe Mode should be on after switching to [target]. */
    fun childSafeAfterSwitch(target: ProfileEntity): Boolean = isChildSafe(target)

    fun nameError(name: String, others: List<ProfileEntity>): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Type a name."
            clean.length > MAX_NAME -> "Use $MAX_NAME characters or fewer."
            others.any { it.name.trim().equals(clean, ignoreCase = true) } -> "Another profile already has that name."
            else -> null
        }
    }

    /** The profile to use when the active one disappears. */
    fun fallbackId(profiles: List<ProfileEntity>, activeId: Long): Long =
        if (profiles.any { it.id == activeId }) activeId else DEFAULT_PROFILE_ID
}

/** Whether a profile change must be confirmed with the PIN (once a PIN exists). */
object ProfileSwitchRules {
    /**
     * Leaving a child profile always needs the PIN. So does any switch that lowers safety from what is on now, so a
     * child cannot step into an unrestricted profile.
     */
    fun needsPin(active: ProfileEntity?, target: ProfileEntity, childSafeNow: Boolean, hasPin: Boolean): Boolean {
        if (!hasPin || active == null || active.id == target.id) return false
        return active.isChild || (childSafeNow && !ProfileRules.isChildSafe(target))
    }

    /**
     * Deleting the active profile falls back to [home], which is a switch and needs the same PIN. A child profile
     * already asked for the PIN before the delete, so it is not asked twice.
     */
    fun needsPinToDelete(active: ProfileEntity?, home: ProfileEntity, childSafeNow: Boolean, hasPin: Boolean): Boolean =
        needsPin(active, home, childSafeNow, hasPin) && !ProfileRules.needsPin(active, hasPin)
}
