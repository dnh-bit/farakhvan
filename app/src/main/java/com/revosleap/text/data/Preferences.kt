package com.revosleap.text.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(val delay: Int = 3, val sim: Int = -1, val theme: String = "system",
    val confirmation: Boolean = true)

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(Settings(
        prefs.getInt("delay", 3).coerceIn(1, 30),
        prefs.getInt("sim", -1), prefs.getString("theme", "system") ?: "system",
        prefs.getBoolean("confirmation", true)
    ))
    val settings = mutable.asStateFlow()
    fun update(settings: Settings) {
        val safe = settings.copy(delay = settings.delay.coerceIn(1, 30))
        prefs.edit().putInt("delay", safe.delay).putInt("sim", safe.sim)
            .putString("theme", safe.theme).putBoolean("confirmation", safe.confirmation).apply()
        mutable.value = safe
    }
}
