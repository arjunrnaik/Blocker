package com.lockweb.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lockweb.corefilter.BlockRule
import com.lockweb.corefilter.RuleMode

/**
 * Room entity representing a user-added blocking rule.
 * The adult blocklist is stored in a separate flat file, NOT in Room.
 */
@Entity(
    tableName = "block_rules",
    indices = [
        Index(value = ["normalized_host"], unique = true)
    ]
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "normalized_host")
    val normalizedHost: String,

    @ColumnInfo(name = "mode")
    val mode: String = RuleMode.SUBDOMAINS.name,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "note")
    val note: String? = null
) {
    /**
     * Converts Room entity to core-filter domain model.
     */
    fun toBlockRule(): BlockRule {
        val ruleMode = try {
            RuleMode.valueOf(mode)
        } catch (_: Exception) {
            RuleMode.SUBDOMAINS
        }
        return BlockRule(domain = normalizedHost, mode = ruleMode)
    }
}
