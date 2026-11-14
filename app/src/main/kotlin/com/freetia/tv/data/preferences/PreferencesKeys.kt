package com.freetia.tv.data.preferences

import androidx.datastore.preferences.core.*

object PreferencesKeys {
    // Startup settings
    val DEFAULT_PLAYLIST_ID = longPreferencesKey("default_playlist_id")
    val OPEN_PLAYER_ON_STARTUP = booleanPreferencesKey("open_player_on_startup")
    val AUTO_START_ON_TV = booleanPreferencesKey("auto_start_on_tv")
    val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
    val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")

    // Display settings
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TEXT_SIZE = intPreferencesKey("text_size")
    val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    val OSD_TIMEOUT_SECONDS = intPreferencesKey("osd_timeout_seconds")

    // Playback settings
    val BUFFER_MODE = stringPreferencesKey("buffer_mode")
    val BUFFER_MIN_MS = intPreferencesKey("buffer_min_ms")
    val BUFFER_MAX_MS = intPreferencesKey("buffer_max_ms")
    val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    val AUTO_QUALITY = booleanPreferencesKey("auto_quality")
    val QUALITY_NOTIFICATIONS = booleanPreferencesKey("quality_notifications")
    val LOCK_QUALITY = booleanPreferencesKey("lock_quality")
    val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")

    // Captions and audio
    val SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
    val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
    val SUBTITLE_BG_COLOR = stringPreferencesKey("subtitle_bg_color")
    val SUBTITLE_BG_OPACITY = floatPreferencesKey("subtitle_bg_opacity")
    val DEFAULT_AUDIO_LANGUAGE = stringPreferencesKey("default_audio_language")
    val SUBTITLE_USE_SYSTEM_STYLE = booleanPreferencesKey("subtitle_use_system_style")

    // Navigation
    val BACK_RETURNS_TO_PREVIOUS = booleanPreferencesKey("back_returns_to_previous")
    val CHANNEL_CHANGE_INSTANT = booleanPreferencesKey("channel_change_instant")
    val CHANNEL_CHANGE_OSD_DELAY_MS = intPreferencesKey("channel_change_osd_delay_ms")
    val NUMBER_ENTRY_TIMEOUT_MS = intPreferencesKey("number_entry_timeout_ms")

    // Color buttons
    val COLOR_BUTTON_RED = stringPreferencesKey("color_button_red")
    val COLOR_BUTTON_GREEN = stringPreferencesKey("color_button_green")
    val COLOR_BUTTON_YELLOW = stringPreferencesKey("color_button_yellow")
    val COLOR_BUTTON_BLUE = stringPreferencesKey("color_button_blue")

    // Safety
    val CHILD_SAFE_MODE = booleanPreferencesKey("child_safe_mode")
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val RECOVERY_CODE_HASH = stringPreferencesKey("recovery_code_hash")
    val RECOVERY_QUESTION = stringPreferencesKey("recovery_question")
    val RECOVERY_ANSWER_HASH = stringPreferencesKey("recovery_answer_hash")
    val DEFAULT_LOCK_BEHAVIOR = stringPreferencesKey("default_lock_behavior")
    val SESSION_UNLOCK_DURATION_MINUTES = intPreferencesKey("session_unlock_duration_minutes")

    // Sleep timer
    val SLEEP_TIMER_ENABLED = booleanPreferencesKey("sleep_timer_enabled")
    val SLEEP_TIMER_DEFAULT_MINUTES = intPreferencesKey("sleep_timer_default_minutes")

    // Cache
    val CLEAR_CACHE_ON_EXIT = booleanPreferencesKey("clear_cache_on_exit")
    val CLEAR_CACHE_ON_STARTUP = booleanPreferencesKey("clear_cache_on_startup")
    val LOGO_CACHE_SIZE_MB = intPreferencesKey("logo_cache_size_mb")

    // Reminders
    val REMINDER_LEAD_TIME_MINUTES = intPreferencesKey("reminder_lead_time_minutes")
    val REMINDER_WAKE_SCREEN = booleanPreferencesKey("reminder_wake_screen")
    val REMINDER_AUTO_TUNE = booleanPreferencesKey("reminder_auto_tune")

    // EPG
    val EPG_SOURCE_URL = stringPreferencesKey("epg_source_url")
    val EPG_REFRESH_INTERVAL_HOURS = intPreferencesKey("epg_refresh_interval_hours")
    val EPG_DAYS = intPreferencesKey("epg_days")

    // Region and content
    val SELECTED_REGION = stringPreferencesKey("selected_region")
    val HIDDEN_DEAD_STREAMS = booleanPreferencesKey("hidden_dead_streams")

    // First run
    val FIRST_RUN_COMPLETED = booleanPreferencesKey("first_run_completed")
    val DEFAULT_PLAYLIST_SEEDED = booleanPreferencesKey("default_playlist_seeded")
    val FIRST_RUN_PIN_SETUP = booleanPreferencesKey("first_run_pin_setup")
    val AUTO_START_SETUP_SHOWN = booleanPreferencesKey("auto_start_setup_shown")

    // Telemetry
    val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
}
