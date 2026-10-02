package com.farakhvan.text.sending

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-side entry point to the sending service. Only one campaign can be active at a time. */
object SendController {
    const val ACTION_START = "com.farakhvan.text.action.START"
    const val ACTION_PAUSE = "com.farakhvan.text.action.PAUSE"
    const val ACTION_RESUME = "com.farakhvan.text.action.RESUME"
    const val ACTION_CANCEL = "com.farakhvan.text.action.CANCEL"
    const val EXTRA_CAMPAIGN_ID = "campaignId"

    private val _active = MutableStateFlow<Long?>(null)
    val active: StateFlow<Long?> = _active.asStateFlow()

    internal fun clearActive() {
        _active.value = null
    }

    /**
     * Starts (or continues) sending the pending rows of [campaignId].
     * Returns false when another campaign is currently being sent.
     */
    fun start(context: Context, campaignId: Long): Boolean {
        val current = _active.value
        if (current != null && current != campaignId) return false
        if (current == campaignId) return true // the running engine picks up newly pending rows
        _active.value = campaignId
        val intent = Intent(context, SendService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_CAMPAIGN_ID, campaignId)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            _active.value = null
            false
        }
    }

    fun pause(context: Context) = command(context, ACTION_PAUSE)
    fun resume(context: Context) = command(context, ACTION_RESUME)
    fun cancel(context: Context) = command(context, ACTION_CANCEL)

    private fun command(context: Context, action: String) {
        try {
            context.startService(Intent(context, SendService::class.java).setAction(action))
        } catch (e: Exception) {
            // Service not running; nothing to control.
        }
    }
}
