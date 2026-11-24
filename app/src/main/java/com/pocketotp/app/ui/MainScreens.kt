package com.pocketotp.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketotp.app.HistoryFormat
import com.pocketotp.app.HistoryItem
import com.pocketotp.app.R
import com.pocketotp.app.SendItem
import com.pocketotp.app.SimSlot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val enabled: Boolean,
    val configured: Boolean,
    val connection: String,
    val endpoint: String,
    val tokenSaved: Boolean,
    val blacklist: String,
    val history: List<HistoryItem>,
    val sends: List<SendItem>,
    val sims: List<SimSlot>,
    val missingPermissions: List<String>,
    val batteryUnrestricted: Boolean,
    val fullScreenAllowed: Boolean,
    val pendingConfirms: Int,
    val versionName: String,
    val nowMs: Long,
    val checks: List<com.pocketotp.app.SetupChecks.Check> = emptyList(),
    val account: AccountUi = AccountUi(),
)

/** Hosted-relay account state. All defaults = old single-user relay (nothing to show). */
data class AccountUi(
    val revoked: Boolean = false,
    val suspended: Boolean = false,
    val plan: String? = null,
    val allowedSlots: List<Int>? = null,
    val issue: com.pocketotp.app.RelayState.Issue? = null,
    val issueSlot: Int = 0,
) {
    fun slotOnPlan(slot: Int): Boolean? = allowedSlots?.let { slot in it }
}

class MainActions(
    val setEnabled: (Boolean) -> Unit,
    val saveConnection: (endpoint: String, newToken: String) -> Unit,
    val saveBlacklist: (String) -> Unit,
    val testUpload: () -> Unit,
    val requestPermissions: () -> Unit,
    val openBatterySettings: () -> Unit,
    val openFullScreenSettings: () -> Unit,
    val openPendingConfirm: () -> Unit,
    val fixCheck: (com.pocketotp.app.SetupChecks.Id) -> Unit = {},
    val scanQr: () -> Unit = {},
    val pairFromText: (String) -> Unit = {},
    val openOnboarding: () -> Unit = {},
    val openRepair: () -> Unit = {},
)

private enum class Tab(val label: Int) { HOME(R.string.tab_home), HISTORY(R.string.tab_history), SETTINGS(R.string.tab_settings) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    state: MainUiState,
    actions: MainActions,
    initialTab: Int = 0,
    showOnboarding: Boolean = false,
    onboardingStep: Int = 0,
    onFinishOnboarding: () -> Unit = {},
    pairing: PairingUiState = PairingUiState(),
    onPairConfirm: () -> Unit = {},
    onPairDismiss: () -> Unit = {},
) {
    PairingDialog(pairing, onPairConfirm, onPairDismiss)
    if (showOnboarding) {
        OnboardingScreen(state, actions, onboardingStep, onFinishOnboarding)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, Tab.entries.size - 1)) }
    val current = Tab.entries[tab]
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (current == Tab.HOME) stringResource(R.string.app_name) else stringResource(current.label),
                    fontWeight = FontWeight.SemiBold,
                )
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.HISTORY -> Icons.AutoMirrored.Filled.List
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        when (current) {
            Tab.HOME -> HomeScreen(state, actions, padding, onOpenHistory = { tab = Tab.HISTORY.ordinal }, onOpenSettings = { tab = Tab.SETTINGS.ordinal })
            Tab.HISTORY -> HistoryScreen(state, padding)
            Tab.SETTINGS -> SettingsScreen(state, actions, padding)
        }
    }
}

// ---------- Home ----------

