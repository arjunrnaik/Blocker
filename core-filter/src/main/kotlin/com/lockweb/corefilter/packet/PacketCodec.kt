package com.lockweb.corefilter.packet

import java.util.concurrent.atomic.AtomicInteger

/**
 * Encodes and decodes raw IPv4 and IPv6 UDP packets.
 */
object PacketCodec {

    private val ipIdCounter = AtomicInteger(1)

    /**
     * Parses an IPv4 or IPv6 UDP packet from raw bytes read from the TUN interface.
     */
    fun parse(packet: ByteArray, length: Int = packet.size): PacketParseResult {
        if (length < 20) {
            return PacketParseResult.Drop("Packet too short (< 20 bytes)")
        }

        val version = (packet[0].toInt() shr 4) and 0x0F

        return when (version) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> PacketParseResult.Drop("Unsupported IP version: $version")
        }
    }

    private fun parseIpv4(packet: ByteArray, length: Int): PacketParseResult {
        val ihl = packet[0].toInt() and 0x0F
        val ipHeaderLen = ihl * 4
        if (ihl < 5 || length < ipHeaderLen) {
            return PacketParseResult.Drop("Invalid IPv4 IHL: $ihl (length: $length)")
        }

        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (length < totalLength || totalLength < ipHeaderLen + 8) {
            return PacketParseResult.Drop("Invalid IPv4 total length: $totalLength (packet: $length)")
        }

        // Check for fragmentation (Flags & Fragment Offset)
        val flagsAndOffset = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        if ((flagsAndOffset and 0x3FFF) != 0) {
            return PacketParseResult.Drop("Fragmented IP packets are not supported")
        }

        // Protocol check (must be UDP = 17)
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) {
            return PacketParseResult.Drop("Non-UDP protocol: $protocol")
        }

        // Verify IPv4 header checksum
        val checksumCheck = Checksum.computeChecksum(packet, 0, ipHeaderLen)
        if (checksumCheck != 0) {
            return PacketParseResult.Drop("Invalid IPv4 header checksum")
        }

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        // Parse UDP
        return parseUdp(packet, ipHeaderLen, totalLength, IpVersion.IPV4, srcIp, dstIp)
    }

    private fun parseIpv6(packet: ByteArray, length: Int): PacketParseResult {
        val ipHeaderLen = 40
        if (length < ipHeaderLen + 8) {
            return PacketParseResult.Drop("IPv6 packet too short: $length")
        }

        val payloadLength = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        val totalLength = ipHeaderLen + payloadLength
        if (length < totalLength) {
            return PacketParseResult.Drop("IPv6 packet truncated (expected: $totalLength, got: $length)")
        }

        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 17) {
            return PacketParseResult.Drop("Unsupported IPv6 next header: $nextHeader")
        }

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)

        // Parse UDP
        return parseUdp(packet, ipHeaderLen, totalLength, IpVersion.IPV6, srcIp, dstIp)
    }

    private fun parseUdp(
        packet: ByteArray,
        udpOffset: Int,
        totalLength: Int,
        version: IpVersion,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): PacketParseResult {
        if (totalLength - udpOffset < 8) {
            return PacketParseResult.Drop("UDP header truncated")
        }

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)

        if (udpLength < 8 || udpOffset + udpLength > totalLength) {
            return PacketParseResult.Drop("Invalid UDP length field: $udpLength")
        }

        val payload = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)

        return PacketParseResult.Success(
            ParsedUdpPacket(
                version = version,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                payload = payload,
                ipHeaderLength = udpOffset
            )
        )
    }

    /**
     * Builds a complete reply packet (IPv4/IPv6 + UDP) wrapping the provided DNS reply payload.
     * Swaps source and destination IP and port addresses.
     */
    fun buildReplyPacket(request: ParsedUdpPacket, replyPayload: ByteArray): ByteArray {
        return when (request.version) {
            IpVersion.IPV4 -> buildIpv4Reply(request, replyPayload)
            IpVersion.IPV6 -> buildIpv6Reply(request, replyPayload)
        }
    }

    private fun buildIpv4Reply(request: ParsedUdpPacket, replyPayload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + replyPayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        // 1. IPv4 Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        packet[1] = 0x00 // DSCP/ECN
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()

        val id = ipIdCounter.getAndIncrement() and 0xFFFF
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()

        packet[6] = 0x40.toByte() // Don't Fragment flag (0x4000)
        packet[7] = 0x00
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // Protocol UDP

        // Header Checksum at bytes 10-11 initially 0
        packet[10] = 0x00
        packet[11] = 0x00

        // Swap IPs: reply source is request destination, reply destination is request source
        System.arraycopy(request.dstIp, 0, packet, 12, 4)
        System.arraycopy(request.srcIp, 0, packet, 16, 4)

        // Calculate IPv4 Header Checksum
        val ipChecksum = Checksum.computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header
        val udpOffset = ipHeaderLen
        // Swap ports: reply source port is request destination port (53)
        packet[udpOffset] = ((request.dstPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (request.dstPort and 0xFF).toByte()
        packet[udpOffset + 2] = ((request.srcPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (request.srcPort and 0xFF).toByte()
        packet[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        packet[udpOffset + 6] = 0x00 // Checksum placeholder
        packet[udpOffset + 7] = 0x00

        // 3. UDP Payload
        System.arraycopy(replyPayload, 0, packet, udpOffset + 8, replyPayload.size)

        // 4. Calculate UDP Checksum (using reply's srcIp and dstIp)
        val replySrcIp = request.dstIp
        val replyDstIp = request.srcIp
        val udpChecksum = Checksum.computeUdpIpv4Checksum(replySrcIp, replyDstIp, packet, udpOffset, udpLen)
        packet[udpOffset + 6] = ((udpChecksum shr 8) and 0xFF).toByte()
        packet[udpOffset + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    private fun buildIpv6Reply(request: ParsedUdpPacket, replyPayload: ByteArray): ByteArray {
        val ipHeaderLen = 40
        val udpLen = 8 + replyPayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        // 1. IPv6 Header
        packet[0] = 0x60.toByte() // Version 6
        packet[1] = 0x00
        packet[2] = 0x00
        packet[3] = 0x00 // Traffic class & Flow label 0
        packet[4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[5] = (udpLen and 0xFF).toByte()
        packet[6] = 17.toByte() // Next header UDP
        packet[7] = 64.toByte() // Hop limit

        // Swap IPs
        System.arraycopy(request.dstIp, 0, packet, 8, 16)
        System.arraycopy(request.srcIp, 0, packet, 24, 16)

        // 2. UDP Header
        val udpOffset = ipHeaderLen
        packet[udpOffset] = ((request.dstPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (request.dstPort and 0xFF).toByte()
        packet[udpOffset + 2] = ((request.srcPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (request.srcPort and 0xFF).toByte()
        packet[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        packet[udpOffset + 6] = 0x00
        packet[udpOffset + 7] = 0x00

        // 3. UDP Payload
        System.arraycopy(replyPayload, 0, packet, udpOffset + 8, replyPayload.size)

        // 4. Calculate UDP Checksum (mandatory in IPv6)
        val replySrcIp = request.dstIp
        val replyDstIp = request.srcIp
        val udpChecksum = Checksum.computeUdpIpv6Checksum(replySrcIp, replyDstIp, packet, udpOffset, udpLen)
        packet[udpOffset + 6] = ((udpChecksum shr 8) and 0xFF).toByte()
        packet[udpOffset + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }
}
