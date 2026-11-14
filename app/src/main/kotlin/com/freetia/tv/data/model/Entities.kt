package com.freetia.tv.data.model

import androidx.room.*
import java.util.*

@Entity(tableName = "playlists", indices = [Index("name"), Index("priority")])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceUrl: String,
    val epgUrl: String = "",
    val region: String = "",
    val priority: Int = 0,
    val isDefault: Boolean = false,
    val updateOnStartup: Boolean = false,
    val backgroundRefreshIntervalMinutes: Int = 60,
    val allowBackgroundPlay: Boolean = false,
    val resumeLastChannel: Boolean = true,
    val lastChannelNumber: Int = 0,
    val lastUpdateTime: Long = 0,
    val lastUpdateSuccess: Boolean = true,
    val failureMessage: String = ""
)

@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("playlistId"), Index("playlistId", "number"), Index("name")]
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
    val groupTitle: String = "",
    val tvgCountry: String = "",
    val tvgLanguage: String = "",
    val tvgType: String = "",
    val catchupEnabled: Boolean = false,
    val catchupDays: Int = 7,
    val epgChannelId: String = "",
    val isAdult: Boolean = false,
    val isMarkedAdult: Boolean = false,
    val lastPlayedTime: Long = 0,
    val preferredAudioTrack: String = "",
    val preferredAudioLanguage: String = "",
    val isCaptionsEnabled: Boolean = false,
    val lastPlayPosition: Long = 0
)

@Entity(
    tableName = "favourites",
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["channelId", "userId"], unique = true), Index("userId")]
)
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val userId: String = "default",
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_groups",
    indices = [Index("userId"), Index("name")]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val userId: String = "default",
    val description: String = "",
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

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = EpgProgrammeEntity::class,
        parentColumns = ["id"],
        childColumns = ["programmeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("programmeId"), Index("userId"), Index("reminderTime")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val programmeId: Long,
    val channelId: Long,
    val userId: String = "default",
    val title: String,
    val reminderTime: Long,
    val startTime: Long,
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
    indices = [Index("epgChannelId"), Index("start"), Index("stop")]
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
    foreignKeys = [ForeignKey(
        entity = ChannelEntity::class,
        parentColumns = ["id"],
        childColumns = ["channelId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["channelId", "userId"], unique = true), Index("userId"), Index("watchedTime")]
)
data class RecentChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val userId: String = "default",
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
