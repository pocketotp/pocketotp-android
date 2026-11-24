package com.jimmy.smsrelay

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object SendSms {
    const val ACTION_SENT = "com.jimmy.smsrelay.SMS_SENT"
    const val ACTION_DELIVERED = "com.jimmy.smsrelay.SMS_DELIVERED"
    const val EXTRA_COMMAND_ID = "command_id"
    const val EXTRA_PARTS = "parts"
    const val EXTRA_PART_INDEX = "part_index"

    fun handleCommand(context: Context, command: JSONObject) {
        val id = command.optString("id")
        if (id.isBlank()) return
        if (!handledIds.add(id)) {
            Log.i(TAG, "ignore duplicate command id=$id")
            return
        }
        val prefs = Preferences(context)
        val dryRun = command.optBoolean("dryRun") || command.optBoolean("dry_run")
        val simSpec = command.optJSONObject("sim") ?: JSONObject()
        val sender = SimInfo.resolve(context, simSpec)
        if (sender == null) {
            report(context, id, "failed", "sim_not_found", null)
            return
        }

        val toRaw = command.optString("to")
        val to = if (toRaw == "other_sim") {
            SimInfo.otherNumber(context, sender)
        } else {
            toRaw
        }
        if (to.isNullOrBlank()) {
            report(context, id, "failed", if (toRaw == "other_sim") "other_sim_number_unknown" else "invalid_to", sender)
            return
        }
        val text = command.optString("text")
        if (text.isEmpty() || text.length > 500) {
            report(context, id, "failed", "invalid_text", sender)
            return
        }

        prefs.addSend(
            SendItem(
                id = id,
                toMasked = SimInfo.maskNumber(to) ?: "****",
                simLabel = label(simSpec, sender),
                status = if (dryRun) "sent" else "queued",
                timestamp = System.currentTimeMillis(),
                error = if (dryRun) "dry_run" else null,
            ),
        )

        if (dryRun) {
            Log.i(TAG, "dry-run send id=$id sim=${sender.slot} skipped SmsManager")
            report(context, id, "sent", "dry_run", sender)
            report(context, id, "delivered", "dry_run", sender)
            return
        }

        try {
            send(context, id, sender, to, text)
        } catch (e: SecurityException) {
            report(context, id, "failed", "missing_send_sms_permission", sender)
        } catch (e: Exception) {
            report(context, id, "failed", e.javaClass.simpleName, sender)
        }
    }

    @Suppress("DEPRECATION")
    private fun send(context: Context, id: String, sender: SimSlot, to: String, text: String) {
        val sm = SmsManager.getSmsManagerForSubscriptionId(sender.subscriptionId)
        val parts = sm.divideMessage(text)
        val count = parts.size
        if (count <= 1) {
            sm.sendTextMessage(
                to,
                null,
                text,
                pending(context, ACTION_SENT, id, 1, 0),
                pending(context, ACTION_DELIVERED, id, 1, 0),
            )
        } else {
            val sent = arrayListOf<PendingIntent>()
            val delivered = arrayListOf<PendingIntent>()
            for (i in 0 until count) {
                sent.add(pending(context, ACTION_SENT, id, count, i))
                delivered.add(pending(context, ACTION_DELIVERED, id, count, i))
            }
            sm.sendMultipartTextMessage(to, null, parts, sent, delivered)
        }
        Log.i(TAG, "submitted SMS id=$id parts=$count slot=${sender.slot}")
    }

    private fun pending(context: Context, action: String, id: String, parts: Int, index: Int): PendingIntent {
        val intent = Intent(context, if (action == ACTION_SENT) SmsSentReceiver::class.java else SmsDeliveredReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_COMMAND_ID, id)
            .putExtra(EXTRA_PARTS, parts)
            .putExtra(EXTRA_PART_INDEX, index)
        val requestCode = (id + action + index).hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun report(context: Context, id: String, status: String, error: String?, sender: SimSlot?) {
        RelayClient.reportSendResult(
            context = context,
            endpoint = Preferences(context).endpoint,
            token = Preferences(context).token,
            id = id,
            status = status,
            error = error,
            subscriptionId = sender?.subscriptionId,
            slot = sender?.slot,
            carrier = sender?.carrier,
        )
        Preferences(context).updateSend(id, status, error)
    }

    private fun label(sim: JSONObject, sender: SimSlot): String {
        val label = sim.optString("label")
        return when {
            label.isNotBlank() -> "$label / slot ${sender.slot}"
            else -> sender.carrier ?: "slot ${sender.slot}"
        }
    }

    private const val TAG = "SmsRelay"
    private val handledIds = ConcurrentHashMap.newKeySet<String>()
}

class SmsSentReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(SendSms.EXTRA_COMMAND_ID) ?: return
        val parts = intent.getIntExtra(SendSms.EXTRA_PARTS, 1)
        val index = intent.getIntExtra(SendSms.EXTRA_PART_INDEX, 0)
        val ok = resultCode == Activity.RESULT_OK
        SendTracker.onPart(context, id, "sent", index, parts, ok, resultCode)
    }
}

class SmsDeliveredReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(SendSms.EXTRA_COMMAND_ID) ?: return
        val parts = intent.getIntExtra(SendSms.EXTRA_PARTS, 1)
        val index = intent.getIntExtra(SendSms.EXTRA_PART_INDEX, 0)
        val ok = resultCode == Activity.RESULT_OK
        SendTracker.onPart(context, id, "delivered", index, parts, ok, resultCode)
    }
}

object SendTracker {
    private val sentOk = mutableMapOf<String, Int>()
    private val deliveredOk = mutableMapOf<String, Int>()
    private val failed = mutableSetOf<String>()

    @Synchronized
    fun onPart(context: Context, id: String, kind: String, index: Int, parts: Int, ok: Boolean, resultCode: Int) {
        if (!ok) {
            if (failed.add(id)) {
                SendSms.report(context, id, "failed", "${kind}_failed:$resultCode", null)
            }
            return
        }
        val map = if (kind == "sent") sentOk else deliveredOk
        val count = (map[id] ?: 0) + 1
        map[id] = count
        if (count >= parts && !failed.contains(id)) {
            SendSms.report(context, id, kind, null, null)
        }
    }
}
