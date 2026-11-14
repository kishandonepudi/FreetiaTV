package com.freetia.tv.ui.player

private fun String.primaryLanguage() = lowercase().substringBefore('-').substringBefore('_')

/**
 * Which of [available] audio languages to play: the channel's own choice, else the global default, else null
 * (meaning the stream's default track).
 */
fun chooseAudioLanguage(available: List<String>, channelPref: String, defaultPref: String): String? =
    listOf(channelPref, defaultPref)
        .filter { it.isNotBlank() }
        .firstNotNullOfOrNull { pref -> available.firstOrNull { it.primaryLanguage() == pref.primaryLanguage() } }

/** The language to hand ExoPlayer as its preference when tuning; blank means no preference. */
fun audioPreferenceFor(channelPref: String, defaultPref: String): String? =
    channelPref.ifBlank { defaultPref }.ifBlank { null }
