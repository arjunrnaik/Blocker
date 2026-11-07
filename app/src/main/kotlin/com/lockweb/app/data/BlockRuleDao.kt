package com.lockweb.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {

    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    fun getAllRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules ORDER BY created_at DESC")
    suspend fun getAllRulesList(): List<BlockRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<BlockRuleEntity>): List<Long>

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteRule(id: Long): Int

    @Query("DELETE FROM block_rules WHERE normalized_host = :normalizedHost")
    suspend fun deleteRuleByHost(normalizedHost: String): Int

    @Query("SELECT * FROM block_rules WHERE normalized_host = :normalizedHost LIMIT 1")
    suspend fun findByHost(normalizedHost: String): BlockRuleEntity?

    @Query("SELECT COUNT(*) FROM block_rules")
    suspend fun count(): Int
}
