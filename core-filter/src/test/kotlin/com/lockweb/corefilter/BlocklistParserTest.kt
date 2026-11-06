package com.lockweb.corefilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class BlocklistParserTest {

    @Test
    fun parse_domainListAndHostsFile_formatsHandled() {
        val content = """
            # Header comment
            # Another comment line
            
            0.0.0.0 evil-tracker.com
            127.0.0.1 ads.example.net # inline comment
            bare-domain.org
            *.wildcard-site.com
            ::1 localhost-v6.test.com
            
            # Empty and whitespace lines above
               spaced-domain.io   
        """.trimIndent()

        val domains = BlocklistParser.parse(content)

        assertEquals(6, domains.size)
        assertTrue(domains.contains("evil-tracker.com"))
        assertTrue(domains.contains("ads.example.net"))
        assertTrue(domains.contains("bare-domain.org"))
        assertTrue(domains.contains("wildcard-site.com"))
        assertTrue(domains.contains("localhost-v6.test.com"))
        assertTrue(domains.contains("spaced-domain.io"))
    }

    @Test
    fun parse_skipsInvalidAndMalformedEntriesSafely() {
        val content = """
            # Valid domain
            valid-site.com
            
            # Malformed lines
            not a domain at all
            192.168.1.1
            -bad-hyphen.com
            bare-tld-com
            com
            co.uk
            http://
            
            # Another valid
            second-valid.com
        """.trimIndent()

        val domains = BlocklistParser.parse(StringReader(content))

        assertEquals(2, domains.size)
        assertTrue(domains.contains("valid-site.com"))
        assertTrue(domains.contains("second-valid.com"))
    }

    @Test
    fun parse_deduplicatesEntries() {
        val content = """
            0.0.0.0 duplicate.com
            duplicate.com
            DUPLICATE.COM.
            www.duplicate.com
        """.trimIndent()

        val domains = BlocklistParser.parse(content)

        assertEquals(2, domains.size)
        assertTrue(domains.contains("duplicate.com"))
        assertTrue(domains.contains("www.duplicate.com"))
    }
}
