package com.lockweb.corefilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSetTest {

    @Test
    fun subdomainsMode_blocksExactAndSubdomainsOnly() {
        val rules = listOf(
            BlockRule("reddit.com", RuleMode.SUBDOMAINS)
        )
        val compileResult = RuleSet.compile(rules)
        val ruleSet = compileResult.ruleSet

        // MUST match:
        assertTrue(ruleSet.matches("reddit.com"))
        assertTrue(ruleSet.matches("www.reddit.com"))
        assertTrue(ruleSet.matches("old.reddit.com"))
        assertTrue(ruleSet.matches("a.b.reddit.com"))
        assertTrue(ruleSet.matches("np.m.reddit.com"))

        // MUST NOT match (label boundary safety):
        assertFalse("reddit.in should not match reddit.com", ruleSet.matches("reddit.in"))
        assertFalse("notreddit.com should not match reddit.com", ruleSet.matches("notreddit.com"))
        assertFalse("reddit.com.example.org should not match reddit.com", ruleSet.matches("reddit.com.example.org"))
        assertFalse("xreddit.com should not match reddit.com", ruleSet.matches("xreddit.com"))
        assertFalse("reddit.community should not match reddit.com", ruleSet.matches("reddit.community"))
        assertFalse("myreddit.com should not match reddit.com", ruleSet.matches("myreddit.com"))
    }

    @Test
    fun exactMode_blocksExactOnlyNotSubdomains() {
        val rules = listOf(
            BlockRule("reddit.com", RuleMode.EXACT)
        )
        val compileResult = RuleSet.compile(rules)
        val ruleSet = compileResult.ruleSet

        // MUST match exact:
        assertTrue(ruleSet.matches("reddit.com"))

        // MUST NOT match subdomains or others:
        assertFalse("www.reddit.com should not match exact reddit.com", ruleSet.matches("www.reddit.com"))
        assertFalse("old.reddit.com should not match exact reddit.com", ruleSet.matches("old.reddit.com"))
        assertFalse("a.b.reddit.com should not match exact reddit.com", ruleSet.matches("a.b.reddit.com"))
        assertFalse("notreddit.com should not match", ruleSet.matches("notreddit.com"))
    }

    @Test
    fun matches_caseInsensitiveAndTrailingDot() {
        val rules = listOf(
            BlockRule("instagram.com", RuleMode.SUBDOMAINS)
        )
        val ruleSet = RuleSet.compile(rules).ruleSet

        assertTrue(ruleSet.matches("INSTAGRAM.COM"))
        assertTrue(ruleSet.matches("Instagram.Com"))
        assertTrue(ruleSet.matches("instagram.com."))
        assertTrue(ruleSet.matches("WWW.INSTAGRAM.COM."))
    }

    @Test
    fun matches_unicodeAndPunycodeInterchangeable() {
        // German umlaut domain: münchen.de -> xn--mnchen-3ya.de
        val rules = listOf(
            BlockRule("xn--mnchen-3ya.de", RuleMode.SUBDOMAINS)
        )
        val ruleSet = RuleSet.compile(rules).ruleSet

        assertTrue("Query in Unicode matches Punycode rule", ruleSet.matches("münchen.de"))
        assertTrue("Query in Punycode matches Punycode rule", ruleSet.matches("xn--mnchen-3ya.de"))
        assertTrue("Subdomain query in Unicode matches", ruleSet.matches("stadt.münchen.de"))
        assertTrue("Subdomain query in Punycode matches", ruleSet.matches("stadt.xn--mnchen-3ya.de"))
    }

    @Test
    fun compile_duplicateRulesDetected() {
        val rules = listOf(
            BlockRule("tiktok.com", RuleMode.SUBDOMAINS),
            BlockRule("TIKTOK.COM", RuleMode.SUBDOMAINS),
            BlockRule("tiktok.com.", RuleMode.EXACT)
        )
        val result = RuleSet.compile(rules)

        assertEquals(1, result.ruleSet.totalRules)
        assertEquals(2, result.duplicates.size)
    }

    @Test
    fun compile_overlappingRulesDetected() {
        val rules = listOf(
            BlockRule("reddit.com", RuleMode.SUBDOMAINS),
            BlockRule("old.reddit.com", RuleMode.SUBDOMAINS),
            BlockRule("a.b.reddit.com", RuleMode.EXACT)
        )
        val result = RuleSet.compile(rules)

        assertEquals(3, result.ruleSet.totalRules)
        assertEquals(2, result.overlaps.size)

        val coveredDomains = result.overlaps.map { it.coveredRule.domain }.toSet()
        assertTrue(coveredDomains.contains("old.reddit.com"))
        assertTrue(coveredDomains.contains("a.b.reddit.com"))
    }

    @Test
    fun compile_mergesBlocklistDomainsCorrectly() {
        val userRules = listOf(
            BlockRule("example.com", RuleMode.EXACT)
        )
        val blocklist = listOf(
            "badsite.com",
            "adultcontent.net",
            "example.com" // Duplicate of user rule: user rule takes precedence
        )

        val result = RuleSet.compile(userRules, blocklist)
        val ruleSet = result.ruleSet

        assertEquals(3, ruleSet.totalRules)
        assertEquals(1, ruleSet.userRulesCount)
        assertEquals(2, ruleSet.blocklistCount) // example.com was already in user rules

        // User rule was EXACT, should remain EXACT
        assertEquals(RuleMode.EXACT, ruleSet.getMode("example.com"))
        assertTrue(ruleSet.matches("example.com"))
        assertFalse(ruleSet.matches("sub.example.com"))

        // Blocklist rules are SUBDOMAINS
        assertEquals(RuleMode.SUBDOMAINS, ruleSet.getMode("badsite.com"))
        assertTrue(ruleSet.matches("badsite.com"))
        assertTrue(ruleSet.matches("api.badsite.com"))
    }
}
