package com.revosleap.text.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Compose-observable app settings backed by SharedPreferences. */
class Settings private constructor(private val prefs: SharedPreferences) {

    private var _delaySec by mutableIntStateOf(prefs.getInt(KEY_DELAY, 3).coerceIn(1, 30))
    private var _defaultSim by mutableIntStateOf(prefs.getInt(KEY_SIM, 0))
    private var _theme by mutableIntStateOf(prefs.getInt(KEY_THEME, 0))
    private var _confirm by mutableStateOf(prefs.getBoolean(KEY_CONFIRM, true))

    /** Delay between two messages, 1..30 seconds. */
    var delaySec: Int
        get() = _delaySec
        set(value) {
            _delaySec = value.coerceIn(1, 30)
            prefs.edit().putInt(KEY_DELAY, _delaySec).apply()
        }

    /** SIM slot index (0 = SIM 1, 1 = SIM 2). */
    var defaultSim: Int
        get() = _defaultSim
        set(value) {
            _defaultSim = value
            prefs.edit().putInt(KEY_SIM, value).apply()
        }

    /** 0 = system, 1 = light, 2 = dark. */
    var theme: Int
        get() = _theme
        set(value) {
            _theme = value
            prefs.edit().putInt(KEY_THEME, value).apply()
        }

    var confirmBeforeSend: Boolean
        get() = _confirm
        set(value) {
            _confirm = value
            prefs.edit().putBoolean(KEY_CONFIRM, value).apply()
        }

    companion object {
        private const val KEY_DELAY = "delay_sec"
        private const val KEY_SIM = "default_sim"
        private const val KEY_THEME = "theme"
        private const val KEY_CONFIRM = "confirm"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings {
            return instance ?: synchronized(this) {
                instance ?: Settings(
                    context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
