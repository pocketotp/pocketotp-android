package com.pocketotp.app.screenshot

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocketotp.app.ConfirmRequest
import com.pocketotp.app.HistoryItem
import com.pocketotp.app.R
import com.pocketotp.app.SetupChecks
import com.pocketotp.app.SimSlot
import com.pocketotp.app.ui.ConfirmScreen
import com.pocketotp.app.ui.MainActions
import com.pocketotp.app.ui.MainApp
import com.pocketotp.app.ui.MainUiState
import com.pocketotp.app.ui.PocketOtpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * JVM screenshots of the marketing screens, in English and Chinese,
 * at the same 1080×2640 / xxxhdpi size as the existing Flip6 device shots.
 * Dark theme, to match the current website images.
 *
 * Record:  ./gradlew :app:recordRoborazziDebug
 * Outputs: app/src/test/screenshots/marketing/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MarketingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazzi = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "src/test/screenshots/marketing",
        ),
    )

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-night-xxhdpi")
    fun homeEn() = capture("home-en") { PhoneFrame("16:15") { HomeShot() } }

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-night-xxhdpi")
    fun homeZh() = capture("home-zh") { PhoneFrame("16:15") { HomeShot() } }

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-night-xxhdpi")
    fun confirmEn() = capture("confirm-en") { PhoneFrame("12:20") { ConfirmShot() } }

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-night-xxhdpi")
    fun confirmZh() = capture("confirm-zh") { PhoneFrame("12:20") { ConfirmShot() } }

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-night-xxhdpi")
    fun welcomeEn() = capture("welcome-en") { PhoneFrame("16:15") { OnboardingShot(0) } }

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-night-xxhdpi")
    fun welcomeZh() = capture("welcome-zh") { PhoneFrame("16:15") { OnboardingShot(0) } }

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-night-xxhdpi")
    fun checklistEn() = capture("checklist-en") { PhoneFrame("16:15") { OnboardingShot(1) } }

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-night-xxhdpi")
    fun checklistZh() = capture("checklist-zh") { PhoneFrame("16:15") { OnboardingShot(1) } }

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-night-xxhdpi")
    fun connectEn() = capture("connect-en") { PhoneFrame("16:15") { OnboardingShot(2) } }

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-night-xxhdpi")
    fun connectZh() = capture("connect-zh") { PhoneFrame("16:15") { OnboardingShot(2) } }

    private fun capture(name: String, content: @Composable () -> Unit) {
        TimeZone.setDefault(zone)
        composeRule.setContent {
            PocketOtpTheme(darkTheme = true) { content() }
        }
        composeRule.waitForIdle()
        val out = File("src/test/screenshots/marketing", "$name.png")
        out.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(
            filePath = out.path,
            roborazziOptions = RoborazziOptions(),
        )
    }

    @Composable
    private fun HomeShot() {
        MainApp(state = marketingHomeState(), actions = noopActions(), initialTab = 0)
    }

    @Composable
    private fun ConfirmShot() {
        val now = clock("12:20")
        val deadline = now + 181_000L
        val created = deadline - 5 * 60_000L
        val iso = isoFmt()
        val ctx = LocalContext.current
        val simLabel = ctx.getString(R.string.confirm_sim_slot, 1) + " · 中国联通 ****7689"
        val text = if (ctx.resources.configuration.locales[0].language == "zh") {
            "PocketOTP 2.5.1 确认页测试（试发送，不会真的发出）"
        } else {
            "PocketOTP 2.5.1 confirm-screen test (dry run, will not send)"
        }
        ConfirmScreen(
            request = ConfirmRequest(
                id = "shot",
                to = "other_sim",
                toMasked = "*****3054",
                simLabel = simLabel,
                text = text,
                textLength = text.length,
                dryRun = true,
                createdAt = iso.format(created),
                expiresAt = iso.format(deadline),
                expiresAtMs = deadline,
            ),
            busy = false,
            active = true,
            obscured = false,
            openedAt = SystemClock.elapsedRealtime() - 2_000L,
            onDeny = {},
            onAllow = {},
            onClose = {},
            nowMs = now,
        )
    }

    @Composable
    private fun OnboardingShot(step: Int) {
        MainApp(
            state = marketingHomeState().copy(
                configured = false,
                checks = marketingChecks(),
            ),
            actions = noopActions(),
            showOnboarding = true,
            onboardingStep = step,
        )
    }

    private fun marketingHomeState(): MainUiState {
        val now = clock("16:15")
        val at1558 = clock("15:58")
        val source = "中国银行 / 95566 / 中国移动全球通"
        val blocked = HistoryItem(
            code = "",
            source = source,
            timestamp = at1558,
            skipped = true,
            reason = "blacklist:银行",
        )
        return MainUiState(
            enabled = true,
            configured = true,
            connection = "websocket",
            endpoint = "https://pocketotp-relay-dev.jimmymjing.workers.dev",
            tokenSaved = true,
            blacklist = "银行\n支付",
            history = listOf(blocked, blocked),
            sends = emptyList(),
            sims = listOf(
                sim(1, "中国联通", "****7689"),
                sim(2, "中国移动全球通", "****3054"),
            ),
            missingPermissions = emptyList(),
            batteryUnrestricted = true,
            fullScreenAllowed = true,
            pendingConfirms = 0,
            versionName = "2.5.1",
            nowMs = now,
            checks = marketingChecks().map { it.copy(status = SetupChecks.Status.OK) },
        )
    }

    private fun marketingChecks(): List<SetupChecks.Check> = listOf(
        SetupChecks.Check(SetupChecks.Id.SMS, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.PHONE, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.NOTIFICATIONS, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.FULL_SCREEN, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.BATTERY, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.POPUP, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.SAMSUNG_SLEEP, SetupChecks.Status.MANUAL, required = false),
    )

    private fun sim(slot: Int, carrier: String, masked: String) = SimSlot(
        subscriptionId = slot,
        simSlotIndex = slot - 1,
        slot = slot,
        carrier = carrier,
        mccmnc = null,
        number = null,
        numberMasked = masked,
        ready = true,
    )

    private fun clock(hhmm: String): Long {
        val parts = hhmm.split(":")
        val cal = Calendar.getInstance(zone)
        cal.set(2026, Calendar.NOVEMBER, 24, parts[0].toInt(), parts[1].toInt(), 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun isoFmt(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun noopActions() = MainActions(
        setEnabled = {},
        saveConnection = { _, _ -> },
        saveBlacklist = {},
        testUpload = {},
        requestPermissions = {},
        openBatterySettings = {},
        openFullScreenSettings = {},
        openPendingConfirm = {},
    )
}

private val FrameBg = Color(0xFF0E1513)
private val FrameFg = Color(0xFFE7E8EA)
private val FrameMute = Color(0xFFBFC9C5)

@Composable
private fun PhoneFrame(time: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FrameBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, color = FrameFg, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 10.dp).background(FrameFg, RoundedCornerShape(1.dp)))
                Box(Modifier.size(width = 16.dp, height = 10.dp).background(FrameFg, RoundedCornerShape(1.dp)))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(11.dp)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(11.dp)
                            .background(FrameFg, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        Row(
            Modifier
                .fillMaxWidth()
                .height(33.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 3-button nav stand-in (menu, home, back), cropped off the site frames.
            Box(Modifier.size(14.dp, 10.dp).background(FrameMute, RoundedCornerShape(1.dp)))
            Box(Modifier.size(12.dp).background(FrameMute, CircleShape))
            Box(Modifier.size(10.dp, 10.dp).background(FrameMute, RoundedCornerShape(1.dp)))
        }
    }
}
