package com.farakhvan.text.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object CampaignStatus {
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"
    const val INTERRUPTED = "INTERRUPTED"
}

object ResultStatus {
    const val PENDING = "PENDING"
    const val SENDING = "SENDING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
}

@Entity(tableName = "contact_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "",
    val color: Int = 0,
    val createdAt: Long = 0,
    val lastUsedAt: Long = 0
)

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId", "number"], unique = true)]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val number: String,
    val name: String = "",
    val valid: Boolean = true,
    val addedAt: Long = 0
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long? = null,
    val targetName: String,
    val message: String,
    val createdAt: Long,
    val simSubId: Int = -1,
    val delayMs: Long = 3000,
    val status: String = CampaignStatus.RUNNING
)

@Entity(
    tableName = "send_results",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["campaignId"])]
)
data class SendResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val number: String,
    val name: String = "",
    val body: String,
    val status: String = ResultStatus.PENDING,
    val reason: String = "",
    val updatedAt: Long = 0
)

data class GroupWithCount(
    @Embedded val group: GroupEntity,
    val memberCount: Int
)

data class CampaignWithCounts(
    @Embedded val campaign: CampaignEntity,
    val total: Int,
    val sent: Int,
    val failed: Int
)
