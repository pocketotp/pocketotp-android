package com.pocketotp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketotp.app.Pairing
import com.pocketotp.app.R
import com.pocketotp.app.SetupChecks

data class PairingUiState(
    val offer: Pairing.Offer? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val replacesExisting: Boolean = false,
)

private const val STEPS = 4

@Composable
fun OnboardingScreen(
    state: MainUiState,
    actions: MainActions,
    initialStep: Int,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0, STEPS - 1)) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onb_step, step + 1, STEPS),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onFinish) { Text(stringResource(if (state.configured) R.string.onb_close else R.string.onb_skip)) }
            }
            LinearProgressIndicator(
                progress = { (step + 1f) / STEPS },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> ChecklistStep(state, actions)
                    2 -> ConnectStep(state, actions)
                    else -> DoneStep(state, actions)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val primaryLabel = when (step) {
                    0 -> stringResource(R.string.onb_start)
                    1 -> stringResource(if (SetupChecks.requiredOk(state.checks)) R.string.onb_next else R.string.onb_next_anyway)
                    2 -> stringResource(if (state.configured) R.string.onb_next else R.string.onb_connect_later)
                    else -> stringResource(R.string.onb_finish)
                }
                Button(
                    onClick = { if (step < STEPS - 1) step++ else onFinish() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(primaryLabel, style = MaterialTheme.typography.titleMedium) }
                if (step > 0) {
                    TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.onb_back))
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.onb_welcome_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(
        R.string.onb_point_codes to R.string.onb_point_codes_detail,
        R.string.onb_point_bank to R.string.onb_point_bank_detail,
        R.string.onb_point_confirm to R.string.onb_point_confirm_detail,
    ).forEach { (t, d) ->
        StepCard {
            Text(stringResource(t), fontWeight = FontWeight.SemiBold)
            Text(stringResource(d), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChecklistStep(state: MainUiState, actions: MainActions) {
    Text(stringResource(R.string.onb_checks_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.onb_checks_progress, SetupChecks.doneCount(state.checks), SetupChecks.autoCount(state.checks)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StepCard(padded = false) {
        state.checks.forEachIndexed { i, check ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ChecklistRow(check, onFix = { actions.fixCheck(check.id) })
        }
    }
    Text(stringResource(R.string.onb_checks_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ChecklistRow(check: SetupChecks.Check, onFix: () -> Unit) {
    val (title, detail) = checkText(check.id)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val ok = check.status == SetupChecks.Status.OK
        val manual = check.status == SetupChecks.Status.MANUAL
        Box(
            Modifier.size(24.dp).background(
                when {
                    ok -> MaterialTheme.colorScheme.primary
                    manual -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                ok -> Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                manual -> Text("!", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), fontWeight = FontWeight.Medium)
                if (!check.required) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.onb_recommended), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) {
            TextButton(onClick = onFix) { Text(stringResource(if (manual) R.string.onb_how else R.string.action_enable)) }
        }
    }
}

private fun checkText(id: SetupChecks.Id): Pair<Int, Int> = when (id) {
    SetupChecks.Id.SMS -> R.string.chk_sms to R.string.chk_sms_detail
    SetupChecks.Id.PHONE -> R.string.chk_phone to R.string.chk_phone_detail
    SetupChecks.Id.NOTIFICATIONS -> R.string.chk_notif to R.string.chk_notif_detail
    SetupChecks.Id.FULL_SCREEN -> R.string.check_fullscreen to R.string.check_fullscreen_detail
    SetupChecks.Id.BATTERY -> R.string.chk_battery to R.string.chk_battery_detail
    SetupChecks.Id.POPUP -> R.string.chk_popup to R.string.chk_popup_detail
    SetupChecks.Id.SAMSUNG_SLEEP -> R.string.chk_samsung to R.string.chk_samsung_detail
}

@Composable
private fun ConnectStep(state: MainUiState, actions: MainActions) {
    Text(stringResource(R.string.onb_connect_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (state.configured) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.onb_connected), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(state.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
    Text(stringResource(R.string.onb_connect_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = actions.scanQr, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pair_scan), style = MaterialTheme.typography.titleMedium)
    }
    PasteLinkCard(actions)
    ManualEntryCard(state, actions)
}

@Composable
private fun PasteLinkCard(actions: MainActions) {
    var link by remember { mutableStateOf("") }
    StepCard {
        Text(stringResource(R.string.pair_paste_title), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            placeholder = { Text("pocketotp://pair?…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(onClick = { actions.pairFromText(link) }, enabled = link.isNotBlank()) {
            Text(stringResource(R.string.pair_paste_go))
        }
    }
}

@Composable
private fun ManualEntryCard(state: MainUiState, actions: MainActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    var endpoint by rememberSaveable(state.endpoint) { mutableStateOf(state.endpoint) }
    var token by remember { mutableStateOf("") }
    StepCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pair_manual_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pair_manual_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open = !open }) { Text(stringResource(if (open) R.string.pair_manual_hide else R.string.pair_manual_show)) }
        }
        if (open) {
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = endpoint, onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.endpoint_hint)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text(stringResource(R.string.token_hint)) },
                placeholder = { Text(stringResource(if (state.tokenSaved) R.string.token_saved_placeholder else R.string.token_empty_placeholder)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = { actions.saveConnection(endpoint, token); token = "" }) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun DoneStep(state: MainUiState, actions: MainActions) {
    Text(stringResource(R.string.onb_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(stringResource(if (state.configured) R.string.onb_done_body else R.string.onb_done_body_unconfigured), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.configured) {
        FilledTonalButton(onClick = actions.testUpload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.onb_test))
        }
        Text(stringResource(R.string.onb_test_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepCard(padded: Boolean = true, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(if (padded) Modifier.padding(14.dp) else Modifier) { content() }
    }
}

// ---------- Pairing confirmation ----------

@Composable
fun PairingDialog(state: PairingUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val offer = state.offer ?: return
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.pair_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pair_confirm_relay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(offer.relayHost, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(stringResource(R.string.pair_confirm_code), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(offer.displayCode, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                Text(stringResource(R.string.pair_confirm_body), style = MaterialTheme.typography.bodySmall)
                if (state.replacesExisting) {
                    Text(stringResource(R.string.pair_confirm_replace), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.pair_claiming))
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !state.busy) {
                Text(stringResource(if (state.error != null) R.string.pair_retry else R.string.pair_connect))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.pair_cancel)) } },
    )
}
