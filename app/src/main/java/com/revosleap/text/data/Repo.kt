package com.revosleap.text.data

import android.content.Context
import androidx.room.withTransaction
import com.revosleap.text.R
import com.revosleap.text.util.ContactsHelper
import com.revosleap.text.util.PhoneNormalizer
import com.revosleap.text.util.PhoneNumber

data class Recipient(val number: String, val name: String, val valid: Boolean)

data class AddReport(val added: Int, val duplicates: Int, val invalid: Int)

/** An entry to add to a group: an already normalized number plus an optional display name. */
data class NewMember(val phone: PhoneNumber, val name: String = "")

object Repo {

    /**
     * Adds numbers to a group. Numbers already present are ignored (unique index) and counted
     * as duplicates, together with [parseDuplicates] already removed while parsing.
     */
    suspend fun addMembers(
        context: Context,
        groupId: Long,
        entries: List<NewMember>,
        parseDuplicates: Int = 0
    ): AddReport {
        val db = AppDatabase.get(context)
        val names = ContactsHelper.nameMap(context)
        val now = System.currentTimeMillis()
        var added = 0
        var dup = parseDuplicates
        var invalid = 0
        db.withTransaction {
            for (e in entries) {
                if (e.phone.canonical.isEmpty()) continue
                val name = e.name.ifBlank { names[e.phone.canonical].orEmpty() }
                val id = db.memberDao().insertIgnore(
                    MemberEntity(
                        groupId = groupId,
                        number = e.phone.canonical,
                        name = name,
                        valid = e.phone.valid,
                        addedAt = now
                    )
                )
                if (id == -1L) {
                    dup++
                } else {
                    added++
                    if (!e.phone.valid) invalid++
                }
            }
        }
        return AddReport(added, dup, invalid)
    }

    suspend fun addFromText(context: Context, groupId: Long, text: String): AddReport {
        val parsed = PhoneNormalizer.parseBulk(text)
        return addMembers(context, groupId, parsed.numbers.map { NewMember(it) }, parsed.duplicates)
    }

    /** Creates a campaign with one result row per recipient. Invalid numbers are stored as failed. */
    suspend fun createCampaign(
        context: Context,
        groupId: Long?,
        targetName: String,
        message: String,
        recipients: List<Recipient>,
        simSubId: Int,
        delayMs: Long
    ): Long {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val invalidReason = context.getString(R.string.reason_invalid_number)
        return db.withTransaction {
            val campaignId = db.campaignDao().insert(
                CampaignEntity(
                    groupId = groupId,
                    targetName = targetName,
                    message = message,
                    createdAt = now,
                    simSubId = simSubId,
                    delayMs = delayMs,
                    status = CampaignStatus.RUNNING
                )
            )
            val rows = recipients.map { r ->
                SendResultEntity(
                    campaignId = campaignId,
                    number = r.number,
                    name = r.name,
                    body = message.replace("{name}", r.name),
                    status = if (r.valid) ResultStatus.PENDING else ResultStatus.FAILED,
                    reason = if (r.valid) "" else invalidReason,
                    updatedAt = now
                )
            }
            db.resultDao().insertAll(rows)
            if (groupId != null) db.groupDao().touch(groupId, now)
            campaignId
        }
    }

    /** Called at process start: nothing can be running yet, so anything "running" was interrupted. */
    suspend fun recoverInterrupted(context: Context) {
        val db = AppDatabase.get(context)
        db.resultDao().failStaleSending(context.getString(R.string.reason_interrupted))
        db.campaignDao().markInterrupted()
    }
}
