package com.lockweb.corefilter.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsCodecTest {

    private fun createDnsQuery(
        id: Int = 0x1234,
        domain: String = "reddit.com",
        qtype: Int = DnsQType.A,
        recursionDesired: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Header (12 bytes)
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)

        val flagsHigh = if (recursionDesired) 0x01 else 0x00 // RD=1
        out.write(flagsHigh)
        out.write(0x00) // Flags low

        out.write(0x00)
        out.write(0x01) // QDCOUNT = 1

        out.write(0x00); out.write(0x00) // ANCOUNT = 0
        out.write(0x00); out.write(0x00) // NSCOUNT = 0
        out.write(0x00); out.write(0x00) // ARCOUNT = 0

        // 2. QNAME (e.g. reddit.com -> \x06reddit\x03com\x00)
        val labels = domain.split('.')
        for (label in labels) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(0x00) // Zero terminator

        // 3. QTYPE & QCLASS
        out.write((qtype shr 8) and 0xFF)
        out.write(qtype and 0xFF)

        out.write(0x00)
        out.write(0x01) // QCLASS = IN (1)

        return out.toByteArray()
    }

    @Test
    fun parse_validQuery_parsedSuccessfully() {
        val raw = createDnsQuery(id = 0xABCD, domain = "old.reddit.com", qtype = DnsQType.AAAA)
        val result = DnsParser.parse(raw)

        assertTrue(result is DnsParseResult.Success)
        val msg = (result as DnsParseResult.Success).message

        assertEquals(0xABCD, msg.header.id)
        assertFalse(msg.header.isResponse)
        assertTrue(msg.header.recursionDesired)
        assertEquals(1, msg.header.qdCount)

        assertNotNull(msg.question)
        assertEquals("old.reddit.com", msg.question?.qname)
        assertEquals(DnsQType.AAAA, msg.question?.qtype)
        assertEquals(1, msg.question?.qclass)
    }

    @Test
    fun parse_httpsQuery_parsedSuccessfully() {
        val raw = createDnsQuery(id = 0x5678, domain = "instagram.com", qtype = DnsQType.HTTPS)
        val result = DnsParser.parse(raw)

        assertTrue(result is DnsParseResult.Success)
        val msg = (result as DnsParseResult.Success).message
        assertEquals("instagram.com", msg.question?.qname)
        assertEquals(DnsQType.HTTPS, msg.question?.qtype)
    }

    @Test
    fun parse_pointerCompression_decompressedSuccessfully() {
        val out = ByteArrayOutputStream()
        // Header (12 bytes)
        out.write(byteArrayOf(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))

        // First label at offset 12: "com\0"
        val comOffset = 12
        out.write(byteArrayOf(3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0))

        // Second label at offset 17: "reddit" + pointer to offset 12 (com)
        val queryStart = out.size()
        out.write(byteArrayOf(6, 'r'.code.toByte(), 'e'.code.toByte(), 'd'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 't'.code.toByte()))
        out.write(0xC0) // Pointer flag
        out.write(comOffset) // Pointer to "com"

        // QTYPE A + QCLASS IN
        out.write(byteArrayOf(0, 1, 0, 1))

        val bytes = out.toByteArray()
        val parsed = DnsParser.parseQName(bytes, queryStart, bytes.size)

        assertNotNull(parsed)
        assertEquals("reddit.com", parsed?.first)
    }

    @Test
    fun parse_compressionLoop_detectedAndRejected() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))

        // Pointer at offset 12 points to offset 14; pointer at offset 14 points back to offset 12
        out.write(0xC0); out.write(14)
        out.write(0xC0); out.write(12)

        val bytes = out.toByteArray()
        val parsed = DnsParser.parseQName(bytes, 12, bytes.size)
        // Must detect loop and return null
        assertEquals(null, parsed)
    }

    @Test
    fun parse_pointerOutOfBounds_rejectedSafely() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        out.write(0xC0); out.write(250) // Points far beyond buffer size

        val bytes = out.toByteArray()
        val parsed = DnsParser.parseQName(bytes, 12, bytes.size)
        assertEquals(null, parsed)
    }

    @Test
    fun parse_multipleQuestions_rejectedSafely() {
        val raw = createDnsQuery()
        // Set QDCOUNT = 2 in header (bytes 4-5)
        raw[5] = 2

        val result = DnsParser.parse(raw)
        assertTrue(result is DnsParseResult.Error)
        assertTrue((result as DnsParseResult.Error).reason.contains("Multiple DNS questions"))
    }

    @Test
    fun parse_zeroQuestions_handledSafely() {
        val raw = createDnsQuery()
        raw[5] = 0 // QDCOUNT = 0

        val result = DnsParser.parse(raw)
        assertTrue(result is DnsParseResult.Success)
        assertEquals(null, (result as DnsParseResult.Success).message.question)
    }

    @Test
    fun parse_truncatedHeader_rejectedSafely() {
        val raw = byteArrayOf(0x01, 0x02, 0x03)
        val result = DnsParser.parse(raw)
        assertTrue(result is DnsParseResult.Error)
    }

    @Test
    fun buildNxdomain_reparsedByParser_matchesExactExpectedFields() {
        val queryBytes = createDnsQuery(id = 0x7788, domain = "tiktok.com", qtype = DnsQType.A)
        val query = (DnsParser.parse(queryBytes) as DnsParseResult.Success).message

        val nxdomainBytes = DnsBuilder.buildNxdomain(query)
        val parsedResponse = DnsParser.parse(nxdomainBytes)

        assertTrue("NXDOMAIN response must be parseable by DnsParser", parsedResponse is DnsParseResult.Success)
        val msg = (parsedResponse as DnsParseResult.Success).message

        // ID must be echoed
        assertEquals(0x7788, msg.header.id)
        // QR must be 1 (Response)
        assertTrue("Must be a response (QR=1)", msg.header.isResponse)
        // AA must be 1 (Authoritative)
        assertTrue("Must be authoritative (AA=1)", msg.header.isAuthoritative)
        // RA must be 1 (Recursion Available)
        assertTrue("Must have RA=1", msg.header.recursionAvailable)
        // RCODE must be NXDOMAIN (3)
        assertEquals(DnsRCode.NXDOMAIN, msg.header.rcode)
        // Question section must be echoed
        assertEquals("tiktok.com", msg.question?.qname)
        assertEquals(DnsQType.A, msg.question?.qtype)
    }

    @Test
    fun buildServfail_reparsedByParser_hasServfailRcode() {
        val servfailBytes = DnsBuilder.buildServfail(0x9999)
        val parsed = (DnsParser.parse(servfailBytes) as DnsParseResult.Success).message

        assertEquals(0x9999, parsed.header.id)
        assertTrue(parsed.header.isResponse)
        assertEquals(DnsRCode.SERVFAIL, parsed.header.rcode)
    }

    @Test
    fun validateResponse_mismatchedId_rejected() {
        val queryBytes = createDnsQuery(id = 0x1111, domain = "example.com")
        val query = (DnsParser.parse(queryBytes) as DnsParseResult.Success).message

        val responseWithDifferentId = createDnsQuery(id = 0x2222, domain = "example.com")
        // Set QR=1
        responseWithDifferentId[2] = 0x80.toByte()

        assertFalse(DnsBuilder.validateResponse(query, responseWithDifferentId))
    }

    @Test
    fun validateResponse_matchingIdAndQuestion_accepted() {
        val queryBytes = createDnsQuery(id = 0x3333, domain = "example.com")
        val query = (DnsParser.parse(queryBytes) as DnsParseResult.Success).message

        val validResponse = DnsBuilder.buildNxdomain(query)
        assertTrue(DnsBuilder.validateResponse(query, validResponse))
    }
}
