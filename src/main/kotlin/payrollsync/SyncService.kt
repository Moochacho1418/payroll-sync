package payrollsync

/**
 * Brings the local worker list in line with the provider for one organization.
 *
 * Only differences are written: new workers are created, changed ones updated, workers the
 * provider no longer returns are deactivated (never deleted, since time records point at them),
 * and everything else is left alone. Running it twice in a row writes nothing the second time.
 */
class SyncService(
    private val client: WorkerClient,
    private val store: WorkerStore,
    private val locks: LockService,
) {
    fun sync(orgId: String): SyncOutcome {
        val result = locks.withLock("payroll-sync:$orgId") { runSync(orgId) }
        return if (result == null) SyncOutcome.AlreadyRunning else SyncOutcome.Completed(result)
    }

    private fun runSync(orgId: String): SyncResult {
        val remote = client.fetchAll(orgId).associateBy { it.providerId }
        val local = store.findAll(orgId).associateBy { it.providerId }

        var created = 0
        var updated = 0
        var unchanged = 0
        for ((id, worker) in remote) {
            val wanted = worker.toLocal()
            when (local[id]) {
                null -> { store.upsert(orgId, wanted); created++ }
                wanted -> unchanged++
                else -> { store.upsert(orgId, wanted); updated++ }
            }
        }

        var deactivated = 0
        for ((id, worker) in local) {
            if (id !in remote) {
                if (worker.active) { store.deactivate(orgId, id); deactivated++ } else unchanged++
            }
        }
        return SyncResult(created, updated, deactivated, unchanged)
    }

    private fun ProviderWorker.toLocal() = LocalWorker(providerId, firstName, lastName, email, active)
}
