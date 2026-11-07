package com.lockweb.app.vpn

import com.lockweb.corefilter.BlockRule
import com.lockweb.corefilter.DnsDecision
import com.lockweb.corefilter.DnsFilter
import com.lockweb.corefilter.RuleMode
import com.lockweb.corefilter.RuleSet
import com.lockweb.corefilter.dns.DnsCache
import com.lockweb.corefilter.dns.DnsParser
import com.lockweb.corefilter.dns.DnsParseResult
import com.lockweb.corefilter.packet.IpVersion
import com.lockweb.corefilter.packet.PacketCodec
import com.lockweb.corefilter.packet.ParsedUdpPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class VpnSelfTest {

    private fun createSyntheticQuery(domain: String, queryId: Int = 0x1A2B): ByteArray {
        val labels = domain.split(".")
        val qnameOut = ByteArrayOutputStream()
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            qnameOut.write(bytes.size)
            qnameOut.write(bytes)
        }
        qnameOut.write(0)
        val qnameBytes = qnameOut.toByteArray()

        val buffer = ByteArray(12 + qnameBytes.size + 4)
        buffer[0] = ((queryId shr 8) and 0xFF).toByte()
        buffer[1] = (queryId and 0xFF).toByte()
        buffer[2] = 0x01.toByte() // Standard query with RD=1
        buffer[3] = 0x00.toByte()
        buffer[4] = 0x00.toByte()
        buffer[5] = 0x01.toByte() // QDCOUNT = 1

        System.arraycopy(qnameBytes, 0, buffer, 12, qnameBytes.size)
        val offset = 12 + qnameBytes.size
        buffer[offset] = 0x00.toByte()
        buffer[offset + 1] = 0x01.toByte() // QTYPE = A (1)
        buffer[offset + 2] = 0x00.toByte()
        buffer[offset + 3] = 0x01.toByte() // QCLASS = IN (1)

        return buffer
    }

    @Test
    fun syntheticInboundPacket_hasDstPort53_andIsEvaluatedByFilter() {
        val syntheticQuery = createSyntheticQuery("selftest.lockweb.internal")
        val parseResult = DnsParser.parse(syntheticQuery)
        assertTrue(parseResult is DnsParseResult.Success)

        // Note: buildReplyPacket swaps src and dst. To create an inbound packet destined for port 53,
        // we provide srcPort=53 and dstPort=54321 in the template request:
        val syntheticInboundPacket = PacketCodec.buildReplyPacket(
            ParsedUdpPacket(
                version = IpVersion.IPV4,
                srcIp = byteArrayOf(10, 111, 222.toByte(), 1),
                dstIp = byteArrayOf(10, 111, 222.toByte(), 2),
                srcPort = 53,
                dstPort = 54321,
                payload = byteArrayOf(),
                ipHeaderLength = 20
            ),
            syntheticQuery
        )

        val ruleSet = RuleSet.compile(
            userRules = listOf(BlockRule("reddit.com", RuleMode.SUBDOMAINS)),
            blocklistDomains = emptyList()
        ).ruleSet

        val decision = DnsFilter.handlePacket(
            syntheticInboundPacket,
            ruleSet,
            DnsCache(100),
            null
        )

        // selftest.lockweb.internal is not blocked, so decision must be Forward, never Drop
        assertTrue("Decision should be Forward, was $decision", decision is DnsDecision.Forward)
    }

    @Test
    fun blockedDomainQuery_returnsBlockDecision() {
        val redditQuery = createSyntheticQuery("reddit.com")
        val inboundPacket = PacketCodec.buildReplyPacket(
            ParsedUdpPacket(
                version = IpVersion.IPV4,
                srcIp = byteArrayOf(10, 111, 222.toByte(), 1),
                dstIp = byteArrayOf(10, 111, 222.toByte(), 2),
                srcPort = 53,
                dstPort = 54321,
                payload = byteArrayOf(),
                ipHeaderLength = 20
            ),
            redditQuery
        )

        val ruleSet = RuleSet.compile(
            userRules = listOf(BlockRule("reddit.com", RuleMode.SUBDOMAINS)),
            blocklistDomains = emptyList()
        ).ruleSet

        val decision = DnsFilter.handlePacket(
            inboundPacket,
            ruleSet,
            DnsCache(100),
            null
        )

        assertTrue(decision is DnsDecision.Block)
        assertEquals("reddit.com", (decision as DnsDecision.Block).qname)
    }
}
