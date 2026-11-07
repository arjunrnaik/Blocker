package com.lockweb.app.blocklist

import com.lockweb.app.data.BlockRuleEntity
import com.lockweb.corefilter.DomainNormalizer
import com.lockweb.corefilter.ParseResult
import com.lockweb.corefilter.RuleMode
import org.json.JSONArray
import org.json.JSONObject

sealed interface ImportResult {
    data class Success(val rulesToAdd: List<BlockRuleEntity>, val skippedCount: Int) : ImportResult
    data class Error(val reason: String) : ImportResult
}

/**
 * Handles secure import and export of USER-ADDED blocking rules in JSON format.
 * In accordance with MASTER_PROMPT.md Section 8:
 * The permanent adult blocklist is NEVER exported or imported.
 */
object RuleImportExport {

    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_IMPORT_SIZE_BYTES = 512 * 1024 // 512 KB
    const val MAX_IMPORT_RULE_COUNT = 5_000

    fun exportToJson(rules: List<BlockRuleEntity>): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("generator", "LockWeb")

        val rulesArray = JSONArray()
        for (rule in rules) {
            val ruleObj = JSONObject()
            ruleObj.put("domain", rule.normalizedHost)
            ruleObj.put("mode", rule.mode)
            if (rule.note != null) {
                ruleObj.put("note", rule.note)
            }
            rulesArray.put(ruleObj)
        }
        root.put("rules", rulesArray)

        return root.toString(2)
    }

    fun importFromJson(jsonString: String): ImportResult {
        val byteSize = jsonString.toByteArray(Charsets.UTF_8).size
        if (byteSize > MAX_IMPORT_SIZE_BYTES) {
            return ImportResult.Error("Import payload exceeds maximum size limit ($byteSize > $MAX_IMPORT_SIZE_BYTES bytes)")
        }

        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            return ImportResult.Error("Invalid JSON payload format")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return ImportResult.Error("Unsupported schema version: $schemaVersion (expected $CURRENT_SCHEMA_VERSION)")
        }

        val rulesArray = root.optJSONArray("rules")
            ?: return ImportResult.Error("Missing 'rules' array in import payload")

        if (rulesArray.length() > MAX_IMPORT_RULE_COUNT) {
            return ImportResult.Error("Too many rules in import file (${rulesArray.length()} > $MAX_IMPORT_RULE_COUNT)")
        }

        val validRules = mutableListOf<BlockRuleEntity>()
        var skippedCount = 0
        val seenHosts = mutableSetOf<String>()

        for (i in 0 until rulesArray.length()) {
            val ruleObj = rulesArray.optJSONObject(i)
            if (ruleObj == null) {
                skippedCount++
                continue
            }

            val domainInput = ruleObj.optString("domain", "").trim()
            if (domainInput.isEmpty()) {
                skippedCount++
                continue
            }

            // Strictly validate and normalize using DomainNormalizer
            val parseResult = DomainNormalizer.parseInput(domainInput)
            val normalizedHost = when (parseResult) {
                is ParseResult.Success -> parseResult.normalizedHost
                else -> {
                    skippedCount++
                    continue
                }
            }

            if (!seenHosts.add(normalizedHost)) {
                // Duplicate within import file
                skippedCount++
                continue
            }

            val modeStr = ruleObj.optString("mode", RuleMode.SUBDOMAINS.name).uppercase()
            val mode = try {
                RuleMode.valueOf(modeStr).name
            } catch (_: Exception) {
                RuleMode.SUBDOMAINS.name
            }

            val note = if (ruleObj.has("note") && !ruleObj.isNull("note")) {
                ruleObj.getString("note").take(100)
            } else {
                null
            }

            validRules.add(
                BlockRuleEntity(
                    normalizedHost = normalizedHost,
                    mode = mode,
                    createdAt = System.currentTimeMillis(),
                    note = note
                )
            )
        }

        return ImportResult.Success(validRules, skippedCount)
    }
}
