package com.pocketotp.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketotp.app.ConfirmManager
import com.pocketotp.app.ConfirmRequest
import com.pocketotp.app.ConfirmUiPolicy
import com.pocketotp.app.R
import com.pocketotp.app.SimInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_WINDOW_MS = 5 * 60_000L
private const val URGENT_SECONDS = 30L

@Composable
fun ConfirmScreen(
    request: ConfirmRequest?,
    busy: Boolean,
    active: Boolean,
    obscured: Boolean,
    openedAt: Long,
    onDeny: () -> Unit,
    onAllow: () -> Unit,
    onClose: () -> Unit,
    nowMs: Long? = null,
) {
    var now by remember { mutableLongStateOf(nowMs ?: System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(request?.id, nowMs) {
        if (nowMs != null) {
            now = nowMs
            return@LaunchedEffect
        }
        while (true) {
            now = System.currentTimeMillis()
            elapsed = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val deadline = request?.expiresAtMs
    val expired = request != null && deadline != null && now >= deadline
    val canAllow = request != null && active && !obscured &&
        ConfirmUiPolicy.canAllow(deadline, now, elapsed - openedAt, busy)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    request == null -> StatusMessage(stringResource(R.string.confirm_missing))
                    expired -> {
                        StatusMessage(stringResource(R.string.confirm_expired))
                        Text(stringResource(R.string.confirm_expired_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> RequestDetails(request, now)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (request == null || expired) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.confirm_close), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Button(
                            onClick = onDeny,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(R.string.confirm_deny_primary), style = MaterialTheme.typography.titleMedium)
                        }
                        HoldToAllowButton(
                            enabled = canAllow,
                            label = stringResource(if (request.dryRun) R.string.confirm_hold_allow_dry else R.string.confirm_hold_allow),
                            onConfirmed = onAllow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp))
}

@Composable
private fun RequestDetails(req: ConfirmRequest, now: Long) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (req.dryRun) R.string.confirm_title_dry else R.string.confirm_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.confirm_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        CountdownRing(req, now)
    }
    if (req.dryRun) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text(
                stringResource(R.string.confirm_detail_dry),
                Modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    DetailCard {
        Label(stringResource(R.string.confirm_label_to))
        Text(
            ConfirmManager.displayNumber(req),
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val fullNumber = req.to.takeIf { it.isNotBlank() && it != "other_sim" && it != req.toMasked }
        when {
            req.to == "other_sim" -> Hint(stringResource(R.string.confirm_to_other_sim))
            fullNumber != null -> Hint(stringResource(R.string.confirm_to_full, fullNumber))
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Label(stringResource(R.string.confirm_label_sim))
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                simDescription(context, req.simLabel),
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
    DetailCard {
        Label(stringResource(R.string.confirm_label_text, req.text.length, runCatching { android.telephony.SmsMessage.calculateLength(req.text, false)[0] }.getOrDefault(1)))
        SelectionContainer {
            Text(
                req.text.ifBlank { "—" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    Text(stringResource(R.string.confirm_footer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

private fun simDescription(context: android.content.Context, label: String): String {
    val slot = label.trim().toIntOrNull()
    val info = slot?.let { s -> runCatching { SimInfo.list(context).firstOrNull { it.slot == s } }.getOrNull() }
    val base = if (slot != null) context.getString(R.string.confirm_sim_slot, slot) else label.ifBlank { "—" }
    val extra = listOfNotNull(info?.carrier?.takeIf { it.isNotBlank() }, info?.numberMasked?.takeIf { it.isNotBlank() })
    return if (extra.isEmpty()) base else base + " · " + extra.joinToString(" ")
}

@Composable
private fun CountdownRing(req: ConfirmRequest, now: Long) {
    val deadline = req.expiresAtMs ?: return
    val created = ConfirmRequest.parseIsoMillis(req.createdAt)
    val total = (if (created != null && deadline > created) deadline - created else DEFAULT_WINDOW_MS).coerceAtLeast(1L)
    val remainingMs = (deadline - now).coerceAtLeast(0L)
    val seconds = ConfirmUiPolicy.remainingSeconds(deadline, now)
    val urgent = (seconds ?: 0L) <= URGENT_SECONDS
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.confirm_countdown_desc, ConfirmUiPolicy.clock(seconds))
    Box(Modifier.size(68.dp).semantics { stateDescription = description }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { (remainingMs.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 5.dp,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        Text(ConfirmUiPolicy.clock(seconds), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HoldToAllowButton(enabled: Boolean, label: String, onConfirmed: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var accessibleHold by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val confirm by rememberUpdatedState(onConfirmed)
    LaunchedEffect(enabled) {
        if (!enabled) {
            accessibleHold?.cancel()
            progress.snapTo(0f)
        }
    }
    val shape = RoundedCornerShape(28.dp)
    val tint = MaterialTheme.colorScheme.error
    val ink = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val holding = progress.value > 0f
    val holdingLabel = stringResource(R.string.confirm_hold_continue)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .border(1.5.dp, ink, shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
                onLongClick(label) {
                    if (enabled && accessibleHold?.isActive != true) {
                        accessibleHold = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(ConfirmUiPolicy.HOLD_MS.toInt(), easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirm()
                        }
                    }
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    this@pointerInput.awaitEachGesture {
                        awaitFirstDown().consume()
                        val hold = launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(ConfirmUiPolicy.HOLD_MS.toInt(), easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirm()
                        }
                        waitForUpOrCancellation()?.consume()
                        if (hold.isActive) {
                            hold.cancel()
                            launch { progress.animateTo(0f, tween(150)) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(tint.copy(alpha = 0.18f)),
        )
        Text(if (holding) holdingLabel else label, color = ink, style = MaterialTheme.typography.titleMedium)
    }
}
