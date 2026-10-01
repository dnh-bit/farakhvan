package com.revosleap.text.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Repository(val database: AppDatabase, private val context: Context) {
    val dao = database.dao()

    suspend fun addMembers(group: Long, recipients: List<Recipient>): Pair<Int, Int> =
        database.withTransaction {
            val unique = Numbers.deduplicate(recipients)
            val inserted = dao.insertMembers(unique.map {
                Member(groupId = group, number = it.number, name = it.name, valid = it.valid)
            }).count { it != -1L }
            inserted to (recipients.size - inserted)
        }

    suspend fun createCampaign(target: String, body: String, recipients: List<Recipient>,
        settings: Settings, group: Long?): Long = database.withTransaction {
        val id = dao.insertCampaign(Campaign(target = target, body = body,
            subscriptionId = settings.sim, delaySeconds = settings.delay))
        dao.insertResults(Numbers.deduplicate(recipients).map {
            val rendered = body.replace("{name}", it.name)
            val invalid = !it.valid || rendered.isBlank()
            SendResult(campaignId = id, number = it.number, name = it.name,
                renderedBody = rendered, valid = it.valid,
                status = if (invalid) States.FAILED else States.PENDING,
                reason = if (!it.valid) "invalid" else if (rendered.isBlank()) "empty" else "",
                timestamp = if (invalid) System.currentTimeMillis() else 0)
        })
        group?.let { dao.touchGroup(it, System.currentTimeMillis()) }
        id
    }

    suspend fun recover() = database.withTransaction {
        dao.interruptCampaigns()
        dao.markInFlightUnknown()
        dao.purgeDeletedGroups()
    }

    suspend fun callback(id: Long, attempt: Long, index: Int, code: Int) = database.withTransaction {
        val row = dao.resultNow(id) ?: return@withTransaction
        if (row.attempt != attempt || index !in 0 until row.partCount ||
            row.status !in listOf(States.SENDING, States.UNKNOWN)) return@withTransaction
        dao.insertPart(SendPart(id, attempt, index, code))
        val parts = dao.parts(id, attempt)
        if (parts.size == row.partCount) {
            val error = parts.firstOrNull { it.code != Activity.RESULT_OK }
            // A multipart failure may still have transmitted some parts. Do not auto-retry.
            val mixed = error != null && parts.any { it.code == Activity.RESULT_OK }
            dao.resultState(id, if (mixed) States.UNKNOWN else if (error == null) States.SENT else States.FAILED,
                if (mixed) "partial" else error?.let { "sms:${it.code}" } ?: "", System.currentTimeMillis())
        }
    }

    suspend fun contacts(): List<Recipient> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Recipient>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        context.contentResolver.query(uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val normalized = Numbers.normalize(cursor.getString(0) ?: "")
                if (normalized.number.isNotBlank()) {
                    rows += Recipient(normalized.number, cursor.getString(1) ?: "", normalized.valid)
                }
            }
        }
        Numbers.deduplicate(rows)
    }

    suspend fun withContactNames(rows: List<Recipient>): List<Recipient> {
        val names = contacts().associate { it.number to it.name }
        return rows.map { it.copy(name = names[it.number] ?: it.name) }
    }

    suspend fun export(id: Long): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "campaign-$id.csv")
        fun cell(value: String): String {
            // Quote all fields and block spreadsheet formula execution (including +98 numbers).
            val dangerous = value.firstOrNull()?.let { it in "=+-@\t\r" } == true
            val safe = if (dangerous) "'$value" else value
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFFnumber,status,timestamp\r\n")
            val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            dao.resultsNow(id).forEach {
                val timestamp = if (it.timestamp > 0) utc.format(Date(it.timestamp)) else ""
                writer.write(listOf(it.number, it.status, timestamp).joinToString(",") { v -> cell(v) } + "\r\n")
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
