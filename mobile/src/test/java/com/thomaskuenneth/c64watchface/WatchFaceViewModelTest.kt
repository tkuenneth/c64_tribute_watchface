package com.thomaskuenneth.c64watchface

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.thomaskuenneth.common.C64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchFaceViewModelTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    @Before
    fun startFromTheDefaults() {
        preferences.edit { clear() }
    }

    @Test
    fun everySettingStartsOff() {
        val viewModel = WatchFaceViewModel(application)

        assertEquals(WatchFaceSettings(), viewModel.settings.value)
    }

    @Test
    fun switchingASettingOnIsPublishedAndStored() {
        val viewModel = WatchFaceViewModel(application)

        viewModel.setDate(true)

        assertTrue("the state did not follow", viewModel.settings.value.date)
        assertTrue(
            "the preference was not written",
            preferences.getBoolean(C64.PREFS_DATE, false),
        )
    }

    @Test
    fun theWatchFaceUsesTheSameKeysAsTheSettings() {
        val viewModel = WatchFaceViewModel(application)

        viewModel.setSeconds(true)
        viewModel.setUppercase(true)

        assertTrue(preferences.getBoolean(C64.PREFS_SECONDS, false))
        assertTrue(preferences.getBoolean(C64.PREFS_UPPERCASE, false))
    }

    @Test
    fun aPreferenceChangedElsewhereReachesTheState() {
        val viewModel = WatchFaceViewModel(application)

        preferences.edit { putBoolean(C64.PREFS_UPPERCASE, true) }

        assertTrue("a change from outside was ignored", viewModel.settings.value.uppercase)
    }

    @Test
    fun theHintIsRememberedOnceItWasAcknowledged() {
        val viewModel = WatchFaceViewModel(application)
        assertFalse(viewModel.settings.value.hintAcknowledged)

        viewModel.acknowledgeHint()

        assertTrue(viewModel.settings.value.hintAcknowledged)
        assertTrue(WatchFaceViewModel(application).settings.value.hintAcknowledged)
    }

    @Test
    fun theModelIsBuiltOnceForTheScreenToDraw() {
        val viewModel = WatchFaceViewModel(application)

        assertEquals(viewModel.c64, viewModel.c64)
        assertFalse("the preview must not centre horizontally", viewModel.c64.centerHorizontally)
    }
}
