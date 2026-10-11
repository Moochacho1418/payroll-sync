package payrollsync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenProviderTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun provider(expiresInSeconds: Long, calls: IntArray) = TokenProvider(
        client = { calls[0]++; AccessToken("t${calls[0]}", now.plusSeconds(expiresInSeconds)) },
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `reuses a cached token`() {
        val calls = intArrayOf(0)
        val tokens = provider(3600, calls)
        assertEquals("t1", tokens.token())
        assertEquals("t1", tokens.token())
        assertEquals(1, calls[0])
    }

    @Test
    fun `refreshes a token inside the refresh margin`() {
        val calls = intArrayOf(0)
        val tokens = provider(30, calls)
        tokens.token()
        assertEquals("t2", tokens.token())
    }

    @Test
    fun `invalidate forces a new token`() {
        val calls = intArrayOf(0)
        val tokens = provider(3600, calls)
        tokens.token()
        tokens.invalidate()
        assertEquals("t2", tokens.token())
    }
}
