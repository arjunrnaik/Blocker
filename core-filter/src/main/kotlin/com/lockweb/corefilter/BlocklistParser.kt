package com.lockweb.corefilter

import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader

/**
 * Parses blocklist files in domain list or hosts-file format (e.g., OISD, StevenBlack).
 */
object BlocklistParser {

    /**
     * Parses a blocklist from a [Reader] line-by-line.
     * Skips comments starting with `#`, empty lines, and malformed domain names safely.
     *
     * @param reader Source reader of the blocklist file.
     * @return List of normalized, unique domain strings.
     */
    fun parse(reader: Reader): List<String> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val validDomains = LinkedHashSet<String>()

        bufferedReader.forEachLine { line ->
            parseLine(line)?.let { domain ->
                validDomains.add(domain)
            }
        }

        return validDomains.toList()
    }

    /**
     * Parses a blocklist string in-memory.
     */
    fun parse(content: String): List<String> {
        return parse(StringReader(content))
    }

    /**
     * Parses a single blocklist line.
     * Handles:
     * - Blank lines -> null
     * - Comment lines `# ...` -> null
     * - Inline comments `domain.com # comment` -> `domain.com`
     * - Hosts format `0.0.0.0 domain.com` or `127.0.0.1 domain.com` -> `domain.com`
     * - Bare domain format `domain.com` -> `domain.com`
     * - Wildcard format `*.domain.com` -> `domain.com`
     * - Validates domain syntax via [DomainNormalizer].
     */
    fun parseLine(line: String): String? {
        var trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('!')) {
            return null
        }

        // Strip inline comments
        val commentIndex = trimmed.indexOf('#')
        if (commentIndex != -1) {
            trimmed = trimmed.substring(0, commentIndex).trim()
            if (trimmed.isEmpty()) return null
        }

        // Split tokens by whitespace
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        var candidate = tokens[0]

        // Handle hosts file redirects (e.g. 0.0.0.0, 127.0.0.1, ::1, ::)
        if (tokens.size >= 2) {
            if (candidate == "0.0.0.0" || candidate == "127.0.0.1" || candidate == "::" || candidate == "::1") {
                candidate = tokens[1]
            }
        }

        // Handle wildcard prefix (e.g., *.example.com)
        if (candidate.startsWith("*.")) {
            candidate = candidate.substring(2)
        }

        // Validate and normalize
        return when (val result = DomainNormalizer.parseInput(candidate)) {
            is ParseResult.Success -> result.normalizedHost
            is ParseResult.Failure -> null
        }
    }
}
