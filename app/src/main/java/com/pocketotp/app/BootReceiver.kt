package com.pocketotp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // MY_PACKAGE_REPLACED: an app update (or adb install -r) killed the old process and its socket.
        val reason = if (action == Intent.ACTION_MY_PACKAGE_REPLACED) "package_replaced" else "boot"
        RelayStart.tryStart(context, reason)
    }
}
