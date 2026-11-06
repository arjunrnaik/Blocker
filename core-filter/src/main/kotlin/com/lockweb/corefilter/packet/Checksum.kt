package com.lockweb.corefilter.packet

/**
 * Internet checksum calculation according to RFC 1071, RFC 768, and RFC 2460.
 */
object Checksum {

    /**
     * Calculates the 16-bit one's complement sum of the given buffer slice.
     */
    fun computeChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length

        while (i < end - 1) {
            val high = buffer[i].toInt() and 0xFF
            val low = buffer[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low
            sum += word
            i += 2
        }

        // Odd byte handling
        if (i < end) {
            val high = buffer[i].toInt() and 0xFF
            sum += (high shl 8)
        }

        // Fold 32-bit sum into 16 bits
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF).toInt()
    }

    /**
     * Computes the UDP checksum for an IPv4 packet including the IPv4 pseudo-header.
     * RFC 768: If the computed checksum is zero, it must be set to 0xFFFF.
     */
    fun computeUdpIpv4Checksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L

        // IPv4 Pseudo Header:
        // Source IP (4 bytes)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        // Destination IP (4 bytes)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        // Zero (1 byte) + Protocol UDP (17 = 0x11)
        sum += 17
        // UDP Length (2 bytes)
        sum += udpLength

        // UDP Header and Data
        var i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            val high = udpPacket[i].toInt() and 0xFF
            val low = udpPacket[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }

        if (i < end) {
            sum += (udpPacket[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val checksum = (sum.inv() and 0xFFFF).toInt()
        return if (checksum == 0) 0xFFFF else checksum
    }

    /**
     * Computes the UDP checksum for an IPv6 packet including the IPv6 pseudo-header.
     * RFC 2460: The UDP checksum is mandatory for IPv6. If zero, set to 0xFFFF.
     */
    fun computeUdpIpv6Checksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L

        // IPv6 Pseudo Header:
        // Source Address (16 bytes = 8 16-bit words)
        for (j in 0 until 16 step 2) {
            sum += ((srcIp[j].toInt() and 0xFF) shl 8) or (srcIp[j + 1].toInt() and 0xFF)
        }
        // Destination Address (16 bytes = 8 16-bit words)
        for (j in 0 until 16 step 2) {
            sum += ((dstIp[j].toInt() and 0xFF) shl 8) or (dstIp[j + 1].toInt() and 0xFF)
        }
        // Upper-Layer Packet Length (32 bits = 2 16-bit words)
        sum += (udpLength shr 16) and 0xFFFF
        sum += udpLength and 0xFFFF
        // Next Header: 17 (32-bit field in pseudo-header = 2 16-bit words)
        sum += 17

        // UDP Header and Data
        var i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            val high = udpPacket[i].toInt() and 0xFF
            val low = udpPacket[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }

        if (i < end) {
            sum += (udpPacket[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val checksum = (sum.inv() and 0xFFFF).toInt()
        return if (checksum == 0) 0xFFFF else checksum
    }
}
