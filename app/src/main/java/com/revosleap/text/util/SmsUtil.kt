package com.revosleap.text.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** How many SMS parts one message costs per recipient. */
data class SmsInfo(
    val parts: Int,
    val used: Int,
    val perPart: Int,
    val unicode: Boolean
)

data class SimInfo(val subId: Int, val slot: Int, val label: String)

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

object SmsUtil {

    /** GSM-7 vs UCS-2 detection and part counting, delegated to the platform for exact results. */
    fun analyze(text: String): SmsInfo {
        if (text.isEmpty()) return SmsInfo(0, 0, 160, false)
        return try {
            val r = SmsMessage.calculateLength(text, false)
            // r = [parts, code units used, code units remaining in last part, encoding unit size]
            SmsInfo(
                parts = r[0],
                used = r[1],
                perPart = r[1] + r[2],
                unicode = r[3] == SmsMessage.ENCODING_16BIT
            )
        } catch (e: Exception) {
            val unicode = text.any { it.code > 127 }
            val single = if (unicode) 70 else 160
            val multi = if (unicode) 67 else 153
            val parts = if (text.length <= single) 1 else (text.length + multi - 1) / multi
            SmsInfo(parts, text.length, if (parts == 1) single else multi * parts, unicode)
        }
    }

    @SuppressLint("MissingPermission")
    fun activeSims(context: Context): List<SimInfo> {
        if (!context.hasPermission(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val list = sm?.activeSubscriptionInfoList ?: emptyList()
            list.sortedBy { it.simSlotIndex }.map {
                SimInfo(it.subscriptionId, it.simSlotIndex, it.displayName?.toString().orEmpty())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** True when the hardware reports two modems, even before the phone-state permission is granted. */
    @Suppress("DEPRECATION")
    fun looksDualSim(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            (tm?.phoneCount ?: 1) >= 2
        } catch (e: Exception) {
            false
        }
    }

    /** subscription id for a slot, or -1 for the system default. */
    fun subIdForSlot(context: Context, slot: Int): Int {
        return activeSims(context).firstOrNull { it.slot == slot }?.subId ?: -1
    }

    fun smsManager(context: Context, subId: Int): SmsManager {
        val base: SmsManager = defaultManager(context)
        if (subId < 0) return base
        return forSubscription(context, subId) ?: base
    }

    @Suppress("DEPRECATION")
    private fun defaultManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }

    @Suppress("DEPRECATION")
    private fun forSubscription(context: Context, subId: Int): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } catch (e: Exception) {
            null
        }
    }
}
