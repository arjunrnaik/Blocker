package com.lockweb.corefilter.dns

/**
 * Clock abstraction for deterministic time testing in JVM tests.
 */
fun interface Clock {
    fun currentTimeMillis(): Long
}

object SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

data class DnsCacheKey(
    val qname: String,
    val qtype: Int,
    val qclass: Int
)

/**
 * Bounded LRU DNS cache with TTL expiration.
 */
class DnsCache(
    private val maxEntries: Int = 1024,
    private val clock: Clock = SystemClock
) {

    private data class CacheEntry(
        val responseBytes: ByteArray,
        val expiresAtMillis: Long
    )

    // LinkedHashMap with accessOrder=true gives LRU eviction
    private val cache = object : LinkedHashMap<DnsCacheKey, CacheEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DnsCacheKey, CacheEntry>?): Boolean {
            return size > maxEntries
        }
    }

    private val lock = Any()

    /**
     * Retrieves a cached DNS response if present and unexpired.
     */
    fun get(qname: String, qtype: Int, qclass: Int): ByteArray? {
        val key = DnsCacheKey(qname.lowercase(), qtype, qclass)
        val now = clock.currentTimeMillis()

        synchronized(lock) {
            val entry = cache[key] ?: return null
            if (now >= entry.expiresAtMillis) {
                cache.remove(key)
                return null
            }
            return entry.responseBytes.copyOf()
        }
    }

    /**
     * Stores a DNS response in the cache with the given TTL in seconds.
     */
    fun put(qname: String, qtype: Int, qclass: Int, response: ByteArray, ttlSeconds: Long) {
        if (ttlSeconds <= 0) return
        val key = DnsCacheKey(qname.lowercase(), qtype, qclass)
        val now = clock.currentTimeMillis()
        val expiresAt = now + (ttlSeconds * 1000L)

        synchronized(lock) {
            cache[key] = CacheEntry(response.copyOf(), expiresAt)
        }
    }

    /**
     * Flushes the entire cache (e.g. when filter rules change).
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    /**
     * Current number of entries in the cache.
     */
    fun size(): Int {
        synchronized(lock) {
            return cache.size
        }
    }
}
