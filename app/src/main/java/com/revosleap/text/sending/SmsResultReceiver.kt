package com.revosleap.text.sending

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.revosleap.text.FarakhvanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsSendService.SENT_ACTION) return
        val pending = goAsync()
        val code = resultCode
        val id = intent.getLongExtra("result", -1)
        val attempt = intent.getLongExtra("attempt", -1)
        val index = intent.getIntExtra("part", -1)
        scope.launch {
            try {
                (context.applicationContext as FarakhvanApp).repository.callback(id, attempt, index, code)
            } finally { pending.finish() }
        }
    }
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
