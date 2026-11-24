package com.pocketotp.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pocketotp.app.ui.MainActions
import com.pocketotp.app.ui.MainApp
import com.pocketotp.app.ui.MainUiState
import com.pocketotp.app.ui.PocketOtpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Preferences
    private var uiState by mutableStateOf<MainUiState?>(null)
    private var sims: List<SimSlot> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh(reloadSims = true) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep codes out of the recent-apps thumbnail.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        prefs = Preferences(this)
        applyConfigFromIntent()
        requestNotificationPermissionIfNeeded()
        syncRelayService()
        refresh(reloadSims = true)
        val initialTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        val actions = MainActions(
            setEnabled = { checked ->
                prefs.enabled = checked
                syncRelayService()
                toast(getString(if (checked) R.string.enabled_on else R.string.enabled_off))
            },
            saveConnection = { endpoint, newToken -> saveConnection(endpoint, newToken) },
            saveBlacklist = { text ->
                prefs.blacklistText = text
                toast(getString(R.string.saved))
            },
            testUpload = { runTestUpload() },
            requestPermissions = { requestNeededPermissions() },
            openBatterySettings = { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            openFullScreenSettings = {
                if (Build.VERSION.SDK_INT >= 34) {
                    openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
                }
            },
            openPendingConfirm = { ConfirmManager.launchPendingIfForeground(this) },
        )
        setContent {
            PocketOtpTheme {
                uiState?.let { MainApp(it, actions, initialTab) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyConfigFromIntent()) {
            syncRelayService()
            refresh()
            toast(getString(R.string.configured_via_adb))
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.register(prefListener)
        refresh(reloadSims = true)
        ConfirmManager.launchPendingIfForeground(this)
    }

    override fun onPause() {
        prefs.unregister(prefListener)
        super.onPause()
    }

    private fun refresh(reloadSims: Boolean = false) {
        if (reloadSims) {
            lifecycleScope.launch {
                sims = withContext(Dispatchers.IO) { runCatching { SimInfo.list(applicationContext) }.getOrDefault(emptyList()) }
                refresh()
            }
        }
        val power = getSystemService(PowerManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        uiState = MainUiState(
            enabled = prefs.enabled,
            configured = prefs.endpoint.isNotBlank() && prefs.token.isNotBlank(),
            connection = prefs.connectionState,
            endpoint = prefs.endpoint,
            tokenSaved = prefs.token.isNotBlank(),
            blacklist = prefs.blacklistText,
            history = prefs.history(),
            sends = prefs.sends(),
            sims = sims,
            missingPermissions = missingPermissions().toList(),
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(packageName) == true,
            fullScreenAllowed = if (Build.VERSION.SDK_INT >= 34) nm?.canUseFullScreenIntent() == true else true,
            pendingConfirms = ConfirmManager.pendingRequests().size,
            versionName = BuildConfig.VERSION_NAME,
            nowMs = System.currentTimeMillis(),
        )
    }

    private fun applyConfigFromIntent(): Boolean {
        if (!BuildConfig.DEBUG) return false
        val intent = intent ?: return false
        var changed = false
        intent.getStringExtra(EXTRA_ENDPOINT)?.let {
            if (it.isNotBlank()) {
                prefs.endpoint = it
                changed = true
            }
        }
        intent.getStringExtra(EXTRA_TOKEN)?.let {
            if (it.isNotBlank()) {
                prefs.token = it
                changed = true
            }
        }
        if (intent.hasExtra(EXTRA_ENABLED)) {
            prefs.enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
            changed = true
        } else if (changed) {
            // A configuration drop from adb should turn forwarding on.
            prefs.enabled = true
        }
        return changed
    }

    private fun saveConnection(endpoint: String, newToken: String) {
        val trimmed = endpoint.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://")) {
            toast(getString(R.string.endpoint_https_required))
            return
        }
        prefs.endpoint = trimmed
        // The saved token is never shown; a blank field keeps it.
        if (newToken.isNotBlank()) prefs.token = newToken
        syncRelayService()
        refresh()
        toast(getString(R.string.saved))
    }

    private fun runTestUpload() {
        if (prefs.endpoint.isBlank() || prefs.token.isBlank()) {
            toast(getString(R.string.need_endpoint_token))
            return
        }
        val body = "【测试】您的验证码是123456，5分钟内有效。"
        val extracted = CodeExtractor.extract(body)
        RelayClient.enqueueUpload(
            context = this,
            endpoint = prefs.endpoint,
            token = prefs.token,
            payload = RelayClient.Payload(
                code = extracted.code ?: "123456",
                sender = "PocketOTP-Test",
                brand = extracted.brand ?: "测试",
                source = "测试 / PocketOTP-Test",
                body = body,
                timestamp = System.currentTimeMillis(),
                subscriptionId = null,
                slot = null,
                carrier = null,
                test = true,
            ),
        )
        toast(getString(R.string.test_queued))
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.toTypedArray()
    }

    private fun missingPermissions(): Array<String> =
        neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    private fun requestNeededPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        permissionLauncher.launch(missing)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun openSettings(action: String, data: Uri? = null) {
        try {
            startActivity(Intent(action).apply { if (data != null) setData(data) })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun syncRelayService() {
        if (RelayStart.shouldRun(this)) {
            RelayStart.tryStart(this)
        } else {
            stopService(Intent(this, RelayService::class.java))
        }
    }

    companion object {
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_TAB = "tab"
    }
}
