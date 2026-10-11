package payrollsync

import java.time.Instant

/** Runs two syncs against a fake provider and prints what each one did. */
fun main() {
    var roster = listOf(
        ProviderWorker("w1", "Ada", "Lovelace", "ada@example.com", true),
        ProviderWorker("w2", "Grace", "Hopper", "grace@example.com", true),
        ProviderWorker("w3", "Alan", "Turing", null, true),
    )
    val api = WorkerApi { _, _, cursor ->
        // Two workers per page, so the client has to follow the cursor.
        val start = cursor?.toInt() ?: 0
        val end = minOf(start + 2, roster.size)
        WorkerPage(roster.subList(start, end), if (end < roster.size) end.toString() else null)
    }
    val tokens = TokenProvider({ AccessToken("demo-token", Instant.now().plusSeconds(3600)) })
    val store = InMemoryWorkerStore()
    val service = SyncService(WorkerClient(api, tokens), store, InMemoryLockService())

    println("first run:  ${service.sync("org-1")}")

    roster = listOf(
        roster[0],
        roster[1].copy(email = "ghopper@example.com"),
    )
    println("second run: ${service.sync("org-1")}")
    println("third run:  ${service.sync("org-1")}")
}
