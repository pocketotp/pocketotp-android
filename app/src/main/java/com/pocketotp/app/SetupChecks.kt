package com.pocketotp.app

/** Onboarding checklist model (pure; unit-tested). */
object SetupChecks {
    enum class Id { SMS, PHONE, NOTIFICATIONS, FULL_SCREEN, BATTERY, POPUP, SAMSUNG_SLEEP }
    enum class Status { OK, MISSING, MANUAL }

    data class Check(val id: Id, val status: Status, val required: Boolean)

    data class Inputs(
        val smsGranted: Boolean,
        val phoneGranted: Boolean,
        val notificationsGranted: Boolean,
        val fullScreenAllowed: Boolean,
        val batteryUnrestricted: Boolean,
        val canDrawOverlays: Boolean,
        val isSamsung: Boolean,
    )

    fun build(i: Inputs): List<Check> = buildList {
        add(Check(Id.SMS, i.smsGranted.status(), required = true))
        add(Check(Id.PHONE, i.phoneGranted.status(), required = true))
        add(Check(Id.NOTIFICATIONS, i.notificationsGranted.status(), required = true))
        add(Check(Id.FULL_SCREEN, i.fullScreenAllowed.status(), required = false))
        add(Check(Id.BATTERY, i.batteryUnrestricted.status(), required = false))
        add(Check(Id.POPUP, i.canDrawOverlays.status(), required = false))
        // One UI's "never sleeping apps" list has no public API to read, so it stays a manual step.
        if (i.isSamsung) add(Check(Id.SAMSUNG_SLEEP, Status.MANUAL, required = false))
    }

    fun requiredOk(checks: List<Check>): Boolean = checks.none { it.required && it.status == Status.MISSING }

    fun doneCount(checks: List<Check>): Int = checks.count { it.status == Status.OK }

    fun autoCount(checks: List<Check>): Int = checks.count { it.status != Status.MANUAL }

    private fun Boolean.status() = if (this) Status.OK else Status.MISSING
}
