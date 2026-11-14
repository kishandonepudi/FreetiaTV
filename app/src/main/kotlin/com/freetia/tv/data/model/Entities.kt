package com.freetia.tv.data.model

import androidx.room.*

/** Profile 1 always exists (seeded in FreetiaDatabase's onCreate callback) and cannot be deleted. */
const val DEFAULT_PROFILE_ID = 1L

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Child profiles always have Child Safe Mode on (§3.15). */
    val isChild: Boolean = false,
    /** One of [SafetyLevel]. */
    val safetyLevel: String = SafetyLevel.OFF,
    val avatarIndex: Int = 0,
    val createdTime: Long = System.currentTimeMillis(),
)

object SafetyLevel {
    const val OFF = "off"
    const val CHILD_SAFE = "child_safe"
}

@Entity(tableName = "playlists", indices = [Index("name"), Index("priority")])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** A [com.freetia.tv.data.secure.PlaylistUrlStore] reference key, never the URL itself (URLs can carry tokens). */
    val sourceUrl: String,
    /** From the playlist's x-tvg-url header; refreshed by sync. */
    val epgUrl: String = "",
    /** User-supplied XMLTV URL; wins over [epgUrl] when set. */
    val userEpgUrl: String = "",
    val region: String = "",
    /** 1 = highest. */
    val priority: Int = 0,
    val isDefault: Boolean = false,
    val updateOnStartup: Boolean = false,
    val backgroundRefreshIntervalMinutes: Int = 24 * 60,
    val allowBackgroundPlay: Boolean = false,
    val resumeLastChannel: Boolean = true,
    val lastChannelId: Long = 0,
    val lastChannelNumber: Int = 0,
    val lastUpdateTime: Long = 0,
    val lastUpdateSuccess: Boolean = true,
    val failureMessage: String = "",
    val epgLastUpdateTime: Long = 0,
    val epgLastUpdateSuccess: Boolean = true,
)

@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("playlistId"), Index("playlistId", "number"), Index("name"),
        Index("playlistId", "groupTitle"), Index("playlistId", "tvgCountry"), Index("tvgId"),
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val tvgId: String = "",
    val tvgName: String = "",
    val tvgLogo: String = "",
    val name: String,
    val number: Int = 0,
    val streamUrl: String,
    /** Raw group-title; several categories may be joined with ';'. */
    val groupTitle: String = "",
    /** Raw tvg-country, e.g. "IN" or "IN;US". */
    val tvgCountry: String = "",
    val tvgLanguage: String = "",
    val tvgType: String = "",
    val catchupEnabled: Boolean = false,
    /** catchup attribute: default, append, shift, flussonic, xc… Empty when absent. */
    val catchup: String = "",
    val catchupSource: String = "",
    val catchupDays: Int = 0,
    val epgChannelId: String = "",
    /** Detected from group title / keywords at sync time. */
    val isAdult: Boolean = false,
    /** User's manual "Mark as adult"; never overwritten by sync. */
    val isMarkedAdult: Boolean = false,
    /** Set by the health check; lists can hide these (HIDDEN_DEAD_STREAMS). */
    val isDead: Boolean = false,
    val lastPlayedTime: Long = 0,
    val preferredAudioTrack: String = "",
    val preferredAudioLanguage: String = "",
    val isCaptionsEnabled: Boolean = false,
    val lastPlayPosition: Long = 0
)

@Entity(
    tableName = "favourites",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index(value = ["channelId", "profileId"], unique = true), Index("profileId")]
)
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val profileId: Long = DEFAULT_PROFILE_ID,
    val sortOrder: Int = 0,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_groups",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index("name")]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val profileId: Long = DEFAULT_PROFILE_ID,
    val description: String = "",
    val sortOrder: Int = 0,
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_channels",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId", "channelId"], unique = true), Index("channelId")]
)
data class GroupChannelCrossRef(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val channelId: Long
)

@Entity(
    tableName = "locked_channels",
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["channelId"], unique = true)]
)
data class LockedChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val lockedTime: Long = System.currentTimeMillis()
)

/**
 * Bulk lock by group-title category or tvg-country region (§3.8). Value-based rather than per-channel rows, so
 * channels that a later refresh adds to the group or region are locked too.
 */
@Entity(tableName = "category_locks", indices = [Index(value = ["scope", "value"], unique = true)])
data class CategoryLockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** One of [LockScope]. */
    val scope: String,
    /** A single category (one segment of group-title) or a region code, matched case-insensitively. */
    val value: String,
    val lockedTime: Long = System.currentTimeMillis()
)

object LockScope {
    const val GROUP = "group"
    const val REGION = "region"
}

/**
 * Programme details are copied in rather than referenced by a foreign key: an EPG refresh replaces programme rows
 * and must not cascade-delete the user's reminders.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("reminderTime"), Index(value = ["channelId", "startTime"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Soft reference to epg_programmes.id at the time it was set; may no longer exist. */
    val programmeId: Long = 0,
    val channelId: Long,
    val title: String,
    /** When to fire: startTime minus the lead time. */
    val reminderTime: Long,
    val startTime: Long,
    val stopTime: Long = 0,
    /** Wake the screen at start time (full-screen intent). */
    val wakeScreen: Boolean = false,
    /** Tune to the channel at start time. */
    val autoTune: Boolean = false,
    val isNotified: Boolean = false,
    val isWoken: Boolean = false,
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg_channels",
    indices = [Index("channelId"), Index("epgChannelId")]
)
data class EpgChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val epgChannelId: String,
    val channelName: String = "",
    val icon: String = ""
)

@Entity(
    tableName = "epg_programmes",
    foreignKeys = [ForeignKey(
        entity = EpgChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["epgChannelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("epgChannelId", "start"), Index("start"), Index("stop"), Index("tvgId", "start")]
)
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val epgChannelId: Long,
    val tvgId: String = "",
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String = "",
    val category: String = "",
    val icon: String = "",
    val rating: Int = 0,
    val length: Int = 0
)

@Entity(
    tableName = "recent_channels",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index(value = ["channelId", "profileId"], unique = true), Index("profileId", "watchedTime")]
)
data class RecentChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val profileId: Long = DEFAULT_PROFILE_ID,
    val watchedTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "health_checks",
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["channelId"], unique = true)]
)
data class PlaylistHealthCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val isHealthy: Boolean = true,
    val lastCheckTime: Long = System.currentTimeMillis(),
    val errorMessage: String = ""
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>?): String = value?.joinToString(",") ?: ""

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")
}
