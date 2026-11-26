package com.thomaskuenneth.c64watchface

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.thomaskuenneth.common.C64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val PREFS_HINT_ACKNOWLEDGED = "hint_acknowledged"

data class WatchFaceSettings(
    val date: Boolean = false,
    val seconds: Boolean = false,
    val uppercase: Boolean = false,
    val hintAcknowledged: Boolean = false,
)

class WatchFaceViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(application)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<WatchFaceSettings> = _settings.asStateFlow()

    val c64: C64 = C64(application, preferences).apply {
        centerHorizontally = false
        setup()
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        c64.stopPulse()
    }

    fun setDate(show: Boolean) = write(C64.PREFS_DATE, show)

    fun setSeconds(show: Boolean) = write(C64.PREFS_SECONDS, show)

    fun setUppercase(uppercase: Boolean) = write(C64.PREFS_UPPERCASE, uppercase)

    fun acknowledgeHint() = write(PREFS_HINT_ACKNOWLEDGED, true)

    private fun write(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    private fun readSettings() = WatchFaceSettings(
        date = preferences.getBoolean(C64.PREFS_DATE, false),
        seconds = preferences.getBoolean(C64.PREFS_SECONDS, false),
        uppercase = preferences.getBoolean(C64.PREFS_UPPERCASE, false),
        hintAcknowledged = preferences.getBoolean(PREFS_HINT_ACKNOWLEDGED, false),
    )
}
