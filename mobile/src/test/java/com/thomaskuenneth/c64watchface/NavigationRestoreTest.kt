package com.thomaskuenneth.c64watchface

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationRestoreTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun hideTheHint() {
        PreferenceManager.getDefaultSharedPreferences(composeTestRule.activity).edit {
            clear()
            putBoolean(PREFS_HINT_ACKNOWLEDGED, true)
        }
    }

    @Test
    fun aFoldDoesNotThrowTheUserOffTheSettingsScreen() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { MainAppScreen() } }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