@Composable
private fun HomeScreen(
    state: MainUiState,
    actions: MainActions,
    padding: PaddingValues,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountBanners(state.account, actions)
        if (state.pendingConfirms > 0) {
            Banner(
                text = stringResource(R.string.home_pending_confirm, state.pendingConfirms),
                action = stringResource(R.string.home_pending_open),
                onAction = actions.openPendingConfirm,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (!com.pocketotp.app.SetupChecks.requiredOk(state.checks)) {
            Banner(
                text = stringResource(R.string.home_missing_perms),
                action = stringResource(R.string.home_finish_setup),
                onAction = actions.openOnboarding,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        StatusCard(state, actions, onOpenSettings)
        if (state.sims.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.sims.sortedBy { it.slot }.forEach { sim ->
                    OutlinedCardBox(Modifier.weight(1f)) {
                        Text(stringResource(R.string.confirm_sim_slot, sim.slot), fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(sim.carrier, sim.numberMasked).joinToString(" ").ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.account.slotOnPlan(sim.slot) == false) {
                            Text(
                                stringResource(R.string.sim_not_on_plan),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.history.isNotEmpty()) TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.home_all)) }
        }
        val recent = state.history.take(HistoryFormat.HOME_RECENT)
        if (recent.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedCardBox(Modifier.fillMaxWidth(), padded = false) {
                ForwardRows(recent, showDate = false)
            }
            Text(
                stringResource(R.string.home_reveal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState, actions: MainActions, onOpenSettings: () -> Unit) {
    val ok = Color(0xFF2E9E57)
    val warn = Color(0xFFE0A100)
    val bad = MaterialTheme.colorScheme.error
    val (title, detail, dot) = when {
        !state.configured -> Triple(stringResource(R.string.status_unconfigured), stringResource(R.string.status_unconfigured_detail), bad)
        !state.enabled -> Triple(stringResource(R.string.status_paused), stringResource(R.string.status_paused_detail), warn)
        state.account.suspended -> Triple(stringResource(R.string.status_suspended), stringResource(R.string.status_suspended_detail), bad)
        state.connection == "websocket" -> Triple(stringResource(R.string.status_online), stringResource(R.string.status_detail_ws), ok)
        state.connection == "polling" -> Triple(stringResource(R.string.status_online), stringResource(R.string.status_detail_poll), ok)
        state.connection == "reconnecting" -> Triple(stringResource(R.string.status_reconnecting), stringResource(R.string.status_reconnecting_detail), warn)
        else -> Triple(stringResource(R.string.status_offline), stringResource(R.string.status_offline_detail), bad)
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            state.account.plan?.let { plan ->
                Text(stringResource(R.string.status_plan, planLabel(plan)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.status_today, HistoryFormat.forwardedToday(state.history, state.nowMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            if (state.configured) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.enable_forwarding), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.enable_forwarding_detail), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.enabled, onCheckedChange = actions.setEnabled)
                }
            } else {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
            }
        }
    }
}

private fun planLabel(plan: String): String = when (plan.lowercase()) {
    "free" -> "Free"
    "pro" -> "Pro"
    else -> plan
}

@Composable
private fun AccountBanners(account: AccountUi, actions: MainActions) {
    val errC = MaterialTheme.colorScheme.errorContainer
    val errT = MaterialTheme.colorScheme.onErrorContainer
    val tC = MaterialTheme.colorScheme.tertiaryContainer
    val tT = MaterialTheme.colorScheme.onTertiaryContainer
    if (account.revoked) {
        Banner(stringResource(R.string.banner_revoked), stringResource(R.string.banner_repair), actions.openRepair, errC, errT)
        return
    }
    if (account.suspended) {
        Banner(stringResource(R.string.banner_suspended), null, {}, errC, errT)
    }
    when (account.issue) {
        com.pocketotp.app.RelayState.Issue.QUOTA_EXCEEDED ->
            Banner(stringResource(R.string.banner_quota), null, {}, tC, tT)
        com.pocketotp.app.RelayState.Issue.NUMBER_NOT_ON_PLAN ->
            Banner(
                if (account.issueSlot > 0) stringResource(R.string.banner_not_on_plan_slot, account.issueSlot)
                else stringResource(R.string.banner_not_on_plan),
                null, {}, tC, tT,
            )
        else -> Unit
    }
}

@Composable
private fun Banner(text: String, action: String?, onAction: () -> Unit, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = if (action != null) 8.dp else 16.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f).padding(vertical = if (action != null) 0.dp else 6.dp), style = MaterialTheme.typography.bodyMedium)
            if (action != null) TextButton(onClick = onAction) { Text(action, color = content, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun OutlinedCardBox(modifier: Modifier = Modifier, padded: Boolean = true, content: @Composable () -> Unit) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(if (padded) Modifier.padding(12.dp) else Modifier) { content() }
    }
}

// ---------- Forward rows with masked codes ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForwardRows(items: List<HistoryItem>, showDate: Boolean) {
    var revealed by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(revealed) {
        if (revealed != null) {
            delay(HistoryFormat.REVEAL_MS)
            revealed = null
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    items.forEachIndexed { i, item ->
        val key = "${item.timestamp}:$i"
        val outcome = HistoryFormat.outcome(item)
        val canReveal = HistoryFormat.canReveal(item)
        val isRevealed = revealed == key && canReveal
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = canReveal,
                    onClick = { if (isRevealed) revealed = null },
                    onLongClickLabel = stringResource(R.string.history_reveal_label),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        revealed = key
                    },
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                when (outcome) {
                    HistoryFormat.Outcome.FORWARDED -> Text(
                        if (isRevealed) item.code else HistoryFormat.maskCode(item.code),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = if (isRevealed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    HistoryFormat.Outcome.BLOCKED -> Text(
                        stringResource(R.string.history_blocked),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    HistoryFormat.Outcome.FAILED -> Text(
                        stringResource(R.string.history_failed),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val sub = when (outcome) {
                    HistoryFormat.Outcome.BLOCKED -> HistoryFormat.blockedKeyword(item)
                        ?.let { stringResource(R.string.history_blocked_by, item.source.ifBlank { "—" }, it) }
                        ?: item.source
                    HistoryFormat.Outcome.FAILED -> item.source + " · " + failReason(item.reason)
                    HistoryFormat.Outcome.FORWARDED -> if (isRevealed) stringResource(R.string.history_revealed, item.source) else item.source
                }
                Text(sub.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeFmt.format(Date(item.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun failReason(reason: String?): String = when (reason) {
    "quota_exceeded" -> stringResource(R.string.fail_quota)
    "number_not_on_plan" -> stringResource(R.string.fail_not_on_plan)
    "device_suspended" -> stringResource(R.string.fail_suspended)
    "device_revoked" -> stringResource(R.string.fail_revoked)
    else -> reason.orEmpty()
}

// ---------- History ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(state: MainUiState, padding: PaddingValues) {
    var which by rememberSaveable { mutableIntStateOf(0) }
    val dayFmt = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(R.string.history_tab_forwards, R.string.history_tab_sends).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = which == i,
                        onClick = { which = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(stringResource(label)) }
                }
            }
        }
        if (which == 0) {
            if (state.history.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val groups = HistoryFormat.groupByDay(state.history, { it.timestamp })
                items(groups, key = { "f" + it.first }) { (day, list) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dayFmt.format(Date(day)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedCardBox(Modifier.fillMaxWidth(), padded = false) { ForwardRows(list, showDate = true) }
                    }
                }
                item {
                    Text(stringResource(R.string.history_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (state.sends.isEmpty()) {
                item { Text(stringResource(R.string.sends_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val groups = HistoryFormat.groupByDay(state.sends, { it.timestamp })
                items(groups, key = { "s" + it.first }) { (day, list) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dayFmt.format(Date(day)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedCardBox(Modifier.fillMaxWidth(), padded = false) { SendRows(list) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendRows(items: List<SendItem>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    items.forEachIndexed { i, item ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val st = HistoryFormat.sendState(item.status)
        val (label, color) = when (st) {
            HistoryFormat.SendState.DELIVERED -> stringResource(R.string.send_delivered) to MaterialTheme.colorScheme.primary
            HistoryFormat.SendState.SENT -> stringResource(R.string.send_sent) to MaterialTheme.colorScheme.primary
            HistoryFormat.SendState.QUEUED -> stringResource(R.string.send_queued) to MaterialTheme.colorScheme.onSurface
            HistoryFormat.SendState.WAITING -> stringResource(R.string.send_waiting) to MaterialTheme.colorScheme.tertiary
            HistoryFormat.SendState.FAILED -> stringResource(R.string.send_failed) to MaterialTheme.colorScheme.error
            HistoryFormat.SendState.CANCELLED -> stringResource(R.string.send_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
            HistoryFormat.SendState.EXPIRED -> stringResource(R.string.send_expired) to MaterialTheme.colorScheme.onSurfaceVariant
            HistoryFormat.SendState.UNKNOWN -> item.status to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.toMasked.ifBlank { "—" }, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                val sim = item.simLabel.toIntOrNull()?.let { stringResource(R.string.confirm_sim_slot, it) } ?: item.simLabel
                val extra = if (item.error == "dry_run") stringResource(R.string.send_dry_run) else item.error.takeIf { it != null && it != "denied" }
                Text(listOfNotNull(sim.ifBlank { null }, extra).joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(timeFmt.format(Date(item.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsScreen(state: MainUiState, actions: MainActions, padding: PaddingValues) {
    var endpoint by rememberSaveable(state.endpoint) { mutableStateOf(state.endpoint) }
    var token by remember { mutableStateOf("") }
    var blacklist by rememberSaveable(state.blacklist) { mutableStateOf(state.blacklist) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(stringResource(R.string.settings_connection))
        OutlinedCardBox(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(stringResource(R.string.endpoint_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token_hint)) },
                    placeholder = { Text(stringResource(if (state.tokenSaved) R.string.token_saved_placeholder else R.string.token_empty_placeholder)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.token_supporting)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = actions.scanQr, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pair_scan))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { actions.saveConnection(endpoint, token); token = "" }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.save))
                    }
                    FilledTonalButton(onClick = actions.testUpload, enabled = state.configured, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.test_upload))
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_device))
        OutlinedCardBox(Modifier.fillMaxWidth(), padded = false) {
            state.checks.forEachIndexed { i, check ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChecklistRow(check, onFix = { actions.fixCheck(check.id) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = actions.openOnboarding, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(stringResource(R.string.settings_run_setup))
            }
        }

        SectionLabel(stringResource(R.string.settings_privacy))
        OutlinedCardBox(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = blacklist,
                    onValueChange = { blacklist = it },
                    label = { Text(stringResource(R.string.blacklist_hint)) },
                    minLines = 3,
                    maxLines = 8,
                    supportingText = { Text(stringResource(R.string.blacklist_helper)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { actions.saveBlacklist(blacklist) }, enabled = blacklist != state.blacklist) {
                    Text(stringResource(R.string.settings_save_blacklist))
                }
                Text(stringResource(R.string.settings_masking_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionLabel(stringResource(R.string.settings_about))
        OutlinedCardBox(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_version, state.versionName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.heightIn(min = 8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}
