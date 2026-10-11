package payrollsync

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkerClientTest {
    private fun worker(id: String) = ProviderWorker(id, "First", "Last", null, true)

    private var tokenCount = 0
    private val tokens = TokenProvider({
        tokenCount++
        AccessToken("token$tokenCount", Instant.now().plusSeconds(3600))
    })
    private val sleeps = mutableListOf<Long>()

    private fun client(api: WorkerApi) = WorkerClient(api, tokens, sleep = { sleeps += it })

    @Test
    fun `follows cursors until the last page`() {
        val pages = mapOf(
            null to WorkerPage(listOf(worker("a"), worker("b")), "2"),
            "2" to WorkerPage(listOf(worker("c")), null),
        )
        val all = client { _, _, cursor -> pages.getValue(cursor) }.fetchAll("org")
        assertEquals(listOf("a", "b", "c"), all.map { it.providerId })
    }

    @Test
    fun `retries a server error with backoff then succeeds`() {
        var calls = 0
        val all = client { _, _, _ ->
            if (++calls < 3) throw ProviderException(503, "unavailable")
            WorkerPage(listOf(worker("a")), null)
        }.fetchAll("org")
        assertEquals(1, all.size)
        assertEquals(listOf(500L, 1000L), sleeps)
    }

    @Test
    fun `gives up after max attempts`() {
        var calls = 0
        assertFailsWith<ProviderException> {
            client { _, _, _ -> calls++; throw ProviderException(429, "slow down") }.fetchAll("org")
        }
        assertEquals(4, calls)
    }

    @Test
    fun `refreshes the token once on 401`() {
        val seen = mutableListOf<String>()
        val all = client { _, token, _ ->
            seen += token
            if (token == "token1") throw ProviderException(401, "expired")
            WorkerPage(listOf(worker("a")), null)
        }.fetchAll("org")
        assertEquals(1, all.size)
        assertEquals(listOf("token1", "token2"), seen)
    }

    @Test
    fun `a second 401 is not retried`() {
        var calls = 0
        assertFailsWith<ProviderException> {
            client { _, _, _ -> calls++; throw ProviderException(401, "bad credentials") }.fetchAll("org")
        }
        assertEquals(2, calls)
    }

    @Test
    fun `a client error is not retried`() {
        var calls = 0
        assertFailsWith<ProviderException> {
            client { _, _, _ -> calls++; throw ProviderException(400, "bad request") }.fetchAll("org")
        }
        assertEquals(1, calls)
    }
}
