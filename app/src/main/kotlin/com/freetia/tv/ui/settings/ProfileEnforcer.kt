package com.freetia.tv.ui.settings

import com.freetia.tv.FreetiaApp
import com.freetia.tv.data.model.ProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps two profile invariants true whatever else changes settings (a restore, the Safety screen, a deleted
 * profile): a child profile always has Child Safe Mode on, and the active profile always exists.
 */
object ProfileEnforcer {
    fun start(app: FreetiaApp) {
        app.appScope.launch {
            try {
                combine(
                    app.settings.activeProfileId.flow,
                    app.database.profileDao().observeAll(),
                    app.settings.childSafeMode.flow,
                ) { id, profiles, childSafe -> Triple(id, profiles, childSafe) }.collect { (id, profiles, childSafe) ->
                    val fallback = ProfileRules.fallbackId(profiles, id)
                    if (fallback != id) app.settings.activeProfileId.set(fallback)
                    val active: ProfileEntity? = profiles.firstOrNull { it.id == fallback }
                    if (active?.isChild == true && !childSafe) app.settings.childSafeMode.set(true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The database could not be read; the next start enforces again.
            }
        }
    }
}
