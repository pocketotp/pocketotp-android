package com.jimmy.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfirmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ConfirmManager.EXTRA_ID) ?: return
        val decision = when (intent.action) {
            ConfirmManager.ACTION_ALLOW -> "allow"
            ConfirmManager.ACTION_DENY -> "deny"
            else -> return
        }
        val pending = goAsync()
        Thread({
            try {
                ConfirmManager.submitDecision(context, id, decision)
            } finally {
                pending.finish()
            }
        }, "sms-relay-confirm").start()
    }
}
