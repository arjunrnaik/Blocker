package com.lockweb.corefilter

import java.util.concurrent.atomic.AtomicLong

/**
 * Increment-only, anonymous counter for blocked queries.
 * Storing domain names or browsing history is strictly forbidden by project privacy rules.
 */
interface BlockCounter {
    fun increment(): Long
    fun getCount(): Long
    fun reset()
}

class AtomicBlockCounter(initialCount: Long = 0L) : BlockCounter {
    private val counter = AtomicLong(initialCount)

    override fun increment(): Long {
        return counter.incrementAndGet()
    }

    override fun getCount(): Long {
        return counter.get()
    }

    override fun reset() {
        counter.set(0L)
    }
}
