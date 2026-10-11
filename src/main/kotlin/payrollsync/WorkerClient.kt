package payrollsync

/** Thrown by the provider API. [status] is the HTTP status code. */
class ProviderException(val status: Int, message: String) : RuntimeException(message) {
    val retryable: Boolean get() = status == 429 || status >= 500
}

/** The provider's worker endpoint, one page per call. */
fun interface WorkerApi {
    fun fetchWorkers(orgId: String, accessToken: String, cursor: String?): WorkerPage
}

/**
 * Reads every worker for an organization: follows pagination, attaches the token, retries
 * rate limits and server errors with backoff, and refreshes the token once on a 401.
 */
class WorkerClient(
    private val api: WorkerApi,
    private val tokens: TokenProvider,
    private val maxAttempts: Int = 4,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val baseDelayMs: Long = 500,
) {
    fun fetchAll(orgId: String): List<ProviderWorker> {
        val workers = mutableListOf<ProviderWorker>()
        var cursor: String? = null
        do {
            val page = fetchPage(orgId, cursor)
            workers += page.workers
            cursor = page.nextCursor
        } while (cursor != null)
        return workers
    }

    private fun fetchPage(orgId: String, cursor: String?): WorkerPage {
        var attempt = 1
        var refreshedToken = false
        while (true) {
            try {
                return api.fetchWorkers(orgId, tokens.token(), cursor)
            } catch (e: ProviderException) {
                when {
                    e.status == 401 && !refreshedToken -> {
                        tokens.invalidate()
                        refreshedToken = true
                    }
                    e.retryable && attempt < maxAttempts -> {
                        sleep(baseDelayMs shl (attempt - 1))
                        attempt++
                    }
                    else -> throw e
                }
            }
        }
    }
}
