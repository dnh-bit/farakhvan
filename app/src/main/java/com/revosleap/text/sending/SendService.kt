package com.revosleap.text.sending

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.revosleap.text.MainActivity
import com.revosleap.text.R
import com.revosleap.text.data.AppDatabase
import com.revosleap.text.data.CampaignStatus
import com.revosleap.text.data.ResultStatus
import com.revosleap.text.data.SendResultEntity
import com.revosleap.text.util.PhoneNormalizer
import com.revosleap.text.util.SmsUtil
import com.revosleap.text.util.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that drains the pending rows of one campaign:
 * one message at a time, with a configurable delay, real delivery results through PendingIntents.
 */
class SendService : Service() {

    private class Tracker(var remaining: Int) {
        val done = CompletableDeferred<Int>()

        @Synchronized
        fun onPart(code: Int) {
            if (done.isCompleted) return
            if (code != Activity.RESULT_OK) {
                done.complete(code)
            } else {
                remaining--
                if (remaining <= 0) done.complete(Activity.RESULT_OK)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var currentId: Long = -1L
    private val paused = MutableStateFlow(false)
    private val trackers = ConcurrentHashMap<Long, Tracker>()
    private val requestCodes = AtomicInteger(1)
    private var wakeLock: PowerManager.WakeLock? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(EXTRA_RESULT_ID, -1L)
            trackers[id]?.onPart(resultCode)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ACTION_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SendController.ACTION_START -> {
                val id = intent.getLongExtra(SendController.EXTRA_CAMPAIGN_ID, -1L)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(id, 0, 0, false),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                )
                if (id < 0 || job?.isActive == true) {
                    if (id < 0) finishService()
                } else {
                    job = scope.launch {
                        try {
                            runCampaign(id)
                        } finally {
                            withContext(NonCancellable) { postSummary(id) }
                            finishService()
                        }
                    }
                }
            }

            SendController.ACTION_PAUSE -> {
                if (job?.isActive == true) {
                    paused.value = true
                    val id = currentId
                    scope.launch { AppDatabase.get(applicationContext).campaignDao().setStatus(id, CampaignStatus.PAUSED) }
                } else {
                    finishService()
                }
            }

            SendController.ACTION_RESUME -> {
                if (job?.isActive == true) {
                    paused.value = false
                    val id = currentId
                    scope.launch { AppDatabase.get(applicationContext).campaignDao().setStatus(id, CampaignStatus.RUNNING) }
                } else {
                    finishService()
                }
            }

            SendController.ACTION_CANCEL -> {
                val j = job
                if (j != null && j.isActive) j.cancel() else finishService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // already unregistered
        }
        releaseWakeLock()
        scope.cancel()
        SendController.clearActive()
        super.onDestroy()
    }

    private fun finishService() {
        SendController.clearActive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ engine

    private suspend fun runCampaign(id: Long) {
        val db = AppDatabase.get(applicationContext)
        val campaign = db.campaignDao().get(id) ?: return
        currentId = id
        paused.value = false
        acquireWakeLock()
        try {
            db.campaignDao().setStatus(id, CampaignStatus.RUNNING)
            val sms = SmsUtil.smsManager(applicationContext, campaign.simSubId)
            while (true) {
                paused.first { !it }
                val row = db.resultDao().nextPending(id) ?: break

                var attempted = false
                if (!PhoneNormalizer.normalize(row.number).valid) {
                    // Never crash on garbage input: just mark and move on without waiting.
                    db.resultDao().markResult(
                        row.id, ResultStatus.FAILED,
                        getString(R.string.reason_invalid_number), System.currentTimeMillis()
                    )
                } else {
                    attempted = true
                    db.resultDao().markSending(row.id, System.currentTimeMillis())
                    val failure = sendOne(sms, row)
                    db.resultDao().markResult(
                        row.id,
                        if (failure == null) ResultStatus.SENT else ResultStatus.FAILED,
                        failure.orEmpty(),
                        System.currentTimeMillis()
                    )
                }
                updateProgressNotification(id, db.resultDao().doneCount(id), db.resultDao().totalCount(id))

                // Carriers throttle bursts: wait between messages.
                if (attempted && db.resultDao().pendingCount(id) > 0) {
                    delay(campaign.delayMs.coerceIn(1000L, 30000L))
                }
            }
            db.campaignDao().setStatus(id, CampaignStatus.DONE)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                db.resultDao().failUnsent(id, getString(R.string.reason_cancelled), System.currentTimeMillis())
                db.campaignDao().setStatus(id, CampaignStatus.CANCELLED)
            }
            throw e
        } finally {
            releaseWakeLock()
        }
    }

