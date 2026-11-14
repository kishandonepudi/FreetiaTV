package com.freetia.tv.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceDetector {
    fun isTV(context: Context): Boolean {
        val pm = context.packageManager
        val isLeanback = pm.hasSystemFeature("android.software.leanback")
        val isFireTV = pm.hasSystemFeature("com.amazon.feature.fire_tv")
        val isAndroidTV = pm.hasSystemFeature("android.hardware.type.television")

        return isLeanback || isFireTV || isAndroidTV
    }

    fun isTablet(context: Context): Boolean {
        val config = context.resources.configuration
        val screenSize = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun isPhone(context: Context): Boolean {
        val pm = context.packageManager
        val hasPhone = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return hasPhone || (hasTouch && !isTablet(context) && !isTV(context))
    }

    fun isFire(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("com.amazon.feature.fire_tv")
    }

    fun getDeviceType(context: Context): DeviceType {
        return when {
            isTV(context) -> if (isFire(context)) DeviceType.FIRE_TV else DeviceType.ANDROID_TV
            isTablet(context) -> DeviceType.TABLET
            else -> DeviceType.PHONE
        }
    }

    enum class DeviceType {
        PHONE, TABLET, ANDROID_TV, FIRE_TV
    }
}
