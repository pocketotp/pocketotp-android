package com.jimmy.smsrelay

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jimmy.smsrelay.databinding.ActivityConfirmBinding

class ConfirmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConfirmBinding
    private var request: ConfirmRequest? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        bindRequest(ConfirmManager.requestFromIntent(intent))
        binding.btnAllow.setOnClickListener { decide("allow") }
        binding.btnDeny.setOnClickListener { decide("deny") }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindRequest(ConfirmManager.requestFromIntent(intent))
    }

    private fun bindRequest(req: ConfirmRequest?) {
        request = req
        if (req == null) {
            binding.txtTitle.setText(R.string.confirm_missing)
            binding.txtBody.text = ""
            binding.btnAllow.isEnabled = false
            binding.btnDeny.isEnabled = false
            return
        }
        if (req.isExpired()) {
            binding.txtTitle.setText(R.string.confirm_expired)
            binding.txtBody.text = details(req)
            binding.btnAllow.isEnabled = false
            binding.btnDeny.isEnabled = false
            return
        }
        binding.txtTitle.setText(if (req.dryRun) R.string.confirm_title_dry else R.string.confirm_title)
        binding.txtBody.text = details(req)
        binding.btnAllow.isEnabled = true
        binding.btnDeny.isEnabled = true
    }

    private fun details(req: ConfirmRequest): String {
        val numberLine = if (req.to.isNotBlank() && req.to != req.toMasked) {
            getString(R.string.confirm_detail_to_full, ConfirmManager.displayNumber(req), req.to)
        } else {
            getString(R.string.confirm_detail_to, ConfirmManager.displayNumber(req))
        }
        return buildString {
            append(numberLine)
            append('\n')
            append(getString(R.string.confirm_detail_sim, req.simLabel.ifBlank { "—" }))
            append('\n')
            append(getString(R.string.confirm_detail_expires, ConfirmManager.formatExpiry(req)))
            if (req.dryRun) {
                append('\n')
                append(getString(R.string.confirm_detail_dry))
            }
            append("\n\n")
            append(req.text.ifBlank { "—" })
        }
    }

    private fun decide(decision: String) {
        val req = request ?: return
        if (busy) return
        if (req.isExpired()) {
            Toast.makeText(this, getString(R.string.confirm_expired), Toast.LENGTH_SHORT).show()
            bindRequest(req)
            return
        }
        busy = true
        binding.btnAllow.isEnabled = false
        binding.btnDeny.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        Thread({
            val result = ConfirmManager.submitDecision(this, req.id, decision)
            runOnUiThread {
                busy = false
                binding.progress.visibility = View.GONE
                val keepOpen = result.error == "rate_limited_hour" || result.error == "rate_limited_day" ||
                    (!result.ok && result.error != "expired" && result.error != "not_awaiting_confirmation")
                if (keepOpen) {
                    binding.btnAllow.isEnabled = true
                    binding.btnDeny.isEnabled = true
                } else {
                    finish()
                }
            }
        }, "sms-relay-confirm-ui").start()
    }
}
