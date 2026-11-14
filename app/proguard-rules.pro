# ProGuard rules for Freetia TV

# Keep all Activity and Service classes
-keep public class com.freetia.tv.ui.** extends android.app.Activity
-keep public class com.freetia.tv.playback.** extends android.app.Service
-keep public class com.freetia.tv.receiver.** extends android.content.BroadcastReceiver

# Keep all entity classes
-keep class com.freetia.tv.data.model.** { *; }

# Keep Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Keep MediaSession
-keep class androidx.media3.session.MediaSession { *; }
-keep class androidx.media3.session.MediaSessionService { *; }

# Keep Moshi/JSON
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Keep security crypto
-keep class androidx.security.crypto.** { *; }

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable

# Don't obfuscate exception names
-renamesourcefileattribute SourceFile

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }

# ExoPlayer/Media3
-dontwarn com.google.android.exoplayer2.**
-keep class androidx.media3.** { *; }
