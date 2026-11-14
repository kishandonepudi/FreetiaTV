package com.freetia.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freetia.tv.data.model.DEFAULT_PROFILE_ID
import com.freetia.tv.data.model.ProfileEntity
import com.freetia.tv.data.model.SafetyLevel
import com.freetia.tv.ui.components.BrandDialog
import com.freetia.tv.ui.components.FocusPill
import com.freetia.tv.ui.components.LocalBrandToast
import com.freetia.tv.ui.components.SettingActionRow
import com.freetia.tv.ui.components.SettingsScaffold
import com.freetia.tv.ui.nav.Navigator
import com.freetia.tv.ui.safety.rememberSafetyActions

private sealed interface ProfileDialog {
    data class Create(val child: Boolean) : ProfileDialog
    data class Manage(val profile: ProfileEntity) : ProfileDialog
    data class Rename(val profile: ProfileEntity) : ProfileDialog
    data class Delete(val profile: ProfileEntity) : ProfileDialog
}

private fun ProfileEntity.describeKind(active: Boolean): String = buildList {
    if (active) add("Active")
    add(if (isChild) "Child profile" else if (safetyLevel == SafetyLevel.CHILD_SAFE) "Child Safe Mode on" else "Standard")
}.joinToString(" · ")

/**
 * Settings > Advanced > Profiles (§3.15). Each profile has its own favourites, groups and recents, and a safety level.
 * A child profile always runs Child Safe Mode, and leaving one needs the PIN.
 */
