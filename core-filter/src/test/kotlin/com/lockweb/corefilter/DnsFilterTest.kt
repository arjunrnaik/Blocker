package com.lockweb.corefilter

import com.lockweb.corefilter.dns.DnsCache
import com.lockweb.corefilter.dns.DnsParser
import com.lockweb.corefilter.dns.DnsParseResult
import com.lockweb.corefilter.dns.DnsQType
import com.lockweb.corefilter.dns.DnsRCode
import com.lockweb.corefilter.packet.Checksum
import com.lockweb.corefilter.packet.PacketCodec
import com.lockweb.corefilter.packet.PacketParseResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsFilterTest {

    private fun createDnsQueryPacket(
        domain: String,
        qtype: Int = DnsQType.A,
        queryId: Int = 0x4321,
        srcIp: ByteArray = byteArrayOf(10, 0, 0, 2),
        dstIp: ByteArray = byteArrayOf(10, 111, 222.toByte(), 1),
        srcPort: Int = 50000,
        dstPort: Int = 53
    ): ByteArray {
        // Build DNS payload
        val dnsOut = ByteArrayOutputStream()
        dnsOut.write((queryId shr 8) and 0xFF)
        dnsOut.write(queryId and 0xFF)
        dnsOut.write(0x01); dnsOut.write(0x00) // Flags: RD=1
        dnsOut.write(0x00); dnsOut.write(0x01) // QDCOUNT = 1
        dnsOut.write(0x00); dnsOut.write(0x00)
        dnsOut.write(0x00); dnsOut.write(0x00)
        dnsOut.write(0x00); dnsOut.write(0x00)

        for (label in domain.split('.')) {
            dnsOut.write(label.length)
            dnsOut.write(label.toByteArray())
        }
        dnsOut.write(0x00)

        dnsOut.write((qtype shr 8) and 0xFF)
        dnsOut.write(qtype and 0xFF)
        dnsOut.write(0x00); dnsOut.write(0x01) // IN

        val dnsPayload = dnsOut.toByteArray()

        // Build IPv4 + UDP packet
        val udpLen = 8 + dnsPayload.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)

        packet[0] = 0x45.toByte()
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[8] = 64.toByte()
        packet[9] = 17.toByte() // UDP

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipCsum = Checksum.computeChecksum(packet, 0, 20)
        packet[10] = ((ipCsum shr 8) and 0xFF).toByte()
        packet[11] = (ipCsum and 0xFF).toByte()

        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort shr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = ((udpLen shr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)

        val udpCsum = Checksum.computeUdpIpv4Checksum(srcIp, dstIp, packet, 20, udpLen)
        packet[26] = ((udpCsum shr 8) and 0xFF).toByte()
        packet[27] = (udpCsum and 0xFF).toByte()

        return packet
    }

    @Test
    fun handlePacket_blockedDomain_returnsBlockWithValidNxdomainPacket() {
        val rules = listOf(
            BlockRule("reddit.com", RuleMode.SUBDOMAINS)
        )
        val ruleSet = RuleSet.compile(rules).ruleSet
        val counter = AtomicBlockCounter()

        // 1. A record query for blocked domain
        val packetA = createDnsQueryPacket("www.reddit.com", qtype = DnsQType.A, queryId = 0x1111)
        val decisionA = DnsFilter.handlePacket(packetA, ruleSet, counter = counter)

        assertTrue(decisionA is DnsDecision.Block)
        val blockA = decisionA as DnsDecision.Block
        assertEquals("www.reddit.com", blockA.qname)
        assertEquals(DnsQType.A, blockA.qtype)
        assertEquals(1, counter.getCount())

        // Verify the reply packet structure
        val parsedReply = (PacketCodec.parse(blockA.replyPacket) as PacketParseResult.Success).packet
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), parsedReply.srcIp)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), parsedReply.dstIp)
        assertEquals(53, parsedReply.srcPort)
        assertEquals(50000, parsedReply.dstPort)

        // Verify DNS payload in the reply packet is NXDOMAIN
        val parsedDns = (DnsParser.parse(parsedReply.payload) as DnsParseResult.Success).message
        assertEquals(0x1111, parsedDns.header.id)
        assertTrue(parsedDns.header.isResponse)
        assertEquals(DnsRCode.NXDOMAIN, parsedDns.header.rcode)
        assertEquals("www.reddit.com", parsedDns.question?.qname)

        // 2. AAAA query for blocked domain
        val packetAaaa = createDnsQueryPacket("old.reddit.com", qtype = DnsQType.AAAA)
        val decisionAaaa = DnsFilter.handlePacket(packetAaaa, ruleSet, counter = counter)
        assertTrue(decisionAaaa is DnsDecision.Block)
        assertEquals(2, counter.getCount())

        // 3. HTTPS query for blocked domain
        val packetHttps = createDnsQueryPacket("reddit.com", qtype = DnsQType.HTTPS)
        val decisionHttps = DnsFilter.handlePacket(packetHttps, ruleSet, counter = counter)
        assertTrue(decisionHttps is DnsDecision.Block)
        assertEquals(3, counter.getCount())
    }

    @Test
    fun handlePacket_allowedDomain_returnsForward() {
        val rules = listOf(
            BlockRule("reddit.com", RuleMode.SUBDOMAINS)
        )
        val ruleSet = RuleSet.compile(rules).ruleSet
        val counter = AtomicBlockCounter()

        val allowedPacket = createDnsQueryPacket("wikipedia.org", qtype = DnsQType.A)
        val decision = DnsFilter.handlePacket(allowedPacket, ruleSet, counter = counter)

        assertTrue(decision is DnsDecision.Forward)
        val forward = decision as DnsDecision.Forward
        assertEquals(50000, forward.requestPacket.srcPort)
        assertEquals(53, forward.requestPacket.dstPort)
        // Counter must NOT increment on allowed forward
        assertEquals(0, counter.getCount())
    }

    @Test
    fun handlePacket_cachedAllowedDomain_returnsCacheHit() {
        val ruleSet = RuleSet.EMPTY
        val cache = DnsCache()

        // Seed cache with Wikipedia response
        val fakeResponse = ByteArray(32) { 0x55.toByte() }
        fakeResponse[2] = 0x80.toByte() // Response
        cache.put("wikipedia.org", DnsQType.A, 1, fakeResponse, ttlSeconds = 60)

        val packet = createDnsQueryPacket("wikipedia.org", qtype = DnsQType.A, queryId = 0x7777)
        val decision = DnsFilter.handlePacket(packet, ruleSet, cache = cache)

        assertTrue(decision is DnsDecision.CacheHit)
        val hit = decision as DnsDecision.CacheHit
        assertEquals("wikipedia.org", hit.qname)

        // Verify reply packet has the current query's ID (0x7777)
        val parsedReply = (PacketCodec.parse(hit.replyPacket) as PacketParseResult.Success).packet
        val replyId = ((parsedReply.payload[0].toInt() and 0xFF) shl 8) or (parsedReply.payload[1].toInt() and 0xFF)
        assertEquals(0x7777, replyId)
    }

    @Test
    fun handlePacket_nonPort53_returnsDrop() {
        val ruleSet = RuleSet.EMPTY
        val packet = createDnsQueryPacket("example.com", dstPort = 8080)
        val decision = DnsFilter.handlePacket(packet, ruleSet)

        assertTrue(decision is DnsDecision.Drop)
        assertTrue((decision as DnsDecision.Drop).reason.contains("Destination port"))
    }
}
