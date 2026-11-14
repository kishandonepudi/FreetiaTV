package com.freetia.tv.data.backup

import java.net.URI

/** Allowed shape of every exported setting, keyed by DataStore key name. Unknown keys in a file are ignored. */
object SettingRules {
    sealed interface Rule
    data object Flag : Rule
    data class Whole(val range: IntRange) : Rule
    data class Fraction(val min: Float, val max: Float) : Rule
    data class Text(val maxLength: Int, val allowed: Set<String>? = null) : Rule

    private val colorActions = setOf("favourites", "search", "epg", "settings", "playlists", "recents", "groups", "none")

    val rules: Map<String, Rule> = mapOf(
        "open_player_on_startup" to Flag,
        "auto_start_on_tv" to Flag,
        "resume_last_channel" to Flag,
        "theme_mode" to Text(10, setOf("dark", "light", "system")),
        "text_size" to Whole(50..200),
        "high_contrast" to Flag,
        "osd_timeout_seconds" to Whole(1..60),
        "buffer_mode" to Text(16, setOf("low_latency", "balanced", "stable", "custom")),
        "buffer_min_ms" to Whole(1_000..120_000),
        "buffer_max_ms" to Whole(1_000..240_000),
        "default_quality" to Text(16),
        "auto_quality" to Flag,
        "quality_notifications" to Flag,
        "lock_quality" to Flag,
        "aspect_ratio" to Text(16, setOf("fit", "fill", "zoom", "ratio_16_9", "ratio_4_3")),
        "subtitle_size" to Whole(25..300),
        "subtitle_color" to Text(20),
        "subtitle_bg_color" to Text(20),
        "subtitle_bg_opacity" to Fraction(0f, 1f),
        "default_audio_language" to Text(8),
        "subtitle_use_system_style" to Flag,
        "back_returns_to_previous" to Flag,
        "channel_change_instant" to Flag,
        "channel_change_osd_delay_ms" to Whole(0..10_000),
        "number_entry_timeout_ms" to Whole(500..10_000),
        "color_button_red" to Text(12, colorActions),
        "color_button_green" to Text(12, colorActions),
        "color_button_yellow" to Text(12, colorActions),
        "color_button_blue" to Text(12, colorActions),
        "child_safe_mode" to Flag,
        "default_lock_behavior" to Text(16, setOf("session_unlock", "always_ask")),
        "session_unlock_duration_minutes" to Whole(0..1_440),
        "sleep_timer_enabled" to Flag,
        "sleep_timer_default_minutes" to Whole(10..240),
        "clear_cache_on_exit" to Flag,
        "clear_cache_on_startup" to Flag,
        "logo_cache_size_mb" to Whole(10..500),
        "reminder_lead_time_minutes" to Whole(0..120),
        "reminder_wake_screen" to Flag,
        "reminder_auto_tune" to Flag,
        "epg_refresh_interval_hours" to Whole(1..168),
        "epg_days" to Whole(1..14),
        "selected_region" to Text(64),
        "hidden_dead_streams" to Flag,
    )

    /** Never in the settings section: secrets (own sections), per-device ids, first-run markers, opt-in analytics. */
    val excluded: Set<String> = setOf(
        "pin_hash", "recovery_code_hash", "recovery_question", "recovery_answer_hash", "epg_source_url",
        "default_playlist_id", "active_profile_id", "first_run_completed", "default_playlist_seeded",
        "first_run_pin_setup", "auto_start_setup_shown", "analytics_enabled",
    )

    /** The normalised value, or null when [value] is not allowed for [key] (or the key is unknown). */
    fun normalize(key: String, value: Any?): Any? = when (val rule = rules[key]) {
        null -> null
        Flag -> value as? Boolean
        is Whole -> (value as? Number)
            ?.takeIf { it is Int || it is Long || it.toDouble() == it.toLong().toDouble() }
            ?.toLong()?.takeIf { it in rule.range.first.toLong()..rule.range.last.toLong() }?.toInt()
        is Fraction -> (value as? Number)?.toFloat()?.takeIf { it in rule.min..rule.max }
        is Text -> (value as? String)?.takeIf { it.length <= rule.maxLength && (rule.allowed == null || it in rule.allowed) }
    }
}

object UrlRules {
    const val MAX_LENGTH = 2_048

    /** Only http and https links with a host; everything else (file:, content:, javascript:) is refused. */
    fun isHttpUrl(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_LENGTH || value.any { it.isWhitespace() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }
}
