package com.pocketotp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfirmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ConfirmManager.EXTRA_ID) ?: return
        // Notifications can only deny. Allow is reachable solely from ConfirmActivity.
        if (intent.action != ConfirmManager.ACTION_DENY) return
        val decision = "deny"
        val pending = goAsync()
        Thread({
            try {
                ConfirmManager.submitDecision(context, id, decision)
            } finally {
                pending.finish()
            }
        }, "pocketotp-confirm").start()
    }
}
