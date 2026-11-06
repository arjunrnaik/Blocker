package com.lockweb.corefilter

/**
 * Represents an individual filtering rule.
 *
 * @property domain The normalized hostname (lowercase ASCII/Punycode, no trailing dot).
 * @property mode Whether to match all subdomains or only the exact hostname.
 * @property note Optional user note describing the rule.
 * @property isPermanent If true, this rule cannot be removed through the app UI (used for permanent filters).
 */
data class BlockRule(
    val domain: String,
    val mode: RuleMode = RuleMode.SUBDOMAINS,
    val note: String? = null,
    val isPermanent: Boolean = false
)
