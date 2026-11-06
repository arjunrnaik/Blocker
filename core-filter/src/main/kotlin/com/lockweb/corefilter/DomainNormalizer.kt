package com.lockweb.corefilter

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Validates, normalizes, and extracts hostnames from user input and URLs according to RFC 1035 and IDNA.
 */
object DomainNormalizer {

    private const val MAX_DOMAIN_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63

    // Strict IPv4 regex: 4 octets between 0 and 255
    private val IPV4_REGEX = Regex(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$"
    )

    /**
     * Parses and normalizes a domain or URL input.
     *
     * @param rawInput Raw string entered by the user or read from a list.
     * @return [ParseResult.Success] with ASCII/Punycode normalized host, or [ParseResult.Failure] with reason.
     */
    fun parseInput(rawInput: String): ParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure("Input cannot be empty.", ErrorType.EMPTY_INPUT)
        }

        // Extract host using java.net.URI
        val rawHost = extractHost(trimmed) ?: return ParseResult.Failure(
            "Could not parse a valid hostname from the input.",
            ErrorType.MALFORMED_URI
        )

        // Strip trailing dot if present (DNS fully qualified name)
        var host = if (rawHost.endsWith('.')) {
            rawHost.dropLast(1)
        } else {
            rawHost
        }

        if (host.isEmpty()) {
            return ParseResult.Failure("Hostname is empty.", ErrorType.EMPTY_INPUT)
        }

        // Lowercase
        host = host.lowercase(Locale.ROOT)

        // Reject IP literals (both IPv4 and IPv6)
        if (isIpLiteral(host)) {
            return ParseResult.Failure(
                "DNS blocking cannot filter direct IP connections. Enter a domain name instead.",
                ErrorType.IP_LITERAL
            )
        }

        // Check for empty labels (e.g. consecutive dots)
        if (host.contains("..") || host.startsWith('.')) {
            return ParseResult.Failure(
                "Domain contains empty label (e.g. consecutive dots '..').",
                ErrorType.EMPTY_LABEL
            )
        }

        // Check if the domain is a bare public suffix or TLD (e.g. "com", "co.uk")
        if (PublicSuffixList.isPublicSuffix(host)) {
            return ParseResult.Failure(
                "Cannot block an entire top-level or public suffix '$host'.",
                ErrorType.PUBLIC_SUFFIX
            )
        }

        // Check single-label names (e.g. "localhost", "myrouter")
        if (!host.contains('.')) {
            return ParseResult.Failure(
                "Single-label names cannot be blocked as public domains.",
                ErrorType.SINGLE_LABEL
            )
        }

        // Pre-IDN label length and hyphen check
        val preLabels = host.split('.')
        for (label in preLabels) {
            if (label.isEmpty()) {
                return ParseResult.Failure(
                    "Domain contains empty label.",
                    ErrorType.EMPTY_LABEL
                )
            }
            if (label.length > MAX_LABEL_LENGTH) {
                return ParseResult.Failure(
                    "Domain label '$label' exceeds maximum allowed length of $MAX_LABEL_LENGTH characters.",
                    ErrorType.INVALID_LENGTH
                )
            }
            if (label.startsWith('-') || label.endsWith('-')) {
                return ParseResult.Failure(
                    "Domain label '$label' cannot start or end with a hyphen.",
                    ErrorType.INVALID_HYPHEN
                )
            }
        }

        if (host.length > MAX_DOMAIN_LENGTH) {
            return ParseResult.Failure(
                "Domain exceeds maximum allowed length of $MAX_DOMAIN_LENGTH characters.",
                ErrorType.INVALID_LENGTH
            )
        }

        // Convert Unicode / IDN to ASCII (Punycode)
        val asciiHost = try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
        } catch (e: Exception) {
            val msg = e.message?.lowercase(Locale.ROOT) ?: ""
            return when {
                msg.contains("empty") -> ParseResult.Failure(
                    "Domain contains empty label.",
                    ErrorType.EMPTY_LABEL
                )
                msg.contains("long") -> ParseResult.Failure(
                    "Domain or label exceeds maximum allowed length.",
                    ErrorType.INVALID_LENGTH
                )
                else -> ParseResult.Failure(
                    "Invalid internationalized domain name: ${e.message}",
                    ErrorType.INVALID_CHARACTERS
                )
            }
        }

        // Total length validation (RFC 1035: max 253 characters)
        if (asciiHost.length > MAX_DOMAIN_LENGTH) {
            return ParseResult.Failure(
                "Domain exceeds maximum allowed length of $MAX_DOMAIN_LENGTH characters.",
                ErrorType.INVALID_LENGTH
            )
        }

        // Post-IDN label validation
        val labels = asciiHost.split('.')
        if (labels.size < 2) {
            return ParseResult.Failure(
                "Single-label names cannot be blocked as public domains.",
                ErrorType.SINGLE_LABEL
            )
        }

        for (label in labels) {
            if (label.isEmpty()) {
                return ParseResult.Failure(
                    "Domain contains empty label (e.g. consecutive dots '..').",
                    ErrorType.EMPTY_LABEL
                )
            }
            if (label.length > MAX_LABEL_LENGTH) {
                return ParseResult.Failure(
                    "Domain label '$label' exceeds maximum allowed length of $MAX_LABEL_LENGTH characters.",
                    ErrorType.INVALID_LENGTH
                )
            }
            if (label.startsWith('-') || label.endsWith('-')) {
                return ParseResult.Failure(
                    "Domain label '$label' cannot start or end with a hyphen.",
                    ErrorType.INVALID_HYPHEN
                )
            }
            for (char in label) {
                val isAlphaNumeric = (char in 'a'..'z') || (char in '0'..'9')
                if (!isAlphaNumeric && char != '-') {
                    return ParseResult.Failure(
                        "Domain label '$label' contains invalid character '$char'.",
                        ErrorType.INVALID_CHARACTERS
                    )
                }
            }
        }

        // Public suffix / bare TLD rejection on post-IDN ASCII
        if (PublicSuffixList.isPublicSuffix(asciiHost)) {
            return ParseResult.Failure(
                "Cannot block an entire top-level or public suffix '$asciiHost'.",
                ErrorType.PUBLIC_SUFFIX
            )
        }

        return ParseResult.Success(asciiHost)
    }

    /**
     * Extracts the host portion using [java.net.URI].
     */
    private fun extractHost(input: String): String? {
        val hasScheme = input.contains("://")
        val uriCandidate = if (hasScheme) input else "http://$input"

        return try {
            val uri = URI(uriCandidate)
            val host = uri.host
            if (host != null && host.isNotEmpty()) {
                host
            } else {
                fallbackHostExtraction(input)
            }
        } catch (_: URISyntaxException) {
            fallbackHostExtraction(input)
        }
    }

    /**
     * Fallback host extractor for inputs with malformed URI syntax (e.g. containing underscores or invalid port characters).
     */
    private fun fallbackHostExtraction(input: String): String? {
        var str = input
        // Remove scheme if present
        val schemeIndex = str.indexOf("://")
        if (schemeIndex != -1) {
            str = str.substring(schemeIndex + 3)
        }
        // Remove user info if present
        val atIndex = str.indexOf('@')
        if (atIndex != -1) {
            str = str.substring(atIndex + 1)
        }
        // Remove path / query / fragment
        val pathDelimiters = charArrayOf('/', '?', '#')
        val firstPathDelimiter = pathDelimiters.map { str.indexOf(it) }.filter { it != -1 }.minOrNull()
        if (firstPathDelimiter != null) {
            str = str.substring(0, firstPathDelimiter)
        }

        // Remove port if present (handle IPv6 bracket notation)
        if (str.startsWith('[')) {
            val closingBracket = str.indexOf(']')
            if (closingBracket != -1) {
                return str.substring(0, closingBracket + 1)
            }
        } else {
            val portColon = str.lastIndexOf(':')
            if (portColon != -1 && str.indexOf(':') == portColon) {
                str = str.substring(0, portColon)
            }
        }

        val cleaned = str.trim()
        return if (cleaned.isNotEmpty()) cleaned else null
    }

    /**
     * Detects IPv4 or IPv6 literals.
     */
    fun isIpLiteral(host: String): Boolean {
        // Strip brackets if IPv6
        val clean = if (host.startsWith('[') && host.endsWith(']')) {
            host.substring(1, host.length - 1)
        } else {
            host
        }

        if (IPV4_REGEX.matches(clean)) {
            return true
        }

        // Check if valid IPv6 literal (contains multiple colons)
        if (clean.count { it == ':' } >= 2) {
            return true
        }

        return false
    }
}
