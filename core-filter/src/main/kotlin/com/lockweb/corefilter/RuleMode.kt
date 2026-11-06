package com.lockweb.corefilter

enum class RuleMode {
    /**
     * Matches the hostname and all its subdomains.
     * Example: "reddit.com" matches "reddit.com", "www.reddit.com", "old.reddit.com".
     */
    SUBDOMAINS,

    /**
     * Matches only the exact hostname.
     * Example: "reddit.com" matches only "reddit.com", NOT "www.reddit.com".
     */
    EXACT
}
