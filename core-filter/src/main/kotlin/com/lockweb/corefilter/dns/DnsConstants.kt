package com.lockweb.corefilter.dns

/**
 * Standard DNS record types (RFC 1035, RFC 3596, RFC 9460).
 */
object DnsQType {
    const val A = 1
    const val NS = 2
    const val CNAME = 5
    const val SOA = 6
    const val PTR = 12
    const val MX = 15
    const val TXT = 16
    const val AAAA = 28
    const val SRV = 33
    const val OPT = 41
    const val HTTPS = 65
    const val ANY = 255
}

/**
 * DNS Response Codes (RCODE).
 */
object DnsRCode {
    const val NOERROR = 0
    const val FORMERR = 1
    const val SERVFAIL = 2
    const val NXDOMAIN = 3
    const val NOTIMP = 4
    const val REFUSED = 5
}
