package com.pocketotp.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object RelayStart {
    private const val TAG = "PocketOTP"

    fun shouldRun(context: Context): Boolean {
        val prefs = Preferences(context)
        return prefs.enabled && prefs.endpoint.isNotBlank() && prefs.token.isNotBlank()
    }

    fun tryStart(context: Context) {
        if (!shouldRun(context)) return
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context, RelayService::class.java),
            )
        } catch (e: Exception) {
            Log.w(TAG, "cannot start RelayService: ${e.javaClass.simpleName}")
        }
    }
}
