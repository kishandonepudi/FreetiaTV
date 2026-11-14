package com.freetia.tv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.freetia.tv.data.model.*

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        FavouriteEntity::class,
        GroupEntity::class,
        GroupChannelCrossRef::class,
        LockedChannelEntity::class,
        ReminderEntity::class,
        EpgProgrammeEntity::class,
        EpgChannelEntity::class,
        RecentChannelEntity::class,
        PlaylistHealthCheckEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FreetiaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSyncDao(): PlaylistSyncDao
    abstract fun channelDao(): ChannelDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun groupDao(): GroupDao
    abstract fun lockedChannelDao(): LockedChannelDao
    abstract fun reminderDao(): ReminderDao
    abstract fun epgDao(): EpgDao
    abstract fun recentChannelDao(): RecentChannelDao
    abstract fun healthCheckDao(): PlaylistHealthCheckDao

    companion object {
        @Volatile
        private var INSTANCE: FreetiaDatabase? = null

        fun getDatabase(context: Context): FreetiaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FreetiaDatabase::class.java,
                    "freetia_database"
                ).build().also { INSTANCE = it }
            }
    }
}