    /** Sends one message (single or multipart). Returns null on success, or a human reason. */
    private suspend fun sendOne(sms: SmsManager, row: SendResultEntity): String? {
        val parts = try {
            sms.divideMessage(row.body)
        } catch (e: Exception) {
            return e.message ?: getString(R.string.reason_generic)
        }
        if (parts.isEmpty()) return getString(R.string.reason_generic)

        val tracker = Tracker(parts.size)
        trackers[row.id] = tracker
        try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (i in parts.indices) sentIntents.add(sentIntent(row.id, i))
            if (parts.size == 1) {
                sms.sendTextMessage(row.number, null, parts[0], sentIntents[0], null)
            } else {
                sms.sendMultipartTextMessage(row.number, null, parts, sentIntents, null)
            }
            val code: Int = withTimeoutOrNull(RESULT_TIMEOUT_MS) { tracker.done.await() }
                ?: return getString(R.string.reason_timeout)
            return if (code == Activity.RESULT_OK) null else reasonFor(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return getString(R.string.reason_permission)
        } catch (e: Exception) {
            return e.message ?: getString(R.string.reason_generic)
        } finally {
            trackers.remove(row.id)
        }
    }

    private fun reasonFor(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> getString(R.string.reason_generic)
        SmsManager.RESULT_ERROR_RADIO_OFF -> getString(R.string.reason_radio_off)
        SmsManager.RESULT_ERROR_NULL_PDU -> getString(R.string.reason_null_pdu)
        SmsManager.RESULT_ERROR_NO_SERVICE -> getString(R.string.reason_no_service)
        else -> getString(R.string.reason_other, code)
    }

    private fun sentIntent(resultId: Long, part: Int): PendingIntent {
        val intent = Intent(ACTION_SENT)
            .setPackage(packageName)
            .putExtra(EXTRA_RESULT_ID, resultId)
            .putExtra(EXTRA_PART, part)
        return PendingIntent.getBroadcast(
            this,
            requestCodes.getAndIncrement(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ------------------------------------------------------------ wake lock

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "farakhvan:sending")?.apply {
            acquire(5 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null
    }

    // --------------------------------------------------------- notifications

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun contentIntent(campaignId: Long): PendingIntent {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(SendController.EXTRA_CAMPAIGN_ID, campaignId)
        return PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildNotification(campaignId: Long, done: Int, total: Int, finished: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent(campaignId))
            .setOnlyAlertOnce(true)
            .setAutoCancel(finished)
            .setOngoing(!finished)
        if (finished) {
            builder.setContentTitle(getString(R.string.notif_finished_title))
                .setContentText(getString(R.string.notif_finished_text, done, total))
        } else {
            builder.setContentTitle(getString(R.string.notif_progress, done, total))
                .setContentText(getString(R.string.notif_tap_to_return))
                .setProgress(total, done, total == 0)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    private fun updateProgressNotification(campaignId: Long, done: Int, total: Int) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification(campaignId, done, total, false))
    }

    @SuppressLint("MissingPermission")
    private suspend fun postSummary(campaignId: Long) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val db = AppDatabase.get(applicationContext)
        val done = db.resultDao().doneCount(campaignId)
        val total = db.resultDao().totalCount(campaignId)
        NotificationManagerCompat.from(this)
            .notify(SUMMARY_ID, buildNotification(campaignId, done, total, true))
    }

    companion object {
        private const val CHANNEL_ID = "sending"
        private const val NOTIFICATION_ID = 1
        private const val SUMMARY_ID = 2
        private const val ACTION_SENT = "com.revosleap.text.SMS_SENT"
        private const val EXTRA_RESULT_ID = "resultId"
        private const val EXTRA_PART = "part"
        private const val RESULT_TIMEOUT_MS = 90_000L
    }
}
