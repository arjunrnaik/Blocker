package com.lockweb.app.vpn

import android.net.VpnService
import com.lockweb.corefilter.dns.DnsBuilder
import com.lockweb.corefilter.dns.DnsParseResult
import com.lockweb.corefilter.dns.DnsParser
import com.lockweb.corefilter.packet.ParsedUdpPacket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Upstream DNS client that forwards allowed queries to upstream resolvers (default: Cloudflare 1.1.1.1 / 1.0.0.1)
 * using protected sockets to bypass the local VPN tunnel.
 */
class UpstreamResolver(
    private val vpnService: VpnService,
    private val primaryDns: String = "1.1.1.1",
    private val fallbackDns: String = "1.0.0.1",
    private val port: Int = 53,
    private val timeoutMs: Int = 2500
) {

    /**
     * Forwards an allowed DNS query to upstream resolvers.
     * Retries once on timeout and falls back to TCP if the UDP response is truncated (TC=1).
     *
     * @param rawQueryBytes The raw DNS query payload extracted from UDP.
     * @param requestPacket Context of the requesting client packet.
     * @return Raw DNS response bytes from upstream, or SERVFAIL on failure.
     */
    fun resolve(rawQueryBytes: ByteArray, requestPacket: ParsedUdpPacket): ByteArray {
        val queryId = if (rawQueryBytes.size >= 2) {
            ((rawQueryBytes[0].toInt() and 0xFF) shl 8) or (rawQueryBytes[1].toInt() and 0xFF)
        } else {
            0
        }

        val parsedQuery = when (val res = DnsParser.parse(rawQueryBytes)) {
            is DnsParseResult.Success -> res.message
            is DnsParseResult.Error -> return DnsBuilder.buildServfail(queryId)
        }

        // 1. Try Primary Resolver via UDP (with 1 retry)
        var responseBytes = sendUdpQueryWithRetry(primaryDns, rawQueryBytes)

        // 2. Fallback to Secondary Resolver if Primary failed
        if (responseBytes == null) {
            responseBytes = sendUdpQueryWithRetry(fallbackDns, rawQueryBytes)
        }

        // 3. Handle Truncation (TC=1): Retry over TCP
        if (responseBytes != null && isTruncated(responseBytes)) {
            val tcpResponse = sendTcpQuery(primaryDns, rawQueryBytes)
                ?: sendTcpQuery(fallbackDns, rawQueryBytes)
            if (tcpResponse != null) {
                responseBytes = tcpResponse
            }
        }

        // 4. Validate or return SERVFAIL
        return if (responseBytes != null && DnsBuilder.validateResponse(parsedQuery, responseBytes)) {
            responseBytes
        } else {
            val qBytes = parsedQuery.question?.rawQuestionBytes
            DnsBuilder.buildServfail(queryId, qBytes)
        }
    }

    private fun sendUdpQueryWithRetry(serverIp: String, queryBytes: ByteArray): ByteArray? {
        // First attempt
        val firstAttempt = sendUdpQuery(serverIp, queryBytes)
        if (firstAttempt != null) return firstAttempt

        // One retry
        return sendUdpQuery(serverIp, queryBytes)
    }

    private fun sendUdpQuery(serverIp: String, queryBytes: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!vpnService.protect(socket)) {
                return null
            }

            socket.soTimeout = timeoutMs

            val address = InetAddress.getByName(serverIp)
            val packet = DatagramPacket(queryBytes, queryBytes.size, address, port)
            socket.send(packet)

            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            receiveBuffer.copyOf(receivePacket.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    /**
     * Sends a DNS query over TCP per RFC 1035 Section 4.2.2 (2-byte length prefix).
     */
    private fun sendTcpQuery(serverIp: String, queryBytes: ByteArray): ByteArray? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            if (!vpnService.protect(socket)) {
                return null
            }

            socket.soTimeout = timeoutMs + 1000
            socket.connect(InetSocketAddress(serverIp, port), timeoutMs)

            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(queryBytes.size)
            output.write(queryBytes)
            output.flush()

            val input = DataInputStream(socket.getInputStream())
            val responseLength = input.readUnsignedShort()
            val responseBuffer = ByteArray(responseLength)
            input.readFully(responseBuffer)

            responseBuffer
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun isTruncated(response: ByteArray): Boolean {
        if (response.size < 3) return false
        val flagsHigh = response[2].toInt() and 0xFF
        return (flagsHigh and 0x02) != 0 // TC bit (0x02)
    }
}
