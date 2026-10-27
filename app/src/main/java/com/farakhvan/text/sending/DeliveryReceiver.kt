package com.farakhvan.text.sending

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.farakhvan.text.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manifest-declared receiver for carrier delivery reports.
 *
 * The sending service only lives while a campaign runs, but a delivery report can arrive
 * minutes later (or after the process was killed). Because the PendingIntent targets this
 * package explicitly, the broadcast still resolves here and the row is updated to DELIVERED.
 */
class DeliveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SendService.ACTION_DELIVERED) return
        val id = intent.getLongExtra(EXTRA_RESULT_ID, -1L)
        if (id <= 0L) return
        if (resultCode != Activity.RESULT_OK) return

        val pending = goAsync()
        val db = AppDatabase.get(context.applicationContext)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                db.resultDao().markDelivered(id, System.currentTimeMillis())
            } catch (e: Exception) {
                // A lost delivery report must never crash the app.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_RESULT_ID = "resultId"
    }
}