@Composable
fun ProfilesScreen(nav: Navigator) {
    val c = rememberSettingsCtx()
    val toast = LocalBrandToast.current
    val actions = rememberSafetyActions()
    val profiles by remember { c.app.database.profileDao().observeAll() }.collectAsState(initial = emptyList())
    val activeId = c.s.activeProfileId.collect()
    val childSafeNow = c.s.childSafeMode.collect()
    val active = profiles.firstOrNull { it.id == activeId }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val dao = c.app.database.profileDao()

    /** Runs [block] after the PIN when management from the active profile is restricted. */
    fun gated(block: () -> Unit) = c.launch {
        if (ProfileRules.needsPin(active, c.app.safety.pin.hasPin())) actions.withPin("Enter your PIN to change profiles", action = block)
        else block()
    }

    fun applySwitch(target: ProfileEntity) = c.launch {
        c.s.activeProfileId.set(target.id)
        c.app.safety.setChildSafeMode(ProfileRules.childSafeAfterSwitch(target))
        toast.show("Switched to ${target.name}.")
    }

    fun switchTo(target: ProfileEntity) = c.launch {
        val hasPin = c.app.safety.pin.hasPin()
        if (ProfileSwitchRules.needsPin(active, target, childSafeNow, hasPin)) {
            actions.withPin("Enter your PIN to switch profile") { applySwitch(target) }
        } else applySwitch(target)
    }

    fun create(name: String, child: Boolean) = c.launch {
        val profile = ProfileRules.normalized(ProfileEntity(name = name, isChild = child))
        dao.insert(profile)
        toast.show("Profile \"$name\" created.")
    }

    fun setChild(profile: ProfileEntity, child: Boolean) {
        val apply = {
            c.launch {
                val updated = if (child) ProfileRules.normalized(profile.copy(isChild = true))
                else profile.copy(isChild = false, safetyLevel = SafetyLevel.OFF)
                dao.update(updated)
                if (profile.id == activeId) c.app.safety.setChildSafeMode(ProfileRules.childSafeAfterSwitch(updated))
            }
            Unit
        }
        // A child profile is only meaningful with a PIN to leave it, so one is created first.
        if (child) actions.ensurePin { gated(apply) } else gated { actions.withPin("Enter your PIN to turn off the child profile", action = apply) }
    }

    fun setLevel(profile: ProfileEntity, level: String) {
        val apply = {
            c.launch {
                val updated = profile.copy(safetyLevel = level)
                dao.update(updated)
                if (profile.id == activeId) c.app.safety.setChildSafeMode(ProfileRules.childSafeAfterSwitch(updated))
            }
            Unit
        }
        if (level == SafetyLevel.CHILD_SAFE) gated(apply)
        else gated { actions.withPin("Enter your PIN to lower the safety level", action = apply) }
    }

    SettingsScaffold(
        title = "Profiles",
        subtitle = "Favourites, groups and recently watched are kept separately for each profile.",
    ) {
        items(profiles, key = { it.id }) { p ->
            SettingActionRow(
                p.name, onClick = { dialog = ProfileDialog.Manage(p) },
                subtitle = p.describeKind(p.id == activeId),
                modifier = Modifier.describe(p.name, p.describeKind(p.id == activeId)),
            )
        }
        if (profiles.size < ProfileRules.MAX_PROFILES) {
            item {
                SettingActionRow(
                    "New profile", onClick = { gated { dialog = ProfileDialog.Create(child = false) } },
                    modifier = Modifier.describe("Add a new profile"),
                )
            }
            item {
                SettingActionRow(
                    "New child profile",
                    onClick = { actions.ensurePin { gated { dialog = ProfileDialog.Create(child = true) } } },
                    subtitle = "Child Safe Mode is always on, and leaving it needs your PIN",
                    modifier = Modifier.describe("Add a new child profile"),
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ProfileDialog.Create -> TextPromptDialog(
            title = if (d.child) "New child profile" else "New profile",
            label = "Name",
            initial = "",
            confirmLabel = "Create",
            maxLength = ProfileRules.MAX_NAME,
            validate = { ProfileRules.nameError(it, profiles) },
            onConfirm = { create(it, d.child); dialog = null },
            onDismiss = { dialog = null },
        )
        is ProfileDialog.Rename -> TextPromptDialog(
            title = "Rename profile",
            label = "Name",
            initial = d.profile.name,
            confirmLabel = "Save",
            maxLength = ProfileRules.MAX_NAME,
            validate = { ProfileRules.nameError(it, profiles.filter { p -> p.id != d.profile.id }) },
            onConfirm = { name -> c.launch { dao.update(d.profile.copy(name = name)) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is ProfileDialog.Delete -> BrandDialog(
            title = "Delete ${d.profile.name}?",
            message = "Its favourites, groups and recently watched are removed. This cannot be undone.",
            confirmLabel = "Delete", destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                val target = d.profile
                dialog = null
                gated {
                    c.launch {
                        val home = if (target.id == activeId) dao.getById(DEFAULT_PROFILE_ID) else null
                        val delete = {
                            c.launch {
                                dao.deleteById(target.id)
                                if (home != null) applySwitch(home)
                                toast.show("Profile deleted.")
                            }
                            Unit
                        }
                        if (home != null && ProfileSwitchRules.needsPinToDelete(active, home, childSafeNow, c.app.safety.pin.hasPin())) {
                            actions.withPin("Enter your PIN to delete this profile", action = delete)
                        } else delete()
                    }
                }
            },
        )
        is ProfileDialog.Manage -> {
            val p = profiles.firstOrNull { it.id == d.profile.id } ?: d.profile
            BrandDialog(
                title = p.name,
                message = p.describeKind(p.id == activeId),
                confirmLabel = if (p.id == activeId) "Close" else "Switch to this profile",
                onConfirm = { dialog = null; if (p.id != activeId) switchTo(p) },
                dismissLabel = null,
                onDismiss = { dialog = null },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusPill("Rename", { gated { dialog = ProfileDialog.Rename(p) } })
                    FocusPill(
                        if (p.isChild) "Turn off child profile" else "Make this a child profile",
                        { setChild(p, !p.isChild); dialog = null },
                    )
                    if (!p.isChild) {
                        val on = p.safetyLevel == SafetyLevel.CHILD_SAFE
                        FocusPill(
                            if (on) "Child Safe Mode: on" else "Child Safe Mode: off",
                            { setLevel(p, if (on) SafetyLevel.OFF else SafetyLevel.CHILD_SAFE); dialog = null },
                            primary = on,
                        )
                    }
                    if (ProfileRules.canDelete(p)) {
                        FocusPill("Delete", { dialog = ProfileDialog.Delete(p) })
                    }
                }
            }
        }
    }
}
