package com.pocketotp.app

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Pads a screen so content stays clear of system bars, the display cutout, and the IME. */
object WindowInsetsPad {
    private const val EXTRA_BOTTOM_DP = 8

    fun apply(activity: Activity, root: View, extraBottomDp: Int = EXTRA_BOTTOM_DP) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        val extraBottomPx = (extraBottomDp * root.resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom + extraBottomPx,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
