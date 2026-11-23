package com.jimmy.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = Preferences(context)
        if (!prefs.enabled) {
            Log.i(TAG, "forwarding disabled; ignoring SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val body = messages.mapNotNull { it.displayMessageBody ?: it.messageBody }.joinToString("")
        val sender = messages.firstOrNull()?.displayOriginatingAddress
            ?: messages.firstOrNull()?.originatingAddress
            ?: ""

        val subId = subscriptionId(intent)
        val (slot, carrier) = simInfo(context, subId)

        val extracted = CodeExtractor.extract(body)
        val source = listOfNotNull(
            extracted.brand?.takeIf { it.isNotBlank() },
            sender.takeIf { it.isNotBlank() },
            carrier?.takeIf { it.isNotBlank() },
        ).joinToString(" / ")

        val blocked = Blacklist.match(
            prefs.blacklistKeywords(),
            sender,
            extracted.brand,
            carrier,
            body,
        )
        if (blocked != null) {
            prefs.addHistory(
                HistoryItem(
                    code = extracted.code ?: "—",
                    source = source.ifBlank { sender },
                    timestamp = System.currentTimeMillis(),
                    skipped = true,
                    reason = "blacklist:$blocked",
                ),
            )
            Log.i(TAG, "skipped blacklisted SMS (keyword present)")
            return
        }

        if (!CodeExtractor.looksLikeVerification(body, extracted)) {
            Log.i(TAG, "not a verification SMS; not uploading")
            return
        }

        RelayClient.enqueueUpload(
            context = context,
            endpoint = prefs.endpoint,
            token = prefs.token,
            payload = RelayClient.Payload(
                code = extracted.code,
                sender = sender,
                brand = extracted.brand,
                source = source,
                body = body,
                timestamp = System.currentTimeMillis(),
                subscriptionId = subId,
                slot = slot,
                carrier = carrier,
                test = false,
            ),
        )
    }

    private fun subscriptionId(intent: Intent): Int? {
        val extras = listOf(
            "subscription",
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            "android.telephony.extra.SUBSCRIPTION_INDEX",
        )
        for (key in extras) {
            val value = intent.getIntExtra(key, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            if (value != SubscriptionManager.INVALID_SUBSCRIPTION_ID && value >= 0) return value
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun simInfo(context: Context, subId: Int?): Pair<Int?, String?> {
        if (subId == null) return null to null
        return try {
            val mgr = context.getSystemService(SubscriptionManager::class.java) ?: return null to null
            val info = mgr.getActiveSubscriptionInfo(subId) ?: return null to null
            val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                ?: info.displayName?.toString()?.takeIf { it.isNotBlank() }
            info.simSlotIndex to carrier
        } catch (e: SecurityException) {
            Log.w(TAG, "missing phone permission for SIM info")
            null to null
        } catch (e: Exception) {
            Log.w(TAG, "SIM info unavailable: ${e.javaClass.simpleName}")
            null to null
        }
    }

    companion object {
        private const val TAG = "SmsRelay"    }
}
