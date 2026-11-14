package com.freetia.tv.work

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freetia.tv.ui.MainActivity

class AutoStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            // Android 10+ blocks background activity launches unless the user grants an exemption; retrying won't help.
            Log.w("AutoStartWorker", "Auto-start launch blocked: ${e.message}")
        }
        return Result.success()
    }
}
