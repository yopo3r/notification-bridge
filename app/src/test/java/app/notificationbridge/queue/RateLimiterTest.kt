package app.notificationbridge.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `allows events up to the budget then blocks`() {
        val limiter = RateLimiter(maxEvents = 3, windowMs = 60_000)
        assertTrue(limiter.tryAcquire("app", now = 0))
        assertTrue(limiter.tryAcquire("app", now = 1_000))
        assertTrue(limiter.tryAcquire("app", now = 2_000))
        assertFalse(limiter.tryAcquire("app", now = 3_000))
    }

    @Test
    fun `budget recovers once the oldest events leave the window`() {
        val limiter = RateLimiter(maxEvents = 2, windowMs = 10_000)
        assertTrue(limiter.tryAcquire("app", now = 0))
        assertTrue(limiter.tryAcquire("app", now = 1_000))
        assertFalse(limiter.tryAcquire("app", now = 9_999))
        assertTrue(limiter.tryAcquire("app", now = 10_000))
    }

    @Test
    fun `each key has its own budget`() {
        val limiter = RateLimiter(maxEvents = 1, windowMs = 60_000)
        assertTrue(limiter.tryAcquire("chatty", now = 0))
        assertFalse(limiter.tryAcquire("chatty", now = 1))
        assertTrue(limiter.tryAcquire("quiet", now = 2))
    }

    @Test
    fun `blocked attempts do not consume budget`() {
        val limiter = RateLimiter(maxEvents = 1, windowMs = 10_000)
        assertTrue(limiter.tryAcquire("app", now = 0))
        repeat(20) { assertFalse(limiter.tryAcquire("app", now = 5_000)) }
        assertTrue(limiter.tryAcquire("app", now = 10_000))
    }
}
