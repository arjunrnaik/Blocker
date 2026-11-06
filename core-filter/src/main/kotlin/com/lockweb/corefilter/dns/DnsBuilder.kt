package com.lockweb.corefilter.dns

/**
 * Builds DNS wire-format responses (NXDOMAIN, SERVFAIL) conforming to RFC 1035.
 */
object DnsBuilder {

    /**
     * Builds an NXDOMAIN response for a blocked query.
     * Echoes the query's ID, question, sets QR=1, AA=1, RD echoed, RA=1, and RCODE=3 (NXDOMAIN).
     */
    fun buildNxdomain(query: DnsMessage): ByteArray {
        val questionBytes = query.question?.rawQuestionBytes ?: byteArrayOf()
        val totalLength = 12 + questionBytes.size
        val response = ByteArray(totalLength)

        // 1. Transaction ID (echoed from query)
        response[0] = ((query.header.id shr 8) and 0xFF).toByte()
        response[1] = (query.header.id and 0xFF).toByte()

        // 2. Flags:
        // Byte 2: QR=1 (response), Opcode=query.opcode, AA=1 (authoritative), TC=0, RD=query.rd
        var flagsHigh = 0x84 // QR (0x80) | AA (0x04)
        flagsHigh = flagsHigh or ((query.header.opcode and 0x0F) shl 3)
        if (query.header.recursionDesired) {
            flagsHigh = flagsHigh or 0x01
        }
        response[2] = flagsHigh.toByte()

        // Byte 3: RA=1 (0x80), Z=0, RCODE=3 (NXDOMAIN = 0x03)
        val flagsLow = 0x80 or DnsRCode.NXDOMAIN
        response[3] = flagsLow.toByte()

        // 3. Question Count = 1 (if question present)
        response[4] = if (questionBytes.isNotEmpty()) 0x00 else 0x00
        response[5] = if (questionBytes.isNotEmpty()) 0x01 else 0x00

        // 4. Answer Count = 0
        response[6] = 0x00
        response[7] = 0x00

        // 5. Authority Record Count = 0
        response[8] = 0x00
        response[9] = 0x00

        // 6. Additional Record Count = 0
        response[10] = 0x00
        response[11] = 0x00

        // 7. Echo Question section exactly
        if (questionBytes.isNotEmpty()) {
            System.arraycopy(questionBytes, 0, response, 12, questionBytes.size)
        }

        return response
    }

    /**
     * Builds a SERVFAIL response (e.g. on upstream resolver failure or timeout).
     */
    fun buildServfail(queryId: Int, rawQuestionBytes: ByteArray? = null): ByteArray {
        val qBytes = rawQuestionBytes ?: byteArrayOf()
        val totalLength = 12 + qBytes.size
        val response = ByteArray(totalLength)

        response[0] = ((queryId shr 8) and 0xFF).toByte()
        response[1] = (queryId and 0xFF).toByte()

        // QR=1, RA=1, RCODE=SERVFAIL (2)
        response[2] = 0x80.toByte()
        response[3] = (0x80 or DnsRCode.SERVFAIL).toByte()

        response[4] = 0x00
        response[5] = if (qBytes.isNotEmpty()) 0x01 else 0x00

        response[6] = 0x00
        response[7] = 0x00
        response[8] = 0x00
        response[9] = 0x00
        response[10] = 0x00
        response[11] = 0x00

        if (qBytes.isNotEmpty()) {
            System.arraycopy(qBytes, 0, response, 12, qBytes.size)
        }

        return response
    }

    /**
     * Validates that an upstream DNS response safely matches the original query.
     */
    fun validateResponse(query: DnsMessage, responseBytes: ByteArray): Boolean {
        if (responseBytes.size < 12) return false

        val responseId = ((responseBytes[0].toInt() and 0xFF) shl 8) or (responseBytes[1].toInt() and 0xFF)
        if (responseId != query.header.id) {
            return false // Mismatched transaction ID
        }

        val flagsHigh = responseBytes[2].toInt() and 0xFF
        val isResponse = (flagsHigh and 0x80) != 0
        if (!isResponse) {
            return false // Not a response
        }

        // Validate question matches if question was parsed
        val queryQuestion = query.question
        if (queryQuestion != null) {
            val parsedResponse = DnsParser.parse(responseBytes)
            if (parsedResponse is DnsParseResult.Success) {
                val respQuestion = parsedResponse.message.question
                if (respQuestion != null) {
                    if (respQuestion.qname != queryQuestion.qname ||
                        respQuestion.qtype != queryQuestion.qtype ||
                        respQuestion.qclass != queryQuestion.qclass
                    ) {
                        return false // Mismatched question section
                    }
                }
            }
        }

        return true
    }
}
