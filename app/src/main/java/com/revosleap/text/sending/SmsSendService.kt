package com.revosleap.text.sending

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.revosleap.text.FarakhvanApp
import com.revosleap.text.MainActivity
import com.revosleap.text.R
import com.revosleap.text.data.Campaign
import com.revosleap.text.data.States
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class SmsSendService : Service() {
    private val app get() = application as FarakhvanApp
    private val dao get() = app.repository.dao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Mutex()
    private var worker: Job? = null
    private var inFlight: Long? = null
    private var lastDispatch = 0L

    override fun onCreate() {
        super.onCreate()
        alive = true
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIFICATION, notification(0, 0),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        val id = intent?.getLongExtra("campaign", -1) ?: -1
        val action = intent?.action ?: START
        scope.launch {
            commands.withLock {
                if (id <= 0 || (worker?.isActive == true && currentCampaign != id)) {
                    if (worker?.isActive != true) finishService()
                    return@withLock
                }
                when (action) {
                    PAUSE -> dao.campaignState(id, States.PAUSED)
                    RESUME -> dao.campaignState(id, States.RUNNING)
                    CANCEL -> {
                        dao.campaignState(id, States.CANCELLED)
                        inFlight?.let {
                            dao.unknownIfSending(it, "uncertain", System.currentTimeMillis())
                        }
                        worker?.cancel()
                        finishService()
                        return@withLock
                    }
                    START -> {
                        val existing = dao.activeCampaign()
                        if (existing != null && existing.id != id && alive && worker?.isActive == true)
                            return@withLock
                        dao.campaignState(id, States.RUNNING)
                    }
                }
                if (worker?.isActive != true) {
                    currentCampaign = id
                    worker = scope.launch { runQueue(id) }
                }
            }
        }
        // No automatic restart or resend after process death.
        return START_NOT_STICKY
    }

    private suspend fun runQueue(id: Long) {
        try {
            val campaign = dao.campaignNow(id) ?: return
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                dao.campaignState(id, States.INTERRUPTED)
                return
            }
            val sms = smsManager(campaign)
            updateNotification(id)
            // Never overlap the last platform attempt following a resume.
            if (lastDispatch == 0L) delay(campaign.delaySeconds * 1000L)
            while (true) {
                val state = dao.campaignNow(id)?.state ?: break
                updateNotification(id)
                if (state == States.CANCELLED) break
                if (state == States.PAUSED) { delay(500); continue }
                if (state != States.RUNNING) break
                val row = dao.next(id) ?: break
                val gap = campaign.delaySeconds * 1000L - (SystemClock.elapsedRealtime() - lastDispatch)
                if (gap > 0) delay(gap)
                // Re-check after delay so pause/cancel doesn't dispatch one extra SMS.
                if (dao.campaignNow(id)?.state != States.RUNNING) continue
                if (!row.valid || row.renderedBody.isBlank()) {
                    dao.resultState(row.id, States.FAILED,
                        if (!row.valid) "invalid" else "empty", System.currentTimeMillis())
                    continue
                }
                val parts = sms.divideMessage(row.renderedBody)
                val attempt = row.attempt + 1
                if (dao.claim(row.id, attempt, parts.size, System.currentTimeMillis()) == 0) continue
                inFlight = row.id
                updateNotification(id)
                val callbacks = ArrayList(parts.indices.map { part ->
                    val callback = Intent(this, SmsResultReceiver::class.java)
                        .setAction(SENT_ACTION)
                        .setData(Uri.parse("farakhvan://sent/${row.id}/$attempt/$part"))
                        .putExtra("result", row.id).putExtra("attempt", attempt).putExtra("part", part)
                    PendingIntent.getBroadcast(this, 0, callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                })
                try {
                    lastDispatch = SystemClock.elapsedRealtime()
                    if (parts.size == 1) {
                        sms.sendTextMessage(row.number, null, parts.first(), callbacks.first(), null)
                    } else {
                        sms.sendMultipartTextMessage(row.number, null, parts, callbacks, null)
                    }
                } catch (_: SecurityException) {
                    dao.resultState(row.id, States.FAILED, "permission", System.currentTimeMillis())
                    dao.campaignState(id, States.INTERRUPTED)
                } catch (_: IllegalArgumentException) {
                    dao.resultState(row.id, States.FAILED, "invalid", System.currentTimeMillis())
                } catch (_: Exception) {
                    // Binder/runtime failure can occur after some parts entered the modem.
                    dao.unknownIfSending(row.id, "uncertain", System.currentTimeMillis())
                }
                val resolved = withTimeoutOrNull(90_000) {
                    dao.results(id).first { rows -> rows.firstOrNull { it.id == row.id }?.status != States.SENDING }
                }
                if (resolved == null) {
                    dao.unknownIfSending(row.id, "timeout", System.currentTimeMillis())
                }
                inFlight = null
                val result = dao.resultNow(row.id)
                if (result?.status == States.UNKNOWN) {
                    dao.campaignState(id, States.INTERRUPTED)
                    break
                }
            }
            val campaignState = dao.campaignNow(id)?.state
            if (campaignState == States.RUNNING && dao.next(id) == null) {
                val uncertain = dao.resultsNow(id).any { it.status == States.UNKNOWN }
                dao.campaignState(id, if (uncertain) States.INTERRUPTED else States.COMPLETE)
            }
            updateNotification(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            dao.campaignState(id, States.INTERRUPTED)
            inFlight?.let {
                dao.unknownIfSending(it, "uncertain", System.currentTimeMillis())
            }
        } finally {
            if (currentCampaign == id) finishService()
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(campaign: Campaign): SmsManager {
        if (campaign.subscriptionId != -1) {
            // A removed/disabled SIM must not silently fall back to another SIM.
            val subscriptions = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            require(subscriptions.any { it.subscriptionId == campaign.subscriptionId })
            return SmsManager.getSmsManagerForSubscriptionId(campaign.subscriptionId)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val first = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                .orEmpty().minByOrNull { it.simSlotIndex }
            if (first != null) return SmsManager.getSmsManagerForSubscriptionId(first.subscriptionId)
        }
        return if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
    }

    private suspend fun updateNotification(id: Long) {
        val rows = dao.resultsNow(id)
        val done = rows.count { it.status in listOf(States.SENT, States.FAILED, States.UNKNOWN) }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(done, rows.size))
    }

    private fun notification(done: Int, total: Int): android.app.Notification {
        val open = Intent(this, MainActivity::class.java).putExtra("campaign", currentCampaign)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sms).setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_progress, done, total))
            .setProgress(total, done, total == 0).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    private fun finishService() {
        currentCampaign = -1
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        alive = false
        currentCampaign = -1
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "sms_queue"
        const val NOTIFICATION = 10
        const val SENT_ACTION = "com.revosleap.text.SMS_SENT"
        const val START = "com.revosleap.text.START"
        const val PAUSE = "com.revosleap.text.PAUSE"
        const val RESUME = "com.revosleap.text.RESUME"
        const val CANCEL = "com.revosleap.text.CANCEL"
        @Volatile var alive = false
            private set
        @Volatile var currentCampaign: Long = -1
            private set
        fun command(context: Context, id: Long, action: String = START) {
            ContextCompat.startForegroundService(context,
                Intent(context, SmsSendService::class.java).setAction(action).putExtra("campaign", id))
        }
    }
}
