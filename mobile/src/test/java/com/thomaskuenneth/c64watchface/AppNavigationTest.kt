package com.thomaskuenneth.c64watchface

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.thomaskuenneth.common.C64
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun hideTheHint() {
        PreferenceManager.getDefaultSharedPreferences(composeTestRule.activity).edit {
            clear()
            putBoolean(PREFS_HINT_ACKNOWLEDGED, true)
        }
        composeTestRule.setContent { AppTheme { MainAppScreen() } }
    }

    @Test
    fun theSettingsScreenShowsEverySwitch() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Date").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seconds").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capitals only").assertIsDisplayed()
    }

    @Test
    fun aSwitchedSettingReachesThePreferencesTheWatchFaceReads() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        val dateRow = composeTestRule.onNode(hasText("Date") and isToggleable())
        dateRow.assertIsOff()
        dateRow.performClick()
        composeTestRule.waitForIdle()
        dateRow.assertIsOn()

        val preferences = PreferenceManager.getDefaultSharedPreferences(composeTestRule.activity)
        assertTrue(
            "switching the date on did not reach the preferences the watch face reads",
            preferences.getBoolean(C64.PREFS_DATE, false),
        )
    }

    @Test
    fun backLeavesTheSettingsScreen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun theAboutScreenCreditsTheFontAndOffersItsLicense() {
        composeTestRule.onNodeWithContentDescription("Info").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Open source licenses").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("View Font License").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntilExactlyOneExists(hasText("Kreative Software License"), 5_000)
    }
}
