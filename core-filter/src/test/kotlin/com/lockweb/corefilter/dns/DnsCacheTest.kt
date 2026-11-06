package com.lockweb.corefilter.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsCacheTest {

    private class TestClock(var currentTime: Long = 1000L) : Clock {
        override fun currentTimeMillis(): Long = currentTime
    }

    @Test
    fun get_unexpiredEntry_returnsCachedResponse() {
        val clock = TestClock(1000L)
        val cache = DnsCache(maxEntries = 10, clock = clock)

        val response = byteArrayOf(1, 2, 3)
        cache.put("example.com", DnsQType.A, 1, response, ttlSeconds = 60)

        // Query at t = 1000ms
        val cached = cache.get("example.com", DnsQType.A, 1)
        assertArrayEquals(response, cached)

        // Query at t = 30000ms (30 seconds later, still within 60s TTL)
        clock.currentTime = 30_000L
        val cachedStillValid = cache.get("example.com", DnsQType.A, 1)
        assertArrayEquals(response, cachedStillValid)
    }

    @Test
    fun get_expiredEntry_returnsNullAndEvicts() {
        val clock = TestClock(1000L)
        val cache = DnsCache(maxEntries = 10, clock = clock)

        val response = byteArrayOf(1, 2, 3)
        cache.put("example.com", DnsQType.A, 1, response, ttlSeconds = 10) // 10s TTL

        // Advance time past 10 seconds: 1000 + 10_001 = 11001
        clock.currentTime = 11_001L

        val cached = cache.get("example.com", DnsQType.A, 1)
        assertNull("Expired entry should return null", cached)
        assertEquals(0, cache.size())
    }

    @Test
    fun put_exceedingMaxEntries_evictsOldestEntryLru() {
        val clock = TestClock(1000L)
        val cache = DnsCache(maxEntries = 2, clock = clock)

        cache.put("site1.com", DnsQType.A, 1, byteArrayOf(1), ttlSeconds = 100)
        cache.put("site2.com", DnsQType.A, 1, byteArrayOf(2), ttlSeconds = 100)

        // Access site1 to make site2 the eldest/least recently used
        cache.get("site1.com", DnsQType.A, 1)

        // Add site3, which should evict site2
        cache.put("site3.com", DnsQType.A, 1, byteArrayOf(3), ttlSeconds = 100)

        assertEquals(2, cache.size())
        assertNull("site2 should have been evicted", cache.get("site2.com", DnsQType.A, 1))
        assertNotNull(cache.get("site1.com", DnsQType.A, 1))
        assertNotNull(cache.get("site3.com", DnsQType.A, 1))
    }

    @Test
    fun clear_flushesAllEntries() {
        val cache = DnsCache(maxEntries = 10)
        cache.put("site1.com", DnsQType.A, 1, byteArrayOf(1), ttlSeconds = 100)
        cache.put("site2.com", DnsQType.A, 1, byteArrayOf(2), ttlSeconds = 100)

        assertEquals(2, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("site1.com", DnsQType.A, 1))
    }

    private fun assertNotNull(actual: Any?) {
        org.junit.Assert.assertNotNull(actual)
    }
}
