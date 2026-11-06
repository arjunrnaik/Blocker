package com.lockweb.corefilter.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCodecTest {

    private fun createValidIpv4UdpPacket(
        srcIp: ByteArray = byteArrayOf(10, 0, 0, 2),
        dstIp: ByteArray = byteArrayOf(10, 111, 222.toByte(), 1),
        srcPort: Int = 54321,
        dstPort: Int = 53,
        payload: ByteArray = byteArrayOf(1, 2, 3, 4)
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45.toByte() // IPv4, IHL=5
        packet[1] = 0x00
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0x12.toByte()
        packet[5] = 0x34.toByte() // ID
        packet[6] = 0x40.toByte() // DF flag
        packet[7] = 0x00
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // UDP

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // IP Checksum
        val ipChecksum = Checksum.computeChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        val udpLen = 8 + payload.size
        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort shr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = ((udpLen shr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        System.arraycopy(payload, 0, packet, 28, payload.size)

        val udpChecksum = Checksum.computeUdpIpv4Checksum(srcIp, dstIp, packet, 20, udpLen)
        packet[26] = ((udpChecksum shr 8) and 0xFF).toByte()
        packet[27] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    private fun createValidIpv6UdpPacket(
        srcIp: ByteArray = ByteArray(16) { 0x01 },
        dstIp: ByteArray = ByteArray(16) { 0x02 },
        srcPort: Int = 45678,
        dstPort: Int = 53,
        payload: ByteArray = byteArrayOf(5, 6, 7, 8)
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLength = 40 + udpLen
        val packet = ByteArray(totalLength)

        packet[0] = 0x60.toByte()
        packet[4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[5] = (udpLen and 0xFF).toByte()
        packet[6] = 17.toByte() // Next header UDP
        packet[7] = 64.toByte() // Hop limit

        System.arraycopy(srcIp, 0, packet, 8, 16)
        System.arraycopy(dstIp, 0, packet, 24, 16)

        packet[40] = ((srcPort shr 8) and 0xFF).toByte()
        packet[41] = (srcPort and 0xFF).toByte()
        packet[42] = ((dstPort shr 8) and 0xFF).toByte()
        packet[43] = (dstPort and 0xFF).toByte()
        packet[44] = ((udpLen shr 8) and 0xFF).toByte()
        packet[45] = (udpLen and 0xFF).toByte()

        System.arraycopy(payload, 0, packet, 48, payload.size)

        val udpChecksum = Checksum.computeUdpIpv6Checksum(srcIp, dstIp, packet, 40, udpLen)
        packet[46] = ((udpChecksum shr 8) and 0xFF).toByte()
        packet[47] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    @Test
    fun parse_validIpv4UdpPacket_successfullyParsed() {
        val raw = createValidIpv4UdpPacket()
        val result = PacketCodec.parse(raw)

        assertTrue(result is PacketParseResult.Success)
        val packet = (result as PacketParseResult.Success).packet

        assertEquals(IpVersion.IPV4, packet.version)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), packet.srcIp)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), packet.dstIp)
        assertEquals(54321, packet.srcPort)
        assertEquals(53, packet.dstPort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), packet.payload)
    }

    @Test
    fun parse_validIpv6UdpPacket_successfullyParsed() {
        val raw = createValidIpv6UdpPacket()
        val result = PacketCodec.parse(raw)

        assertTrue(result is PacketParseResult.Success)
        val packet = (result as PacketParseResult.Success).packet

        assertEquals(IpVersion.IPV6, packet.version)
        assertEquals(45678, packet.srcPort)
        assertEquals(53, packet.dstPort)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), packet.payload)
    }

    @Test
    fun buildReplyPacket_ipv4_swapsAddressesAndCalculatesValidChecksums() {
        val raw = createValidIpv4UdpPacket(srcPort = 1111, dstPort = 53)
        val parsed = (PacketCodec.parse(raw) as PacketParseResult.Success).packet

        val replyPayload = byteArrayOf(9, 8, 7, 6, 5)
        val replyPacket = PacketCodec.buildReplyPacket(parsed, replyPayload)

        // Parse the built reply packet to verify structure
        val replyParsedResult = PacketCodec.parse(replyPacket)
        assertTrue(replyParsedResult is PacketParseResult.Success)
        val replyParsed = (replyParsedResult as PacketParseResult.Success).packet

        // Verify swapped IPs and ports
        assertArrayEquals("Reply srcIp must be request dstIp", parsed.dstIp, replyParsed.srcIp)
        assertArrayEquals("Reply dstIp must be request srcIp", parsed.srcIp, replyParsed.dstIp)
        assertEquals(53, replyParsed.srcPort)
        assertEquals(1111, replyParsed.dstPort)
        assertArrayEquals(replyPayload, replyParsed.payload)

        // Verify IPv4 header checksum validity
        assertEquals(0, Checksum.computeChecksum(replyPacket, 0, 20))
    }

    @Test
    fun buildReplyPacket_ipv6_swapsAddressesAndCalculatesValidUdpChecksum() {
        val raw = createValidIpv6UdpPacket(srcPort = 2222, dstPort = 53)
        val parsed = (PacketCodec.parse(raw) as PacketParseResult.Success).packet

        val replyPayload = byteArrayOf(10, 20, 30)
        val replyPacket = PacketCodec.buildReplyPacket(parsed, replyPayload)

        val replyParsedResult = PacketCodec.parse(replyPacket)
        assertTrue(replyParsedResult is PacketParseResult.Success)
        val replyParsed = (replyParsedResult as PacketParseResult.Success).packet

        assertArrayEquals(parsed.dstIp, replyParsed.srcIp)
        assertArrayEquals(parsed.srcIp, replyParsed.dstIp)
        assertEquals(53, replyParsed.srcPort)
        assertEquals(2222, replyParsed.dstPort)
        assertArrayEquals(replyPayload, replyParsed.payload)
    }

    @Test
    fun parse_ipv4Fragment_rejectedSafely() {
        val raw = createValidIpv4UdpPacket()
        // Set More Fragments (MF) flag in byte 6 (0x20)
        raw[6] = 0x20.toByte()

        // Recompute IP checksum
        raw[10] = 0
        raw[11] = 0
        val csum = Checksum.computeChecksum(raw, 0, 20)
        raw[10] = ((csum shr 8) and 0xFF).toByte()
        raw[11] = (csum and 0xFF).toByte()

        val result = PacketCodec.parse(raw)
        assertTrue(result is PacketParseResult.Drop)
        assertTrue((result as PacketParseResult.Drop).reason.contains("Fragmented"))
    }

    @Test
    fun parse_badIpv4Checksum_rejectedSafely() {
        val raw = createValidIpv4UdpPacket()
        // Corrupt checksum byte
        raw[10] = (raw[10].toInt() xor 0xFF).toByte()

        val result = PacketCodec.parse(raw)
        assertTrue(result is PacketParseResult.Drop)
        assertTrue((result as PacketParseResult.Drop).reason.contains("checksum"))
    }

    @Test
    fun parse_nonUdpProtocol_rejectedSafely() {
        val raw = createValidIpv4UdpPacket()
        raw[9] = 6 // TCP instead of UDP (17)
        raw[10] = 0
        raw[11] = 0
        val csum = Checksum.computeChecksum(raw, 0, 20)
        raw[10] = ((csum shr 8) and 0xFF).toByte()
        raw[11] = (csum and 0xFF).toByte()

        val result = PacketCodec.parse(raw)
        assertTrue(result is PacketParseResult.Drop)
        assertTrue((result as PacketParseResult.Drop).reason.contains("Non-UDP"))
    }

    @Test
    fun parse_truncatedPacket_rejectedSafely() {
        val raw = byteArrayOf(0x45, 0x00, 0x00, 0x10) // Only 4 bytes
        val result = PacketCodec.parse(raw)
        assertTrue(result is PacketParseResult.Drop)
        assertTrue((result as PacketParseResult.Drop).reason.contains("short"))
    }
}
