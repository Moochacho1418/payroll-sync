package payrollsync

import java.time.Clock
import java.time.Duration

/** Exchanges client credentials for an access token. One call to the provider's token endpoint. */
fun interface TokenClient {
    fun requestToken(): AccessToken
}

/**
 * Caches the provider's OAuth token and refreshes it shortly before it expires.
 *
 * Every API call asks for a token, so without the cache each call would hit the token endpoint.
 * The refresh margin keeps a request from leaving with a token that expires mid-flight.
 * Synchronized so concurrent callers trigger one refresh, not one each.
 */
class TokenProvider(
    private val client: TokenClient,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshMargin: Duration = Duration.ofSeconds(60),
) {
    private var cached: AccessToken? = null

    @Synchronized
    fun token(): String {
        val current = cached
        if (current != null && clock.instant().plus(refreshMargin).isBefore(current.expiresAt)) {
            return current.value
        }
        return client.requestToken().also { cached = it }.value
    }

    /** Drops the cached token, used when the provider rejects it with a 401 before expiry. */
    @Synchronized
    fun invalidate() {
        cached = null
    }
}
