package com.lockweb.corefilter.dns

/**
 * Parsed DNS Question section (RFC 1035 Section 4.1.2).
 */
data class DnsQuestion(
    val qname: String,
    val qtype: Int,
    val qclass: Int,
    val rawQuestionBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsQuestion
        if (qname != other.qname) return false
        if (qtype != other.qtype) return false
        if (qclass != other.qclass) return false
        if (!rawQuestionBytes.contentEquals(other.rawQuestionBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qname.hashCode()
        result = 31 * result + qtype
        result = 31 * result + qclass
        result = 31 * result + rawQuestionBytes.contentHashCode()
        return result
    }
}

/**
 * 12-byte DNS Message Header (RFC 1035 Section 4.1.1).
 */
data class DnsHeader(
    val id: Int,
    val isResponse: Boolean,
    val opcode: Int,
    val isAuthoritative: Boolean,
    val isTruncated: Boolean,
    val recursionDesired: Boolean,
    val recursionAvailable: Boolean,
    val rcode: Int,
    val qdCount: Int,
    val anCount: Int,
    val nsCount: Int,
    val arCount: Int
)

/**
 * Parsed DNS Message.
 */
data class DnsMessage(
    val header: DnsHeader,
    val question: DnsQuestion?,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsMessage
        if (header != other.header) return false
        if (question != other.question) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + (question?.hashCode() ?: 0)
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

sealed interface DnsParseResult {
    data class Success(val message: DnsMessage) : DnsParseResult
    data class Error(val reason: String) : DnsParseResult
}
