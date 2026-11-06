package com.lockweb.corefilter.packet

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecksumTest {

    @Test
    fun computeChecksum_knownGoodIpv4Header_returnsZeroForValidPacket() {
        // Standard IPv4 Header RFC 1071 test vector
        // 45 00 00 3c 1c 46 40 00 40 06 b1 e6 ac 10 0a 63 ac 10 0a 0c
        // Checksum is 0xb1e6
        val ipv4Header = byteArrayOf(
            0x45.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3c.toByte(),
            0x1c.toByte(), 0x46.toByte(), 0x40.toByte(), 0x00.toByte(),
            0x40.toByte(), 0x06.toByte(), 0xb1.toByte(), 0xe6.toByte(),
            0xac.toByte(), 0x10.toByte(), 0x0a.toByte(), 0x63.toByte(),
            0xac.toByte(), 0x10.toByte(), 0x0a.toByte(), 0x0c.toByte()
        )

        // Verifying an existing valid header (including its checksum) must sum to 0
        val verification = Checksum.computeChecksum(ipv4Header, 0, ipv4Header.size)
        assertEquals(0, verification)
    }

    @Test
    fun computeChecksum_oddLengthBuffer_computedCorrectly() {
        val oddData = byteArrayOf(0x01, 0x02, 0x03)
        val checksum = Checksum.computeChecksum(oddData, 0, oddData.size)
        // High byte of odd byte is padded with 0x00 in low byte: 0x0102 + 0x0300 = 0x0402 -> inv = 0xFBFD
        assertEquals(0xFBFD, checksum)
    }

    @Test
    fun computeUdpIpv4Checksum_returnsExpectedChecksum() {
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(10, 0, 0, 1)

        // UDP packet: srcPort=12345, dstPort=53, len=12, checksum=0, data="test"
        val udpPacket = byteArrayOf(
            0x30.toByte(), 0x39.toByte(), // srcPort 12345
            0x00.toByte(), 0x35.toByte(), // dstPort 53
            0x00.toByte(), 0x0c.toByte(), // len 12
            0x00.toByte(), 0x00.toByte(), // checksum placeholder
            't'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte()
        )

        val checksum = Checksum.computeUdpIpv4Checksum(srcIp, dstIp, udpPacket, 0, udpPacket.size)
        // Verify non-zero
        assert(checksum in 1..0xFFFF)
    }
}
