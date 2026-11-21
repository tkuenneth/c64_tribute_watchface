package com.thomaskuenneth.c64watchface

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thomaskuenneth.common.C64
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TIMEOUT_MILLIS = 8_000L
private const val NANOS_PER_MILLI = 1_000_000L
private val QUIET_MILLIS = 4L * C64.INTERACTIVE_UPDATE_RATE_MS

@RunWith(RobolectricTestRunner::class)
class WatchFaceCursorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var watchFaceVisible by mutableStateOf(true)

    @Test
    fun theCursorBlinksWhileTheWatchFaceIsOnScreen() {
        val c64 = showWatchFace()

        awaitToggles(c64, 3)
    }

    @Test
    fun theCursorStopsBlinkingOnceTheWatchFaceIsGone() {
        val c64 = showWatchFace()
        awaitToggles(c64, 2)

        watchFaceVisible = false
        composeTestRule.waitForIdle()

        awaitQuiet(c64)
    }

    private fun showWatchFace(): C64 {
        val preferences = PreferenceManager.getDefaultSharedPreferences(composeTestRule.activity)
        preferences.edit { putBoolean("hint_acknowledged", true) }
        composeTestRule.setContent {
            if (watchFaceVisible) {
                WatchFaceScreen(
                    sharedPreferences = preferences,
                    onNavigateSettings = {},
                    onNavigateInfo = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        val view = checkNotNull(findWatchFace(composeTestRule.activity.window.decorView)) {
            "The watch face preview is not in the view hierarchy"
        }
        return checkNotNull(view.tag as? C64) {
            "The watch face preview carries no C64 to draw"
        }
    }

    private fun findWatchFace(view: View): C64WatchFace? = when {
        view is C64WatchFace -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull {
            findWatchFace(view.getChildAt(it))
        }
        else -> null
    }

    private fun awaitToggles(c64: C64, toggles: Int) {
        var previous = c64.c64CursorVisible
        var seen = 0
        composeTestRule.waitUntil("the cursor changed $toggles times", TIMEOUT_MILLIS) {
            val current = c64.c64CursorVisible
            if (current != previous) {
                previous = current
                seen++
            }
            seen >= toggles
        }
    }

    private fun awaitQuiet(c64: C64) {
        var previous = c64.c64CursorVisible
        var lastToggle = System.nanoTime()
        composeTestRule.waitUntil(
            "the cursor stayed unchanged for $QUIET_MILLIS ms",
            TIMEOUT_MILLIS
        ) {
            val current = c64.c64CursorVisible
            if (current != previous) {
                previous = current
                lastToggle = System.nanoTime()
            }
            System.nanoTime() - lastToggle > QUIET_MILLIS * NANOS_PER_MILLI
        }
    }
}
