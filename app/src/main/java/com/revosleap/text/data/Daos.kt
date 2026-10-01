package com.revosleap.text.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query(
        "SELECT g.*, (SELECT COUNT(*) FROM members m WHERE m.groupId = g.id) AS memberCount " +
            "FROM contact_groups g ORDER BY g.name COLLATE NOCASE"
    )
    fun observeAll(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM contact_groups WHERE id = :id")
    fun observe(id: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM contact_groups WHERE id = :id")
    suspend fun get(id: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("UPDATE contact_groups SET lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id")
    fun observe(groupId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id")
    suspend fun getAll(groupId: Long): List<MemberEntity>

    /** Returns -1 for rows ignored because the number already exists in the group. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(member: MemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface CampaignDao {
    @Insert
    suspend fun insert(campaign: CampaignEntity): Long

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun get(id: Long): CampaignEntity?

    @Query(
        "SELECT c.*, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id) AS total, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id AND r.status = 'SENT') AS sent, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id AND r.status = 'FAILED') AS failed " +
            "FROM campaigns c ORDER BY c.createdAt DESC"
    )
    fun observeAll(): Flow<List<CampaignWithCounts>>

    @Query(
        "SELECT c.*, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id) AS total, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id AND r.status = 'SENT') AS sent, " +
            "(SELECT COUNT(*) FROM send_results r WHERE r.campaignId = c.id AND r.status = 'FAILED') AS failed " +
            "FROM campaigns c WHERE c.id = :id"
    )
    fun observeOne(id: Long): Flow<CampaignWithCounts?>

    @Query("UPDATE campaigns SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE campaigns SET status = 'INTERRUPTED' WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun markInterrupted()

    @Query("DELETE FROM campaigns WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ResultDao {
    @Insert
    suspend fun insertAll(rows: List<SendResultEntity>)

    @Query("SELECT * FROM send_results WHERE campaignId = :campaignId ORDER BY id")
    fun observe(campaignId: Long): Flow<List<SendResultEntity>>

    @Query("SELECT * FROM send_results WHERE campaignId = :campaignId ORDER BY id")
    suspend fun getAll(campaignId: Long): List<SendResultEntity>

    @Query("SELECT * FROM send_results WHERE campaignId = :campaignId AND status = 'PENDING' ORDER BY id LIMIT 1")
    suspend fun nextPending(campaignId: Long): SendResultEntity?

    @Query("SELECT COUNT(*) FROM send_results WHERE campaignId = :campaignId AND status = 'PENDING'")
    suspend fun pendingCount(campaignId: Long): Int

    @Query("SELECT COUNT(*) FROM send_results WHERE campaignId = :campaignId")
    suspend fun totalCount(campaignId: Long): Int

    @Query("SELECT COUNT(*) FROM send_results WHERE campaignId = :campaignId AND status IN ('SENT', 'FAILED')")
    suspend fun doneCount(campaignId: Long): Int

    @Query("UPDATE send_results SET status = 'SENDING', reason = '', updatedAt = :now WHERE id = :id")
    suspend fun markSending(id: Long, now: Long)

    @Query("UPDATE send_results SET status = :status, reason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun markResult(id: Long, status: String, reason: String, now: Long)

    @Query("UPDATE send_results SET status = 'PENDING', reason = '' WHERE campaignId = :campaignId AND status = 'FAILED'")
    suspend fun resetFailed(campaignId: Long)

    @Query("UPDATE send_results SET status = 'PENDING', reason = '' WHERE id = :id AND status = 'FAILED'")
    suspend fun resetOne(id: Long)

    /** Cancel: everything not yet finished becomes failed so it can be retried deliberately. */
    @Query(
        "UPDATE send_results SET status = 'FAILED', reason = :reason, updatedAt = :now " +
            "WHERE campaignId = :campaignId AND status IN ('PENDING', 'SENDING')"
    )
    suspend fun failUnsent(campaignId: Long, reason: String, now: Long)

    /** After a crash/kill: a row stuck in SENDING has an unknown outcome - never assume it was sent. */
    @Query("UPDATE send_results SET status = 'FAILED', reason = :reason WHERE status = 'SENDING'")
    suspend fun failStaleSending(reason: String)
}
