package com.lockweb.corefilter

import com.lockweb.corefilter.dns.DnsBuilder
import com.lockweb.corefilter.dns.DnsCache
import com.lockweb.corefilter.dns.DnsParseResult
import com.lockweb.corefilter.dns.DnsParser
import com.lockweb.corefilter.packet.PacketCodec
import com.lockweb.corefilter.packet.PacketParseResult
import com.lockweb.corefilter.packet.ParsedUdpPacket

/**
 * Outcome of evaluating an inbound network packet.
 */
sealed interface DnsDecision {
    /**
     * The query is blocked by filter rules. Contains the complete IPv4/IPv6+UDP reply packet (NXDOMAIN).
     */
    data class Block(
        val replyPacket: ByteArray,
        val qname: String,
        val qtype: Int
    ) : DnsDecision {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Block
            return replyPacket.contentEquals(other.replyPacket) && qname == other.qname && qtype == other.qtype
        }

        override fun hashCode(): Int {
            var result = replyPacket.contentHashCode()
            result = 31 * result + qname.hashCode()
            result = 31 * result + qtype
            return result
        }
    }

    /**
     * The query is allowed and must be forwarded to the upstream resolver.
     */
    data class Forward(
        val rawDnsPayload: ByteArray,
        val requestPacket: ParsedUdpPacket
    ) : DnsDecision {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Forward
            return rawDnsPayload.contentEquals(other.rawDnsPayload) && requestPacket == other.requestPacket
        }

        override fun hashCode(): Int {
            var result = rawDnsPayload.contentHashCode()
            result = 31 * result + requestPacket.hashCode()
            return result
        }
    }

    /**
     * The query was answered directly from local DNS cache.
     */
    data class CacheHit(
        val replyPacket: ByteArray,
        val qname: String
    ) : DnsDecision {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CacheHit
            return replyPacket.contentEquals(other.replyPacket) && qname == other.qname
        }

        override fun hashCode(): Int {
            var result = replyPacket.contentHashCode()
            result = 31 * result + qname.hashCode()
            return result
        }
    }

    /**
     * Packet was dropped (e.g. non-UDP, port not 53, malformed DNS, fragment).
     */
    data class Drop(val reason: String) : DnsDecision
}

/**
 * Pure function engine for evaluating DNS traffic without side effects or Android I/O dependencies.
 */
object DnsFilter {

    /**
     * Handles an inbound raw IP packet from the TUN interface.
     *
     * @param packetBytes Complete IP packet bytes.
     * @param ruleSet Active compiled filter rules.
     * @param cache Optional in-memory DNS cache.
     * @param counter Optional anonymous block counter.
     * @return [DnsDecision] indicating whether to Block (NXDOMAIN), Forward, CacheHit, or Drop.
     */
    fun handlePacket(
        packetBytes: ByteArray,
        ruleSet: RuleSet,
        cache: DnsCache? = null,
        counter: BlockCounter? = null
    ): DnsDecision {
        // 1. Decode IP and UDP
        val packet = when (val parseResult = PacketCodec.parse(packetBytes)) {
            is PacketParseResult.Success -> parseResult.packet
            is PacketParseResult.Drop -> return DnsDecision.Drop(parseResult.reason)
        }

        // 2. Ensure destination port is DNS (port 53)
        if (packet.dstPort != 53) {
            return DnsDecision.Drop("Destination port is not 53 (was ${packet.dstPort})")
        }

        // 3. Decode DNS message
        val dnsMessage = when (val dnsResult = DnsParser.parse(packet.payload)) {
            is DnsParseResult.Success -> dnsResult.message
            is DnsParseResult.Error -> return DnsDecision.Drop(dnsResult.reason)
        }

        // Reject if inbound packet claims to be a response
        if (dnsMessage.header.isResponse) {
            return DnsDecision.Drop("Received DNS response packet as inbound query")
        }

        val question = dnsMessage.question
            ?: return DnsDecision.Drop("DNS query missing question section")

        val qname = question.qname

        // 4. Check if blocked by rules
        if (ruleSet.matches(qname)) {
            counter?.increment()

            val nxdomainDns = DnsBuilder.buildNxdomain(dnsMessage)
            val replyPacket = PacketCodec.buildReplyPacket(packet, nxdomainDns)

            return DnsDecision.Block(
                replyPacket = replyPacket,
                qname = qname,
                qtype = question.qtype
            )
        }

        // 5. Check cache for allowed queries
        if (cache != null) {
            val cachedDns = cache.get(qname, question.qtype, question.qclass)
            if (cachedDns != null && cachedDns.size >= 12) {
                val replyDns = cachedDns.copyOf()
                // Adjust cached transaction ID to match current query ID
                replyDns[0] = ((dnsMessage.header.id shr 8) and 0xFF).toByte()
                replyDns[1] = (dnsMessage.header.id and 0xFF).toByte()

                val replyPacket = PacketCodec.buildReplyPacket(packet, replyDns)
                return DnsDecision.CacheHit(replyPacket, qname)
            }
        }

        // 6. Forward allowed query
        return DnsDecision.Forward(
            rawDnsPayload = packet.payload,
            requestPacket = packet
        )
    }
}
