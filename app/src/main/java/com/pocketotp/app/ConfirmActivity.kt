package com.pocketotp.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.pocketotp.app.ui.ConfirmScreen
import com.pocketotp.app.ui.PocketOtpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfirmActivity : ComponentActivity() {
    private var request by mutableStateOf<ConfirmRequest?>(null)
    private var busy by mutableStateOf(false)
    private var active by mutableStateOf(false)
    private var openedAt by mutableLongStateOf(0L)
    private var obscured by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bindRequest(intent)
        setContent {
            PocketOtpTheme {
                BackHandler {
                    if (request == null || request?.isExpired() == true) finish()
                    else decide("deny")
                }
                ConfirmScreen(
                    request = request,
                    busy = busy,
                    active = active,
                    obscured = obscured,
                    openedAt = openedAt,
                    onDeny = { decide("deny") },
                    onAllow = { decide("allow") },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning to this screen always resets the accidental-touch guard.
        openedAt = SystemClock.elapsedRealtime()
        active = true
    }

    override fun onPause() {
        active = false // Cancels any in-flight hold when another window takes focus.
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        active = hasFocus && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (hasFocus) openedAt = SystemClock.elapsedRealtime()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // A touch that passed through another app's overlay can still deny, never allow.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            obscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindRequest(intent)
    }

    private fun bindRequest(intent: Intent?) {
        request = ConfirmManager.requestFromIntent(intent)
        openedAt = SystemClock.elapsedRealtime()
    }

    private fun decide(decision: String) {
        val req = request ?: return
        if (busy || req.isExpired()) return
        if (decision == "allow" && (!active || obscured || !ConfirmUiPolicy.canAllow(
                req.expiresAtMs, System.currentTimeMillis(),
                SystemClock.elapsedRealtime() - openedAt, busy,
            ))) return
        busy = true
        lifecycleScope.launch {
            // Existing confirmation client/protocol, deliberately unchanged.
            val result = withContext(Dispatchers.IO) {
                ConfirmManager.submitDecision(applicationContext, req.id, decision)
            }
            busy = false
            val keepOpen = !result.ok && result.error != "expired" &&
                result.error != "not_awaiting_confirmation"
            // A different pending request may have arrived during the network operation.
            if (!keepOpen && request?.id == req.id) finish()
        }
    }
}
