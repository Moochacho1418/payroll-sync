package payrollsync

import java.time.Instant

/** A worker as the payroll provider returns it. */
data class ProviderWorker(
    val providerId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val active: Boolean,
)

/** One page of a paginated provider response. */
data class WorkerPage(val workers: List<ProviderWorker>, val nextCursor: String?)

/** An OAuth access token and when it stops working. */
data class AccessToken(val value: String, val expiresAt: Instant)

/** What a sync run did, for logging and alerting. */
data class SyncResult(
    val created: Int,
    val updated: Int,
    val deactivated: Int,
    val unchanged: Int,
) {
    val total: Int get() = created + updated + deactivated + unchanged
}

/** Outcome of asking to run a sync for one organization. */
sealed interface SyncOutcome {
    data class Completed(val result: SyncResult) : SyncOutcome

    /** Another instance already holds the lock for this organization. */
    data object AlreadyRunning : SyncOutcome
}
