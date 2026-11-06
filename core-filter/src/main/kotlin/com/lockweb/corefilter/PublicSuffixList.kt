package com.lockweb.corefilter

/**
 * Embedded public suffix list containing common TLDs and multi-part public suffixes.
 * Prevents users from accidentally adding rules that block entire TLDs (e.g., "com", "co.uk").
 */
object PublicSuffixList {

    private val PUBLIC_SUFFIXES: Set<String> = setOf(
        // Generic TLDs
        "com", "net", "org", "edu", "gov", "mil", "int", "arpa",
        "info", "biz", "name", "pro", "mobi", "asia", "cat", "jobs",
        "tel", "travel", "xxx", "app", "dev", "io", "ai", "co", "me",
        "cc", "tv", "xyz", "online", "site", "store", "tech", "club",
        "vip", "top", "space", "live", "world", "blog", "cloud", "link",
        "wiki", "news", "today", "agency", "email", "guru", "media",
        "zone", "work", "shop", "one", "pub", "page",

        // Country code TLDs
        "in", "us", "uk", "ca", "au", "de", "fr", "jp", "cn", "ru",
        "br", "it", "es", "nl", "se", "no", "fi", "dk", "pl", "ch",
        "at", "be", "cz", "gr", "hu", "ie", "il", "mx", "nz", "pt",
        "ro", "sg", "kr", "tw", "hk", "za", "tr", "ua", "vn", "my",
        "th", "ph", "id", "pk", "bd", "ng", "eg", "ke", "sa", "ae",

        // Multi-part ccTLD public suffixes: United Kingdom
        "co.uk", "org.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "ac.uk", "gov.uk", "sch.uk",

        // Multi-part ccTLD public suffixes: India
        "co.in", "net.in", "org.in", "gen.in", "firm.in", "ind.in", "nic.in", "ac.in", "edu.in", "res.in", "gov.in", "mil.in",

        // Multi-part ccTLD public suffixes: Australia
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "asn.au", "id.au",

        // Multi-part ccTLD public suffixes: Japan
        "co.jp", "ne.jp", "or.jp", "ac.jp", "ed.jp", "go.jp", "gr.jp", "lg.jp",

        // Multi-part ccTLD public suffixes: Brazil
        "com.br", "net.br", "org.br", "gov.br", "edu.br", "mil.br", "art.br", "eco.br",

        // Multi-part ccTLD public suffixes: New Zealand
        "co.nz", "net.nz", "org.nz", "govt.nz", "ac.nz", "school.nz", "geek.nz",

        // Multi-part ccTLD public suffixes: South Africa
        "co.za", "net.za", "org.za", "gov.za", "ac.za", "edu.za",

        // Multi-part ccTLD public suffixes: Canada
        "gc.ca", "qc.ca", "on.ca", "bc.ca", "ab.ca",

        // Multi-part ccTLD public suffixes: Other common
        "com.sg", "edu.sg", "gov.sg", "com.mx", "org.mx", "gob.mx", "edu.mx",
        "co.kr", "ne.kr", "or.kr", "re.kr", "pe.kr", "go.kr", "mil.kr",
        "com.tw", "org.tw", "net.tw", "edu.tw", "gov.tw",
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr",
        "com.pk", "org.pk", "net.pk", "edu.pk", "gov.pk",
        "com.bd", "org.bd", "net.bd", "edu.bd", "gov.bd"
    )

    /**
     * Checks whether the given normalized ASCII domain is a bare public suffix or TLD.
     */
    fun isPublicSuffix(normalizedDomain: String): Boolean {
        return PUBLIC_SUFFIXES.contains(normalizedDomain.lowercase())
    }
}
