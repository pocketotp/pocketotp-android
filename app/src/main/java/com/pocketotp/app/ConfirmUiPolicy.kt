package com.pocketotp.app

/** UI-only policy. The relay remains the authority for expiry and sending. */
object ConfirmUiPolicy {
    const val HOLD_MS = 1_000L
    const val OPEN_GUARD_MS = 1_000L

    fun remainingSeconds(deadlineMs: Long?, nowMs: Long): Long? =
        deadlineMs?.let { ((it - nowMs).coerceAtLeast(0L) + 999L) / 1_000L }

    fun clock(seconds: Long?): String = seconds?.let {
        "%d:%02d".format(java.util.Locale.ROOT, it / 60, it % 60)
    } ?: "—"

    fun canAllow(deadlineMs: Long?, nowMs: Long, visibleForMs: Long, busy: Boolean): Boolean =
        !busy && deadlineMs != null && deadlineMs > nowMs && visibleForMs >= OPEN_GUARD_MS
}
