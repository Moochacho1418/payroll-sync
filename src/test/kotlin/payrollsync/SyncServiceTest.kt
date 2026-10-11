package payrollsync

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncServiceTest {
    private var roster = listOf(
        ProviderWorker("a", "Ada", "Lovelace", "ada@example.com", true),
        ProviderWorker("b", "Grace", "Hopper", null, true),
    )
    private val store = InMemoryWorkerStore()
    private val locks = InMemoryLockService()
    private val service = SyncService(
        WorkerClient(
            { _, _, _ -> WorkerPage(roster, null) },
            TokenProvider({ AccessToken("t", Instant.now().plusSeconds(3600)) }),
        ),
        store,
        locks,
    )

    private fun run() = (service.sync("org") as SyncOutcome.Completed).result

    @Test
    fun `first run creates every worker`() {
        assertEquals(SyncResult(created = 2, updated = 0, deactivated = 0, unchanged = 0), run())
    }

    @Test
    fun `only changed workers are updated`() {
        run()
        roster = listOf(roster[0], roster[1].copy(email = "grace@example.com"))
        assertEquals(SyncResult(created = 0, updated = 1, deactivated = 0, unchanged = 1), run())
    }

    @Test
    fun `workers missing from the provider are deactivated once`() {
        run()
        roster = listOf(roster[0])
        assertEquals(SyncResult(created = 0, updated = 0, deactivated = 1, unchanged = 1), run())
        assertFalse(store.findAll("org").first { it.providerId == "b" }.active)
        assertEquals(SyncResult(created = 0, updated = 0, deactivated = 0, unchanged = 2), run())
    }

    @Test
    fun `an identical second run writes nothing`() {
        run()
        val writesAfterFirst = store.writes
        assertEquals(SyncResult(created = 0, updated = 0, deactivated = 0, unchanged = 2), run())
        assertEquals(writesAfterFirst, store.writes)
    }

    @Test
    fun `a sync already running for the org is skipped`() {
        val outcome = locks.withLock("payroll-sync:org") { service.sync("org") }
        assertEquals(SyncOutcome.AlreadyRunning, outcome)
        assertEquals(0, store.writes)
    }
}
