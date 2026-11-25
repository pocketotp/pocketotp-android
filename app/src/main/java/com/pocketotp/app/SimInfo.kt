package com.pocketotp.app

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import org.json.JSONArray
import org.json.JSONObject

data class SimSlot(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val slot: Int,
    val carrier: String?,
    val mccmnc: String?,
    val number: String?,
    val numberMasked: String?,
    val ready: Boolean,
) {
    fun toPublicJson(): JSONObject = JSONObject()
        .put("subscriptionId", subscriptionId)
        .put("simSlotIndex", simSlotIndex)
        .put("slot", slot)
        .put("carrier", carrier)
        .put("mccmnc", mccmnc)
        .put("hasNumber", !number.isNullOrBlank())
        .put("numberMasked", numberMasked)
        .put("ready", ready)
}

object SimInfo {
    /** 1-based slot as required by the relay. Android's simSlotIndex is 0-based. */
    fun uploadSlot(simSlotIndex: Int): Int = simSlotIndex + 1

    fun findBySubscriptionId(context: Context, subId: Int?): SimSlot? {
        if (subId == null) return null
        return list(context).firstOrNull { it.subscriptionId == subId }
    }

    fun list(context: Context): List<SimSlot> {
        return try {
            val mgr = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            val infos = mgr.activeSubscriptionInfoList ?: return emptyList()
            infos.map { info ->
                val mcc = mccOf(info)
                val mnc = mncOf(info)
                val mccmnc = if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) "$mcc$mnc" else null
                val number = try {
                    info.number?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: SecurityException) {
                    null
                }
                SimSlot(
                    subscriptionId = info.subscriptionId,
                    simSlotIndex = info.simSlotIndex,
                    slot = uploadSlot(info.simSlotIndex),
                    carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.displayName?.toString(),
                    mccmnc = mccmnc,
                    number = number,
                    numberMasked = maskNumber(number),
                    ready = true,
                )
            }.sortedBy { it.simSlotIndex }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resolve(context: Context, sim: org.json.JSONObject): SimSlot? {
        val slots = list(context)
        if (slots.isEmpty()) return null
        val kind = sim.optString("kind")
        if (kind == "carrier") {
            val mccmnc = sim.optString("mccmnc")
            return slots.firstOrNull { it.mccmnc == mccmnc }
        }
        if (kind == "slot") {
            val index = if (sim.has("simSlotIndex")) sim.optInt("simSlotIndex") else sim.optInt("slot") - 1
            return slots.firstOrNull { it.simSlotIndex == index }
        }
        return null
    }

    fun otherNumber(context: Context, sender: SimSlot): String? {
        return list(context).firstOrNull { it.subscriptionId != sender.subscriptionId }?.number
    }

    fun toPublicArray(slots: List<SimSlot>): JSONArray {
        val arr = JSONArray()
        slots.forEach { arr.put(it.toPublicJson()) }
        return arr
    }

    fun maskNumber(number: String?): String? {
        val digits = number?.filter { it.isDigit() || it == '+' } ?: return null
        if (digits.length < 4) return null
        return "****${digits.takeLast(4)}"
    }

    @Suppress("DEPRECATION")
    private fun mccOf(info: android.telephony.SubscriptionInfo): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            info.mccString?.let { return it }
        }
        return info.mcc.takeIf { it > 0 }?.toString()
    }

    @Suppress("DEPRECATION")
    private fun mncOf(info: android.telephony.SubscriptionInfo): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            info.mncString?.let { return it }
        }
        return info.mnc.takeIf { it >= 0 }?.toString()?.padStart(2, '0')
    }
}
