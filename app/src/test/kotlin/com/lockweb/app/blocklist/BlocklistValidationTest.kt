package com.lockweb.app.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistValidationTest {

    @Test
    fun emptyDownload_isRejected() {
        val result = BlocklistManager.validateBlocklistContent("", 5000L)
        assertTrue(result is BlocklistValidationResult.Invalid)
        assertEquals("Downloaded content is empty", (result as BlocklistValidationResult.Invalid).reason)
    }

    @Test
    fun truncatedDownload_isRejected() {
        // Current file is 10,000 bytes. Download is 3,000 bytes (< 50% of 10,000)
        val shortContent = "pornhub.com\nxvideos.com\n"
        val currentSize = 10_000L

        val result = BlocklistManager.validateBlocklistContent(shortContent, currentSize)
        assertTrue(result is BlocklistValidationResult.Invalid)
        assertTrue((result as BlocklistValidationResult.Invalid).reason.contains("< 50% of current"))
    }

    @Test
    fun validDownload_isAccepted() {
        val validContent = """
            # OISD NSFW List Update
            pornhub.com
            xvideos.com
            # Comment line
            xnxx.com
            
            chaturbate.com
        """.trimIndent()

        val result = BlocklistManager.validateBlocklistContent(validContent, 50L)
        assertTrue(result is BlocklistValidationResult.Valid)
        val valid = result as BlocklistValidationResult.Valid
        assertEquals(4, valid.domains.size)
        assertTrue(valid.domains.contains("pornhub.com"))
        assertTrue(valid.domains.contains("chaturbate.com"))
    }
}
