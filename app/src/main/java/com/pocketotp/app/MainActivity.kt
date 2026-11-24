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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pocketotp.app.ui.PairingUiState
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pocketotp.app.ui.AccountUi
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
    private var showOnboarding by mutableStateOf(false)
    private var onboardingStep by mutableStateOf(0)
    private var pairing by mutableStateOf(PairingUiState())
    private var samsungGuide by mutableStateOf(false)
    /** Debug builds only: preview account banners without touching stored state. */
    private var debugAccount: AccountUi? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { pairFromText(it) }
    }

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
        debugAccount = debugAccountFrom(intent)
        applyConfigFromIntent()
        requestNotificationPermissionIfNeeded()
        syncRelayService()
        refresh(reloadSims = true)
        val initialTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        val state0 = uiState
        showOnboarding = intent?.getBooleanExtra(EXTRA_ONBOARDING, false) == true ||
            (!prefs.onboardingDone && state0 != null && (!state0.configured || !SetupChecks.requiredOk(state0.checks)))
        onboardingStep = intent?.getIntExtra(EXTRA_ONBOARDING_STEP, 0) ?: 0
        if (intent?.getBooleanExtra(EXTRA_REPAIR, false) == true) openRepair()
        handlePairingIntent(intent)
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
            fixCheck = { fixCheck(it) },
            scanQr = { launchScanner() },
            pairFromText = { pairFromText(it) },
            openOnboarding = { onboardingStep = 1; showOnboarding = true },
            openRepair = { openRepair() },
        )
        setContent {
            PocketOtpTheme {
                uiState?.let {
                    MainApp(
                        state = it,
                        actions = actions,
                        initialTab = initialTab,
                        showOnboarding = showOnboarding,
                        onboardingStep = onboardingStep,
                        onFinishOnboarding = {
                            prefs.onboardingDone = true
                            showOnboarding = false
                        },
                        pairing = pairing,
                        onPairConfirm = { claimPairing() },
                        onPairDismiss = { pairing = PairingUiState() },
                    )
                }
                if (samsungGuide) {
                    AlertDialog(
                        onDismissRequest = { samsungGuide = false },
                        title = { Text(stringResource(R.string.chk_samsung)) },
                        text = { Text(stringResource(R.string.samsung_guide_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                samsungGuide = false
                                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                            }) { Text(stringResource(R.string.samsung_guide_open)) }
                        },
                        dismissButton = { TextButton(onClick = { samsungGuide = false }) { Text(stringResource(R.string.pair_cancel)) } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REPAIR, false)) openRepair()
        if (intent.getBooleanExtra(EXTRA_ONBOARDING, false)) {
            onboardingStep = intent.getIntExtra(EXTRA_ONBOARDING_STEP, 0)
            showOnboarding = true
        }
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
            account = debugAccount ?: AccountUi(
                revoked = prefs.revoked && prefs.token.isBlank(),
                suspended = prefs.planSuspended,
                plan = prefs.plan.ifBlank { null },
                allowedSlots = RelayState.decodeSlots(prefs.allowedSlots),
                issue = RelayState.Issue.fromKey(prefs.relayIssue),
                issueSlot = prefs.relayIssueSlot,
            ),
            nowMs = System.currentTimeMillis(),
            checks = SetupChecks.build(
                SetupChecks.Inputs(
                    smsGranted = granted(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS),
                    phoneGranted = granted(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS),
                    notificationsGranted = (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) &&
                        NotificationManagerCompat.from(this).areNotificationsEnabled(),
                    fullScreenAllowed = if (Build.VERSION.SDK_INT >= 34) nm?.canUseFullScreenIntent() == true else true,
                    batteryUnrestricted = power?.isIgnoringBatteryOptimizations(packageName) == true,
                    canDrawOverlays = Settings.canDrawOverlays(this),
                    isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
                ),
            ),
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
                RelayEvents.onNewConnection(this)
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
        if (trimmed != prefs.endpoint || newToken.isNotBlank()) RelayEvents.onNewConnection(this)
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

    private fun granted(vararg perms: String): Boolean =
        perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requestNeededPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        // After a "don't ask again" denial the system dialog no longer appears; send the user to app settings.
        val blocked = prefs.permissionsAsked && missing.none { shouldShowRequestPermissionRationale(it) }
        if (blocked) {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            return
        }
        prefs.permissionsAsked = true
        permissionLauncher.launch(missing)
    }

    private fun fixCheck(id: SetupChecks.Id) {
        when (id) {
            SetupChecks.Id.SMS, SetupChecks.Id.PHONE, SetupChecks.Id.NOTIFICATIONS -> {
                if (missingPermissions().isEmpty() && id == SetupChecks.Id.NOTIFICATIONS) {
                    // Permission granted but notifications switched off in system settings.
                    openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) }
                } else {
                    requestNeededPermissions()
                }
            }
            SetupChecks.Id.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
            }
            SetupChecks.Id.BATTERY -> {
                @Suppress("BatteryLife") // Sideloaded app; a persistent relay connection is the core function.
                val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                try {
                    startActivity(direct)
                } catch (_: Exception) {
                    openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }
            SetupChecks.Id.POPUP -> openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            SetupChecks.Id.SAMSUNG_SLEEP -> samsungGuide = true
        }
    }

    // ---------- QR pairing ----------

    private fun debugAccountFrom(intent: Intent?): AccountUi? {
        if (!BuildConfig.DEBUG) return null
        return when (intent?.getStringExtra(EXTRA_DEBUG_ACCOUNT)) {
            "revoked" -> AccountUi(revoked = true)
            "suspended" -> AccountUi(suspended = true, plan = "free", allowedSlots = emptyList())
            "quota" -> AccountUi(plan = "free", allowedSlots = listOf(1), issue = RelayState.Issue.QUOTA_EXCEEDED)
            "slot" -> AccountUi(plan = "free", allowedSlots = listOf(1), issue = RelayState.Issue.NUMBER_NOT_ON_PLAN, issueSlot = 2)
            else -> null
        }
    }

    /** Jump straight to the "connect" step (after the relay revoked this phone). */
    private fun openRepair() {
        onboardingStep = 2
        showOnboarding = true
    }

    private fun launchScanner() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.pair_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }

    private fun handlePairingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || !Pairing.SCHEME.equals(data.scheme, ignoreCase = true)) return
        // Never claim automatically from a link: the dialog asks first.
        pairFromText(data.toString())
        intent.data = null
    }

    private fun pairFromText(text: String) {
        when (val parsed = Pairing.parse(text)) {
            is Pairing.Parsed.Ok -> pairing = PairingUiState(offer = parsed.offer, replacesExisting = prefs.token.isNotBlank())
            is Pairing.Parsed.Error -> toast(getString(when (parsed.reason) {
                Pairing.Reason.NOT_POCKETOTP -> R.string.pair_err_not_pocketotp
                Pairing.Reason.UNSUPPORTED_VERSION -> R.string.pair_err_version
                Pairing.Reason.BAD_RELAY -> R.string.pair_err_relay
                Pairing.Reason.BAD_CODE -> R.string.pair_err_code
                Pairing.Reason.EXPIRED -> R.string.pair_err_expired
            }))
        }
    }

    private fun claimPairing() {
        val offer = pairing.offer ?: return
        if (pairing.busy) return
        pairing = pairing.copy(busy = true, error = null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { Pairing.claim(offer) }
            when (result) {
                is Pairing.ClaimResult.Ok -> {
                    RelayEvents.onNewConnection(this@MainActivity)
                    prefs.endpoint = result.claimed.relay
                    prefs.token = result.claimed.token
                    prefs.deviceId = result.claimed.deviceId.orEmpty()
                    prefs.enabled = true
                    pairing = PairingUiState()
                    syncRelayService()
                    refresh()
                    toast(getString(R.string.pair_ok, offer.relayHost))
                }
                is Pairing.ClaimResult.Failed -> pairing = pairing.copy(
                    busy = false,
                    error = getString(when (RelayState.claimErrorOf(result.error)) {
                        RelayState.ClaimError.CODE -> if (result.error == "code_expired") R.string.pair_claim_err_expired else R.string.pair_claim_err_code
                        RelayState.ClaimError.DEVICE_LIMIT -> R.string.pair_claim_err_device_limit
                        RelayState.ClaimError.RATE -> R.string.pair_claim_err_rate
                        RelayState.ClaimError.NETWORK -> R.string.pair_claim_err_network
                        RelayState.ClaimError.OTHER -> R.string.pair_claim_err_other
                    }) + " (" + result.error + ")",
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun openSettings(action: String, data: Uri? = null, extras: Intent.() -> Unit = {}) {
        try {
            startActivity(Intent(action).apply { if (data != null) setData(data); extras() })
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
        const val EXTRA_ONBOARDING = "onboarding"
        const val EXTRA_ONBOARDING_STEP = "onboarding_step"
        const val EXTRA_REPAIR = "repair"
        const val EXTRA_DEBUG_ACCOUNT = "debug_account"
    }
}
