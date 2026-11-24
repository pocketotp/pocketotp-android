package com.pocketotp.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocketotp.app.SetupChecks
import com.pocketotp.app.ui.ChecklistRow
import com.pocketotp.app.ui.PocketOtpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Layout goldens for every permission row, including the narrow foldable
 * cover width (~320dp) and font scale 1.3, in English and Chinese.
 * The "Recommended" badge must stay on one line and move under a long
 * title instead of wrapping letter-by-letter.
 *
 * Record:  ./gradlew :app:recordRoborazziDebug --tests com.pocketotp.app.screenshot.ChecklistRowScreenshotTest
 * Outputs: app/src/test/screenshots/checklist/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChecklistRowScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "en-w360dp-h880dp-xxhdpi")
    fun allRowsEn() = capture("all-rows-en")

    @Test
    @Config(qualifiers = "zh-w360dp-h880dp-xxhdpi")
    fun allRowsZh() = capture("all-rows-zh")

    @Test
    @Config(qualifiers = "en-w320dp-h1200dp-xxhdpi")
    fun coverEn() = capture("cover-en")

    @Test
    @Config(qualifiers = "zh-w320dp-h1200dp-xxhdpi")
    fun coverZh() = capture("cover-zh")

    @Test
    @Config(qualifiers = "en-w320dp-h1200dp-xxhdpi")
    fun coverEnFont13() = capture("cover-en-font13", fontScale = 1.3f)

    @Test
    @Config(qualifiers = "zh-w320dp-h1200dp-xxhdpi")
    fun coverZhFont13() = capture("cover-zh-font13", fontScale = 1.3f)

    private fun capture(name: String, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PocketOtpTheme(darkTheme = true) { ChecklistCard() }
            }
        }
        composeRule.waitForIdle()
        val out = File("src/test/screenshots/checklist", "$name.png")
        out.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(
            filePath = out.path,
            roborazziOptions = RoborazziOptions(),
        )
    }

    @Composable
    private fun ChecklistCard() {
        Surface {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
                    checks().forEachIndexed { i, check ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ChecklistRow(check, onFix = {})
                    }
                }
            }
        }
    }

    private fun checks(): List<SetupChecks.Check> = listOf(
        SetupChecks.Check(SetupChecks.Id.SMS, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.PHONE, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.NOTIFICATIONS, SetupChecks.Status.OK, required = true),
        SetupChecks.Check(SetupChecks.Id.FULL_SCREEN, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.BATTERY, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.POPUP, SetupChecks.Status.MISSING, required = false),
        SetupChecks.Check(SetupChecks.Id.SAMSUNG_SLEEP, SetupChecks.Status.MANUAL, required = false),
    )
}
