package com.freetia.tv.data.repository

import java.util.Locale

/**
 * Region and language filters built from tvg-country, tvg-language and group-title (§3.3).
 * Stored in the single `selectedRegion` setting as "" (never asked), "*" (asked, show everything),
 * "IN" (country) or "IN/hindi" (country and language; a star, slash and language for a language alone).
 */
data class RegionFilter(val country: String = "", val language: String = "") {
    val isAll: Boolean get() = country.isEmpty() && language.isEmpty()

    fun encode(): String = if (isAll) ALL else "${country.ifEmpty { ALL }}/$language".trimEnd('/')

    fun matches(tvgCountry: String, tvgLanguage: String): Boolean {
        if (country.isNotEmpty()) {
            val countries = RegionRules.splitValues(tvgCountry).map { it.uppercase(Locale.ROOT) }
            // Channels that carry no country are kept: many playlists simply omit it.
            if (countries.isNotEmpty() && country !in countries) return false
        }
        if (language.isNotEmpty()) {
            val languages = RegionRules.splitValues(tvgLanguage).map { it.lowercase(Locale.ROOT) }
            if (languages.isNotEmpty() && language.lowercase(Locale.ROOT) !in languages) return false
        }
        return true
    }

    companion object {
        const val ALL = "*"

        fun decode(stored: String): RegionFilter {
            if (stored.isBlank() || stored == ALL) return RegionFilter()
            val parts = stored.split('/', limit = 2)
            val country = parts[0].takeIf { it != ALL }.orEmpty().uppercase(Locale.ROOT)
            return RegionFilter(country, parts.getOrNull(1).orEmpty().lowercase(Locale.ROOT))
        }

        /** True until the user has picked a region or skipped the picker. */
        fun needsFirstRunPicker(stored: String): Boolean = stored.isBlank()
    }
}

object RegionRules {
    private val SEPARATORS = Regex("[;,|]")
    private val IGNORED_CATEGORIES = setOf("undefined", "all")

    fun splitValues(raw: String): List<String> =
        raw.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    /** Distinct upper-case country codes from raw tvg-country values, sorted. */
    fun countries(raw: Collection<String>): List<String> =
        raw.flatMap(::splitValues).map { it.uppercase(Locale.ROOT) }.distinct().sorted()

    /** Distinct lower-case languages from raw tvg-language values, sorted. */
    fun languages(raw: Collection<String>): List<String> =
        raw.flatMap(::splitValues).map { it.lowercase(Locale.ROOT) }.distinct().sorted()

    /** Distinct group-title segments, as [categoriesOf] splits them. */
    fun categories(raw: Collection<String>): List<String> =
        raw.flatMap(::categoriesOf).distinct().sorted()

    fun categoriesOf(groupTitle: String): List<String> =
        groupTitle.split(';').map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in IGNORED_CATEGORIES }
            .distinct()

    fun countryName(code: String): String =
        Locale("", code).displayCountry.takeIf { it.isNotBlank() && !it.equals(code, true) } ?: code

    fun languageName(language: String): String {
        val name = Locale(language).displayLanguage
        return if (name.isBlank() || name.equals(language, true)) language.replaceFirstChar { it.uppercase() } else name
    }
}
