package com.lockweb.app.blocklist

import com.lockweb.app.data.BlockRuleEntity
import com.lockweb.corefilter.RuleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImportExportTest {

    @Test
    fun exportAndImport_roundtripSuccess() {
        val rules = listOf(
            BlockRuleEntity(id = 1, normalizedHost = "reddit.com", mode = RuleMode.SUBDOMAINS.name, note = "Distracting"),
            BlockRuleEntity(id = 2, normalizedHost = "twitter.com", mode = RuleMode.EXACT.name, note = null)
        )

        val json = RuleImportExport.exportToJson(rules)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("reddit.com"))
        assertTrue(json.contains("twitter.com"))

        val importResult = RuleImportExport.importFromJson(json)
        assertTrue(importResult is ImportResult.Success)
        val success = importResult as ImportResult.Success
        assertEquals(2, success.rulesToAdd.size)
        assertEquals(0, success.skippedCount)
        assertEquals("reddit.com", success.rulesToAdd[0].normalizedHost)
        assertEquals(RuleMode.SUBDOMAINS.name, success.rulesToAdd[0].mode)
    }

    @Test
    fun import_rejectsOversizedPayload() {
        val hugeBuilder = StringBuilder("{\"schemaVersion\":1,\"rules\":[")
        while (hugeBuilder.length <= RuleImportExport.MAX_IMPORT_SIZE_BYTES + 10) {
            hugeBuilder.append("{\"domain\":\"test${hugeBuilder.length}.com\",\"mode\":\"SUBDOMAINS\"},")
        }
        hugeBuilder.append("{\"domain\":\"final.com\",\"mode\":\"SUBDOMAINS\"}]}")

        val result = RuleImportExport.importFromJson(hugeBuilder.toString())
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).reason.contains("exceeds maximum size"))
    }

    @Test
    fun import_rejectsInvalidSchemaVersion() {
        val badSchema = """{"schemaVersion": 99, "rules": []}"""
        val result = RuleImportExport.importFromJson(badSchema)
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).reason.contains("Unsupported schema version"))
    }

    @Test
    fun import_skipsMalformedAndDuplicateDomains() {
        val json = """
            {
                "schemaVersion": 1,
                "rules": [
                    {"domain": "valid.com", "mode": "SUBDOMAINS"},
                    {"domain": "valid.com", "mode": "SUBDOMAINS"},
                    {"domain": "192.168.1.1", "mode": "SUBDOMAINS"},
                    {"domain": "malformed..domain", "mode": "SUBDOMAINS"},
                    {"domain": "another.org", "mode": "EXACT"}
                ]
            }
        """.trimIndent()

        val result = RuleImportExport.importFromJson(json)
        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(2, success.rulesToAdd.size) // valid.com and another.org
        assertEquals(3, success.skippedCount) // 1 duplicate, 1 IP literal, 1 empty label
        assertEquals("valid.com", success.rulesToAdd[0].normalizedHost)
        assertEquals("another.org", success.rulesToAdd[1].normalizedHost)
    }
}
