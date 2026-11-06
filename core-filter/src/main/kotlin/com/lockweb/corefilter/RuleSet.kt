package com.lockweb.corefilter

import java.net.IDN
import java.util.Locale

/**
 * Information about a rule that is rendered redundant by a broader parent rule.
 */
data class OverlapInfo(
    val coveredRule: BlockRule,
    val parentRule: BlockRule
)

/**
 * Result of compiling user rules and blocklist domains into an immutable [RuleSet].
 */
data class CompileResult(
    val ruleSet: RuleSet,
    val duplicates: List<BlockRule>,
    val overlaps: List<OverlapInfo>
)

/**
 * An immutable, high-performance rule engine for label-boundary domain filtering.
 */
class RuleSet internal constructor(
    private val ruleMap: Map<String, RuleMode>,
    val userRulesCount: Int,
    val blocklistCount: Int
) {

    val totalRules: Int get() = ruleMap.size

    /**
     * Checks whether the given hostname matches any rule in the compiled rule set.
     * Uses strict label-boundary matching to prevent substring false-positives.
     *
     * @param hostname The requested hostname from a DNS question.
     * @return `true` if blocked, `false` otherwise.
     */
    fun matches(hostname: String): Boolean {
        if (hostname.isEmpty() || ruleMap.isEmpty()) return false

        // Normalize query hostname
        var q = hostname.trim()
        if (q.endsWith('.')) {
            q = q.dropLast(1)
        }
        if (q.isEmpty()) return false

        q = q.lowercase(Locale.ROOT)

        // Convert Unicode to ASCII/Punycode for lookup consistency
        val asciiQ = try {
            IDN.toASCII(q, IDN.ALLOW_UNASSIGNED)
        } catch (_: Exception) {
            q
        }

        // 1. Direct exact match check
        val exactMatchMode = ruleMap[asciiQ]
        if (exactMatchMode != null) {
            return true
        }

        // 2. Label-boundary suffix traversal (from most specific parent to TLD)
        var dotIndex = asciiQ.indexOf('.')
        while (dotIndex != -1 && dotIndex < asciiQ.length - 1) {
            val parentDomain = asciiQ.substring(dotIndex + 1)
            val parentMode = ruleMap[parentDomain]
            if (parentMode == RuleMode.SUBDOMAINS) {
                return true
            }
            dotIndex = asciiQ.indexOf('.', dotIndex + 1)
        }

        return false
    }

    /**
     * Returns true if the domain is explicitly present in the rule set.
     */
    fun containsExactRule(domain: String): Boolean {
        return ruleMap.containsKey(domain.lowercase(Locale.ROOT))
    }

    /**
     * Returns the [RuleMode] configured for this exact domain, or null if not found.
     */
    fun getMode(domain: String): RuleMode? {
        return ruleMap[domain.lowercase(Locale.ROOT)]
    }

    companion object {
        val EMPTY = RuleSet(emptyMap(), 0, 0)

        /**
         * Compiles user-defined rules and optional external blocklist domains into an immutable [RuleSet].
         *
         * @param userRules Explicit user rules with metadata.
         * @param blocklistDomains Collection of domain strings from a third-party blocklist (e.g. OISD NSFW).
         * @return [CompileResult] containing the compiled rule set, any duplicate rules, and overlap diagnostics.
         */
        fun compile(
            userRules: Collection<BlockRule>,
            blocklistDomains: Collection<String> = emptyList()
        ): CompileResult {
            val duplicates = mutableListOf<BlockRule>()
            val seenUserDomains = mutableMapOf<String, BlockRule>()
            val finalMap = mutableMapOf<String, RuleMode>()

            // 1. Process user rules
            for (rule in userRules) {
                val normalizedHost = rule.domain.lowercase(Locale.ROOT).removeSuffix(".")
                if (seenUserDomains.containsKey(normalizedHost)) {
                    duplicates.add(rule)
                } else {
                    seenUserDomains[normalizedHost] = rule
                    finalMap[normalizedHost] = rule.mode
                }
            }

            // 2. Process blocklist domains (defaulting to SUBDOMAINS mode)
            var validBlocklistCount = 0
            for (blocklistDomain in blocklistDomains) {
                val normalized = blocklistDomain.lowercase(Locale.ROOT).removeSuffix(".")
                if (normalized.isNotEmpty() && !finalMap.containsKey(normalized)) {
                    finalMap[normalized] = RuleMode.SUBDOMAINS
                    validBlocklistCount++
                }
            }

            // 3. Detect overlaps within user rules
            val overlaps = mutableListOf<OverlapInfo>()
            for ((domain, rule) in seenUserDomains) {
                var dotIndex = domain.indexOf('.')
                while (dotIndex != -1 && dotIndex < domain.length - 1) {
                    val parentDomain = domain.substring(dotIndex + 1)
                    val parentRule = seenUserDomains[parentDomain]
                    if (parentRule != null && parentRule.mode == RuleMode.SUBDOMAINS) {
                        overlaps.add(OverlapInfo(coveredRule = rule, parentRule = parentRule))
                        break
                    }
                    dotIndex = domain.indexOf('.', dotIndex + 1)
                }
            }

            val ruleSet = RuleSet(
                ruleMap = java.util.Collections.unmodifiableMap(finalMap),
                userRulesCount = seenUserDomains.size,
                blocklistCount = validBlocklistCount
            )

            return CompileResult(
                ruleSet = ruleSet,
                duplicates = duplicates,
                overlaps = overlaps
            )
        }
    }
}
