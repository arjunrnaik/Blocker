package com.lockweb.corefilter

/**
 * Result of parsing and validating user domain input.
 */
sealed interface ParseResult {
    /**
     * Parsing succeeded.
     * @property normalizedHost The normalized domain in ASCII/Punycode format.
     */
    data class Success(val normalizedHost: String) : ParseResult

    /**
     * Parsing rejected the input.
     * @property reason Human-readable explanation suitable for showing in the UI.
     * @property errorType Machine-readable category of rejection.
     */
    data class Failure(val reason: String, val errorType: ErrorType) : ParseResult
}

enum class ErrorType {
    EMPTY_INPUT,
    MALFORMED_URI,
    IP_LITERAL,
    SINGLE_LABEL,
    PUBLIC_SUFFIX,
    INVALID_CHARACTERS,
    INVALID_LENGTH,
    EMPTY_LABEL,
    INVALID_HYPHEN
}
