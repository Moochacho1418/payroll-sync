package payrollsync

/** A worker as this system stores it, keyed by the provider's id. */
data class LocalWorker(
    val providerId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val active: Boolean,
)

/** The local side of the sync. */
interface WorkerStore {
    fun findAll(orgId: String): List<LocalWorker>
    fun upsert(orgId: String, worker: LocalWorker)
    fun deactivate(orgId: String, providerId: String)
}

class InMemoryWorkerStore : WorkerStore {
    private val orgs = mutableMapOf<String, MutableMap<String, LocalWorker>>()

    /** How many writes have happened, so tests can prove an unchanged run writes nothing. */
    var writes = 0
        private set

    override fun findAll(orgId: String): List<LocalWorker> = orgs[orgId]?.values?.toList().orEmpty()

    override fun upsert(orgId: String, worker: LocalWorker) {
        orgs.getOrPut(orgId) { mutableMapOf() }[worker.providerId] = worker
        writes++
    }

    override fun deactivate(orgId: String, providerId: String) {
        val workers = orgs[orgId] ?: return
        val worker = workers[providerId] ?: return
        workers[providerId] = worker.copy(active = false)
        writes++
    }
}
