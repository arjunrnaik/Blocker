package com.lockweb.corefilter.dns

import java.util.Locale

/**
 * Fast, secure DNS wire-format parser with loop and bounds protection.
 */
object DnsParser {

    private const val HEADER_SIZE = 12
    private const val MAX_POINTER_JUMPS = 16
    private const val MAX_NAME_LENGTH = 255

    /**
     * Parses a raw DNS message buffer.
     */
    fun parse(bytes: ByteArray, length: Int = bytes.size): DnsParseResult {
        if (length < HEADER_SIZE) {
            return DnsParseResult.Error("DNS message too short (< $HEADER_SIZE bytes)")
        }

        // 1. Header (12 bytes)
        val id = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val flagsHigh = bytes[2].toInt() and 0xFF
        val flagsLow = bytes[3].toInt() and 0xFF

        val isResponse = (flagsHigh and 0x80) != 0
        val opcode = (flagsHigh shr 3) and 0x0F
        val isAuthoritative = (flagsHigh and 0x04) != 0
        val isTruncated = (flagsHigh and 0x02) != 0
        val recursionDesired = (flagsHigh and 0x01) != 0
        val recursionAvailable = (flagsLow and 0x80) != 0
        val rcode = flagsLow and 0x0F

        val qdCount = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val anCount = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        val nsCount = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        val arCount = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)

        val header = DnsHeader(
            id = id,
            isResponse = isResponse,
            opcode = opcode,
            isAuthoritative = isAuthoritative,
            isTruncated = isTruncated,
            recursionDesired = recursionDesired,
            recursionAvailable = recursionAvailable,
            rcode = rcode,
            qdCount = qdCount,
            anCount = anCount,
            nsCount = nsCount,
            arCount = arCount
        )

        // 2. Reject unsupported question counts safely
        if (qdCount == 0) {
            return DnsParseResult.Success(
                DnsMessage(header = header, question = null, rawBytes = bytes.copyOf(length))
            )
        }

        if (qdCount > 1) {
            return DnsParseResult.Error("Multiple DNS questions ($qdCount) are not supported")
        }

        // 3. Parse Question Section
        val questionStartOffset = HEADER_SIZE
        val nameParseResult = parseQName(bytes, questionStartOffset, length)
            ?: return DnsParseResult.Error("Malformed DNS QNAME or compression loop detected")

        val (qname, offsetAfterName) = nameParseResult

        if (offsetAfterName + 4 > length) {
            return DnsParseResult.Error("DNS Question section truncated (missing QTYPE or QCLASS)")
        }

        val qtype = ((bytes[offsetAfterName].toInt() and 0xFF) shl 8) or (bytes[offsetAfterName + 1].toInt() and 0xFF)
        val qclass = ((bytes[offsetAfterName + 2].toInt() and 0xFF) shl 8) or (bytes[offsetAfterName + 3].toInt() and 0xFF)
        val questionEndOffset = offsetAfterName + 4

        val rawQuestionBytes = bytes.copyOfRange(questionStartOffset, questionEndOffset)

        val question = DnsQuestion(
            qname = qname,
            qtype = qtype,
            qclass = qclass,
            rawQuestionBytes = rawQuestionBytes
        )

        return DnsParseResult.Success(
            DnsMessage(
                header = header,
                question = question,
                rawBytes = bytes.copyOf(length)
            )
        )
    }

    /**
     * Parses a QNAME starting at [startOffset] with pointer compression support and loop protection.
     * @return Pair of (normalized domain string, offset after name in the original buffer), or null if malformed.
     */
    fun parseQName(bytes: ByteArray, startOffset: Int, length: Int): Pair<String, Int>? {
        var cursor = startOffset
        val nameBuilder = StringBuilder()
        var jumpsCount = 0
        var offsetAfterName: Int? = null
        val visitedPointers = mutableSetOf<Int>()

        while (true) {
            if (cursor >= length) return null

            val lenByte = bytes[cursor].toInt() and 0xFF

            if (lenByte == 0) {
                // End of name label
                if (offsetAfterName == null) {
                    offsetAfterName = cursor + 1
                }
                break
            }

            // Check if it's a compression pointer (0b11xxxxxx)
            if ((lenByte and 0xC0) == 0xC0) {
                if (cursor + 1 >= length) return null

                val pointer = ((lenByte and 0x3F) shl 8) or (bytes[cursor + 1].toInt() and 0xFF)

                // Loop and bounds protection
                if (pointer >= length || visitedPointers.contains(pointer) || jumpsCount >= MAX_POINTER_JUMPS) {
                    return null // Loop or invalid pointer detected
                }

                visitedPointers.add(pointer)
                jumpsCount++

                if (offsetAfterName == null) {
                    offsetAfterName = cursor + 2
                }

                cursor = pointer
                continue
            }

            // Standard label
            val labelLen = lenByte
            cursor++

            if (cursor + labelLen > length) return null

            if (nameBuilder.isNotEmpty()) {
                nameBuilder.append('.')
            }

            for (i in 0 until labelLen) {
                val char = (bytes[cursor + i].toInt() and 0xFF).toChar()
                nameBuilder.append(char)
            }

            cursor += labelLen

            if (nameBuilder.length > MAX_NAME_LENGTH) {
                return null // Name exceeds RFC 1035 limit
            }
        }

        val finalOffset = offsetAfterName ?: (cursor + 1)
        val normalizedName = nameBuilder.toString().lowercase(Locale.ROOT)
        return Pair(normalizedName, finalOffset)
    }
}
