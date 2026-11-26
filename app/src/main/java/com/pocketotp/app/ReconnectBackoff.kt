package com.pocketotp.app

/**
 * Exponential reconnect delay (1s, 2s, 4s … capped at 30s). [reset] after a socket opened, so a
 * phone that stays connected for hours reconnects within about a second after a drop instead of
 * waiting the 30s cap it reached the last time the network was flaky.
 */
class ReconnectBackoff(private val initialMs: Long = 1_000L, private val maxMs: Long = 30_000L) {
    @Volatile private var current = initialMs

    /** The delay to wait now; the next one doubles (up to the cap). */
    fun next(): Long {
        val d = current
        current = (current * 2).coerceAtMost(maxMs)
        return d
    }

    fun reset() {
        current = initialMs
    }
}
