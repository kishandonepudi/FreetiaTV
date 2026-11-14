package com.freetia.tv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freetia.tv.BuildConfig
import com.freetia.tv.data.model.*

@Database(
    entities = [
        ProfileEntity::class,
        PlaylistEntity::class,
        ChannelEntity::class,
        FavouriteEntity::class,
        GroupEntity::class,
        GroupChannelCrossRef::class,
        LockedChannelEntity::class,
        CategoryLockEntity::class,
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
    abstract fun profileDao(): ProfileDao
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

        /** Favourites, recents and groups reference profile 1 by default, so it must exist before any insert. */
        val SeedDefaultProfile = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO profiles (id, name, isChild, safetyLevel, avatarIndex, createdTime) " +
                        "VALUES ($DEFAULT_PROFILE_ID, 'Me', 0, '${SafetyLevel.OFF}', 0, ${System.currentTimeMillis()})"
                )
            }
        }

        fun getDatabase(context: Context): FreetiaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FreetiaDatabase::class.java,
                    "freetia_database"
                )
                    .addCallback(SeedDefaultProfile)
                    .apply {
                        // v1 is unreleased; debug installs from earlier schema drafts are simply rebuilt.
                        if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
                    }
                    .build().also { INSTANCE = it }
            }
    }
}
