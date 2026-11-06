package com.lockweb.corefilter.packet

enum class IpVersion {
    IPV4,
    IPV6
}

/**
 * Represents a successfully decoded IP/UDP packet.
 */
data class ParsedUdpPacket(
    val version: IpVersion,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
    val ipHeaderLength: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedUdpPacket

        if (version != other.version) return false
        if (!srcIp.contentEquals(other.srcIp)) return false
        if (!dstIp.contentEquals(other.dstIp)) return false
        if (srcPort != other.srcPort) return false
        if (dstPort != other.dstPort) return false
        if (!payload.contentEquals(other.payload)) return false
        if (ipHeaderLength != other.ipHeaderLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + srcPort
        result = 31 * result + dstPort
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + ipHeaderLength
        return result
    }
}

sealed interface PacketParseResult {
    data class Success(val packet: ParsedUdpPacket) : PacketParseResult
    data class Drop(val reason: String) : PacketParseResult
}
