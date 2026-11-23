package com.jimmy.smsrelay

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jimmy.smsrelay.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Preferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshPermissionState() }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Preferences.KEY_HISTORY -> runOnUiThread { renderHistory() }
            Preferences.KEY_SENDS -> runOnUiThread { renderSends() }
            Preferences.KEY_CONNECTION -> runOnUiThread { renderConnection() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        applyConfigFromIntent()
        bindUiFromPrefs()
        refreshPermissionState()
        renderHistory()
        renderSends()
        renderConnection()
        syncRelayService()

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            syncRelayService()
            toast(if (checked) getString(R.string.enabled_on) else getString(R.string.enabled_off))
        }
        binding.btnSave.setOnClickListener { saveForm() }
        binding.btnRequestPerms.setOnClickListener { requestNeededPermissions() }
        binding.btnTest.setOnClickListener { runTestUpload() }
        binding.btnSendSmsStub.setOnClickListener {
            toast(getString(R.string.send_sms_stub_toast))
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyConfigFromIntent()) {
            bindUiFromPrefs()
            syncRelayService()
            toast(getString(R.string.configured_via_adb))
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.register(prefListener)
        refreshPermissionState()
        renderHistory()
        renderSends()
        renderConnection()
    }

    override fun onPause() {
        prefs.unregister(prefListener)
        super.onPause()
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

    private fun bindUiFromPrefs() {
        binding.inputEndpoint.setText(prefs.endpoint)
        binding.inputToken.setText(prefs.token)
        binding.inputBlacklist.setText(prefs.blacklistText)
        binding.switchEnabled.setOnCheckedChangeListener(null)
        binding.switchEnabled.isChecked = prefs.enabled
        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            syncRelayService()
            toast(if (checked) getString(R.string.enabled_on) else getString(R.string.enabled_off))
        }
    }

    private fun saveForm() {
        prefs.endpoint = binding.inputEndpoint.text?.toString().orEmpty()
        prefs.token = binding.inputToken.text?.toString().orEmpty()
        prefs.blacklistText = binding.inputBlacklist.text?.toString().orEmpty()
        prefs.enabled = binding.switchEnabled.isChecked
        syncRelayService()
        toast(getString(R.string.saved))
    }

    private fun runTestUpload() {
        saveForm()
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
                sender = "SMS-Relay-Test",
                brand = extracted.brand ?: "测试",
                source = "测试 / SMS-Relay-Test",
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

    private fun renderHistory() {
        val items = prefs.history()
        if (items.isEmpty()) {
            binding.txtHistory.text = getString(R.string.history_empty)
            return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        binding.txtHistory.text = items.joinToString("\n") { item ->
            val time = fmt.format(Date(item.timestamp))
            val mark = if (item.skipped) "⏭" else "✓"
            val extra = item.reason?.let { " ($it)" } ?: ""
            "$mark $time  ${item.code}  ·  ${item.source}$extra"
        }
    }

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    private fun missingPermissions(): Array<String> =
        neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    private fun refreshPermissionState() {
        val missing = missingPermissions()
        binding.txtPerms.text = if (missing.isEmpty()) {
            getString(R.string.perms_granted)
        } else {
            getString(R.string.perms_missing, missing.joinToString(", ") { it.substringAfterLast('.') })
        }
        binding.btnRequestPerms.visibility =
            if (missing.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestNeededPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        permissionLauncher.launch(missing)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }


    private fun renderSends() {
        val items = prefs.sends()
        if (items.isEmpty()) {
            binding.txtSends.text = getString(R.string.sends_empty)
            return
        }
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        binding.txtSends.text = items.joinToString("\n") { item ->
            val time = fmt.format(java.util.Date(item.timestamp))
            val extra = item.error?.let { " ($it)" } ?: ""
            "${item.status}  $time  ${item.toMasked}  ·  ${item.simLabel}$extra"
        }
    }

    private fun renderConnection() {
        binding.txtConnection.text = when (prefs.connectionState) {
            "websocket" -> getString(R.string.connection_websocket)
            "polling" -> getString(R.string.connection_polling)
            "reconnecting" -> getString(R.string.connection_reconnecting)
            else -> getString(R.string.connection_offline)
        }
    }

    private fun syncRelayService() {
        val shouldRun = prefs.enabled && prefs.endpoint.isNotBlank() && prefs.token.isNotBlank()
        val intent = Intent(this, RelayService::class.java)
        if (shouldRun) {
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(intent)
        }
    }

    companion object {
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ENABLED = "enabled"
    }
}
