package com.farakhvan.text.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.farakhvan.text.data.ResultStatus
import com.farakhvan.text.data.SendResultEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Forces left-to-right rendering of numbers inside RTL text. */
fun String.ltr(): String = "\u202A$this\u202C"

object Formatting {

    /** Jalali (Persian calendar) date for the UI. */
    fun dateTime(millis: Long): String {
        return try {
            val f = android.icu.text.SimpleDateFormat(
                "yyyy/MM/dd  HH:mm",
                android.icu.util.ULocale("fa_IR@calendar=persian")
            )
            f.format(Date(millis))
        } catch (e: Exception) {
            SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(Date(millis))
        }
    }

    private fun isoDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    /** CSV with header number,status,timestamp (machine-readable, Latin). */
    fun buildCsv(rows: List<SendResultEntity>): String {
        val sb = StringBuilder("number,status,timestamp\n")
        for (r in rows) {
            val status = when (r.status) {
                ResultStatus.SENT -> "sent"
                ResultStatus.FAILED -> "failed"
                ResultStatus.SENDING -> "sending"
                else -> "pending"
            }
            val ts = if (r.updatedAt > 0) isoDateTime(r.updatedAt) else ""
            sb.append(r.number).append(',').append(status).append(',').append(ts).append('\n')
        }
        return sb.toString()
    }

    /** Writes the CSV to the cache dir and returns a share-sheet intent. */
    fun csvShareIntent(context: Context, campaignId: Long, rows: List<SendResultEntity>, chooserTitle: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "campaign-$campaignId.csv")
        file.writeText(buildCsv(rows), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle)
    }
}
