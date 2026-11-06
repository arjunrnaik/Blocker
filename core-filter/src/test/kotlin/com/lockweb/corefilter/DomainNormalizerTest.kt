package com.lockweb.corefilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainNormalizerTest {

    @Test
    fun parseInput_bareDomain_success() {
        val result = DomainNormalizer.parseInput("reddit.com")
        assertTrue(result is ParseResult.Success)
        assertEquals("reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_wwwForm_success() {
        val result = DomainNormalizer.parseInput("www.reddit.com")
        assertTrue(result is ParseResult.Success)
        assertEquals("www.reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_fullUrlWithHttp_extractsHostOnly() {
        val result = DomainNormalizer.parseInput("http://reddit.com/r/technology?sort=new#top")
        assertTrue(result is ParseResult.Success)
        assertEquals("reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_fullUrlWithHttpsAndPortAndUserInfo_extractsHostOnly() {
        val result = DomainNormalizer.parseInput("https://user:password@reddit.com:8080/some/path?query=val#ref")
        assertTrue(result is ParseResult.Success)
        assertEquals("reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_urlWithoutScheme_extractsHostOnly() {
        val result = DomainNormalizer.parseInput("reddit.com/some/path?query=123")
        assertTrue(result is ParseResult.Success)
        assertEquals("reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_uppercaseAndTrailingDot_normalized() {
        val result = DomainNormalizer.parseInput("REDDIT.COM.")
        assertTrue(result is ParseResult.Success)
        assertEquals("reddit.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_leadingAndTrailingWhitespace_trimmed() {
        val result = DomainNormalizer.parseInput("   instagram.com   ")
        assertTrue(result is ParseResult.Success)
        assertEquals("instagram.com", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_unicodeDomain_convertsToPunycode() {
        // German umlaut: münchen.de -> xn--mnchen-3ya.de
        val result = DomainNormalizer.parseInput("münchen.de")
        assertTrue(result is ParseResult.Success)
        assertEquals("xn--mnchen-3ya.de", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_punycodeDomain_acceptedDirectly() {
        val result = DomainNormalizer.parseInput("xn--mnchen-3ya.de")
        assertTrue(result is ParseResult.Success)
        assertEquals("xn--mnchen-3ya.de", (result as ParseResult.Success).normalizedHost)
    }

    @Test
    fun parseInput_emptyInput_rejected() {
        val result = DomainNormalizer.parseInput("   ")
        assertTrue(result is ParseResult.Failure)
        assertEquals(ErrorType.EMPTY_INPUT, (result as ParseResult.Failure).errorType)
    }

    @Test
    fun parseInput_ipv4Literal_rejectedWithSpecificMessage() {
        val inputs = listOf("192.168.1.1", "1.1.1.1", "http://8.8.8.8:8080/test", "127.0.0.1")
        for (input in inputs) {
            val result = DomainNormalizer.parseInput(input)
            assertTrue("Expected rejection for $input", result is ParseResult.Failure)
            val failure = result as ParseResult.Failure
            assertEquals(ErrorType.IP_LITERAL, failure.errorType)
            assertTrue(failure.reason.contains("DNS blocking cannot filter direct IP connections"))
        }
    }

    @Test
    fun parseInput_ipv6Literal_rejectedWithSpecificMessage() {
        val inputs = listOf("[2001:db8::1]", "2001:db8::1", "::1", "http://[::1]:8080")
        for (input in inputs) {
            val result = DomainNormalizer.parseInput(input)
            assertTrue("Expected rejection for IPv6 $input", result is ParseResult.Failure)
            val failure = result as ParseResult.Failure
            assertEquals(ErrorType.IP_LITERAL, failure.errorType)
            assertTrue(failure.reason.contains("DNS blocking cannot filter direct IP connections"))
        }
    }

    @Test
    fun parseInput_singleLabelName_rejectedWithSpecificMessage() {
        val inputs = listOf("localhost", "myrouter", "test", "server")
        for (input in inputs) {
            val result = DomainNormalizer.parseInput(input)
            assertTrue("Expected rejection for single-label $input", result is ParseResult.Failure)
            val failure = result as ParseResult.Failure
            assertEquals(ErrorType.SINGLE_LABEL, failure.errorType)
            assertTrue(failure.reason.contains("Single-label names cannot be blocked as public domains"))
        }
    }

    @Test
    fun parseInput_barePublicSuffix_rejectedWithSpecificMessage() {
        val inputs = listOf("com", "org", "net", "co.uk", "com.au", "co.in", "in", "io", "gov.in")
        for (input in inputs) {
            val result = DomainNormalizer.parseInput(input)
            assertTrue("Expected rejection for public suffix $input", result is ParseResult.Failure)
            val failure = result as ParseResult.Failure
            assertEquals(ErrorType.PUBLIC_SUFFIX, failure.errorType)
            assertTrue(failure.reason.contains("Cannot block an entire top-level or public suffix"))
        }
    }

    @Test
    fun parseInput_emptyLabelConsecutiveDots_rejected() {
        val result = DomainNormalizer.parseInput("reddit..com")
        assertTrue(result is ParseResult.Failure)
        assertEquals(ErrorType.EMPTY_LABEL, (result as ParseResult.Failure).errorType)
    }

    @Test
    fun parseInput_labelStartingOrEndingWithHyphen_rejected() {
        val result1 = DomainNormalizer.parseInput("-reddit.com")
        assertTrue(result1 is ParseResult.Failure)
        assertEquals(ErrorType.INVALID_HYPHEN, (result1 as ParseResult.Failure).errorType)

        val result2 = DomainNormalizer.parseInput("reddit-.com")
        assertTrue(result2 is ParseResult.Failure)
        assertEquals(ErrorType.INVALID_HYPHEN, (result2 as ParseResult.Failure).errorType)
    }

    @Test
    fun parseInput_invalidCharacters_rejected() {
        val result = DomainNormalizer.parseInput("reddit_test.com")
        assertTrue(result is ParseResult.Failure)
        assertEquals(ErrorType.INVALID_CHARACTERS, (result as ParseResult.Failure).errorType)
    }

    @Test
    fun parseInput_oversizeLabel_rejected() {
        val longLabel = "a".repeat(64)
        val result = DomainNormalizer.parseInput("$longLabel.com")
        assertTrue(result is ParseResult.Failure)
        assertEquals(ErrorType.INVALID_LENGTH, (result as ParseResult.Failure).errorType)
    }

    @Test
    fun parseInput_oversizeTotalDomain_rejected() {
        // 5 labels of 55 chars + 5 dots + "com" = 283 chars > 253 max
        val longDomain = "a".repeat(55) + "." + "b".repeat(55) + "." + "c".repeat(55) + "." + "d".repeat(55) + "." + "e".repeat(55) + ".com"
        assertTrue(longDomain.length > 253)
        val result = DomainNormalizer.parseInput(longDomain)
        assertTrue(result is ParseResult.Failure)
        assertEquals(ErrorType.INVALID_LENGTH, (result as ParseResult.Failure).errorType)
    }
}
