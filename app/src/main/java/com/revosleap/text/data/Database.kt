package com.revosleap.text.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "",
    val color: Long = 0xFF00796B,
    val lastUsed: Long = 0,
    val deleted: Boolean = false
)

@Entity(
    tableName = "members",
    foreignKeys = [ForeignKey(entity = Group::class, parentColumns = ["id"],
        childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["groupId", "number"], unique = true)]
)
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val number: String,
    val name: String = "",
    val valid: Boolean = true
)

@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val target: String,
    val body: String,
    val subscriptionId: Int = -1,
    val delaySeconds: Int = 3,
    val state: String = States.PENDING
)

@Entity(
    tableName = "send_results",
    foreignKeys = [ForeignKey(entity = Campaign::class, parentColumns = ["id"],
        childColumns = ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["campaignId"])]
)
data class SendResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val number: String,
    val name: String,
    val renderedBody: String,
    val valid: Boolean,
    val status: String = States.PENDING,
    val reason: String = "",
    val timestamp: Long = 0,
    val attempt: Long = 0,
    val partCount: Int = 0
)

@Entity(
    tableName = "send_parts",
    primaryKeys = ["resultId", "attempt", "part"],
    foreignKeys = [ForeignKey(entity = SendResult::class, parentColumns = ["id"],
        childColumns = ["resultId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["resultId"])]
)
data class SendPart(val resultId: Long, val attempt: Long, val part: Int, val code: Int)

data class GroupSummary(val id: Long, val name: String, val emoji: String, val color: Long,
    val lastUsed: Long, val count: Int)
data class CampaignSummary(val id: Long, val createdAt: Long, val target: String,
    val body: String, val state: String, val total: Int, val sent: Int, val failed: Int)

object States {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val FAILED = "failed"
    const val PAUSED = "paused"
    const val CANCELLED = "cancelled"
    const val INTERRUPTED = "interrupted"
    const val UNKNOWN = "unknown"
    const val COMPLETE = "complete"
}

@Dao
interface AppDao {
    @Query("""SELECT g.id, g.name, g.emoji, g.color, g.lastUsed, COUNT(m.id) AS count
        FROM groups g LEFT JOIN members m ON m.groupId = g.id WHERE g.deleted = 0
        GROUP BY g.id ORDER BY g.lastUsed DESC, g.name""")
    fun groups(): Flow<List<GroupSummary>>
    @Query("SELECT * FROM groups WHERE id = :id")
    fun group(id: Long): Flow<Group?>
    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY name, number")
    fun members(groupId: Long): Flow<List<Member>>
    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id")
    suspend fun membersNow(groupId: Long): List<Member>
    @Query("SELECT m.* FROM members m JOIN groups g ON g.id=m.groupId WHERE g.deleted=0 ORDER BY m.name")
    suspend fun allMembers(): List<Member>
    @Insert
    suspend fun insertGroup(group: Group): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<Member>): List<Long>
    @Query("UPDATE groups SET name = :name, emoji = :emoji, color = :color WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String, emoji: String, color: Long)
    @Query("UPDATE groups SET deleted = :deleted WHERE id = :id")
    suspend fun hideGroup(id: Long, deleted: Boolean)
    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)
    @Query("DELETE FROM groups WHERE deleted=1")
    suspend fun purgeDeletedGroups()
    @Query("DELETE FROM members WHERE groupId = :groupId AND id IN (:ids)")
    suspend fun deleteMembers(groupId: Long, ids: List<Long>)
    @Query("UPDATE groups SET lastUsed = :time WHERE id = :id")
    suspend fun touchGroup(id: Long, time: Long)
    @Insert
    suspend fun insertCampaign(campaign: Campaign): Long
    @Insert
    suspend fun insertResults(results: List<SendResult>)
    @Query("""SELECT c.id,c.createdAt,c.target,c.body,c.state,COUNT(r.id) AS total,
        COALESCE(SUM(CASE WHEN r.status='sent' THEN 1 ELSE 0 END),0) AS sent,
        COALESCE(SUM(CASE WHEN r.status='failed' THEN 1 ELSE 0 END),0) AS failed
        FROM campaigns c LEFT JOIN send_results r ON r.campaignId=c.id
        GROUP BY c.id ORDER BY c.createdAt DESC""")
    fun campaigns(): Flow<List<CampaignSummary>>
    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun campaign(id: Long): Flow<Campaign?>
    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun campaignNow(id: Long): Campaign?
    @Query("SELECT * FROM campaigns WHERE state IN ('running','paused') ORDER BY id LIMIT 1")
    suspend fun activeCampaign(): Campaign?
    @Query("SELECT * FROM send_results WHERE campaignId = :id ORDER BY id")
    fun results(id: Long): Flow<List<SendResult>>
    @Query("SELECT * FROM send_results WHERE campaignId = :id ORDER BY id")
    suspend fun resultsNow(id: Long): List<SendResult>
    @Query("SELECT * FROM send_results WHERE id = :id")
    suspend fun resultNow(id: Long): SendResult?
    @Query("SELECT * FROM send_results WHERE campaignId=:id AND status='pending' ORDER BY id LIMIT 1")
    suspend fun next(id: Long): SendResult?
    @Query("UPDATE campaigns SET state = :state WHERE id = :id")
    suspend fun campaignState(id: Long, state: String)
    @Query("""UPDATE send_results SET status=:status, reason=:reason, timestamp=:time
        WHERE id=:id""")
    suspend fun resultState(id: Long, status: String, reason: String, time: Long)
    @Query("""UPDATE send_results SET status='unknown',reason=:reason,timestamp=:time
        WHERE id=:id AND status='sending'""")
    suspend fun unknownIfSending(id: Long, reason: String, time: Long)
    @Query("""UPDATE send_results SET status='sending', reason='', timestamp=:time,
        attempt=:attempt,partCount=:parts WHERE id=:id AND status='pending'""")
    suspend fun claim(id: Long, attempt: Long, parts: Int, time: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPart(part: SendPart): Long
    @Query("SELECT * FROM send_parts WHERE resultId=:id AND attempt=:attempt")
    suspend fun parts(id: Long, attempt: Long): List<SendPart>
    @Query("""UPDATE send_results SET status='pending',reason='',timestamp=0
        WHERE campaignId=:campaign AND valid=1 AND status='failed' AND reason NOT IN ('invalid','empty')""")
    suspend fun retryFailed(campaign: Long)
    @Query("""UPDATE send_results SET status='pending',reason='',timestamp=0
        WHERE id=:id AND valid=1 AND status IN ('failed','unknown') AND reason NOT IN ('invalid','empty')""")
    suspend fun retryOne(id: Long)
    @Query("UPDATE campaigns SET state='interrupted' WHERE state IN ('running','paused','pending')")
    suspend fun interruptCampaigns()
    @Query("""UPDATE send_results SET status='unknown',reason='uncertain'
        WHERE status='sending'""")
    suspend fun markInFlightUnknown()
}

@Database(entities = [Group::class, Member::class, Campaign::class, SendResult::class, SendPart::class],
    version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
