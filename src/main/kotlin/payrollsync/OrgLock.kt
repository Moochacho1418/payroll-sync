package payrollsync

import java.sql.Connection
import javax.sql.DataSource

/** Runs a block only if no other instance is running it for the same key. */
interface LockService {
    /** Returns the block's result, or null if the lock is already held elsewhere. */
    fun <T> withLock(key: String, block: () -> T): T?
}

/**
 * A lock shared by every instance of the service, held in MySQL with GET_LOCK.
 *
 * The lock belongs to the connection that took it, so the same connection is kept open for the
 * whole run and used to release it. If the process dies, MySQL drops the connection and the lock
 * with it, so a crashed run can never leave an organization locked forever.
 */
class MySqlLockService(
    private val dataSource: DataSource,
    private val timeoutSeconds: Int = 0,
) : LockService {
    override fun <T> withLock(key: String, block: () -> T): T? {
        dataSource.connection.use { connection ->
            if (!acquire(connection, key)) return null
            try {
                return block()
            } finally {
                release(connection, key)
            }
        }
    }

    private fun acquire(connection: Connection, key: String): Boolean =
        connection.prepareStatement("SELECT GET_LOCK(?, ?)").use { statement ->
            statement.setString(1, key)
            statement.setInt(2, timeoutSeconds)
            statement.executeQuery().use { it.next() && it.getInt(1) == 1 }
        }

    private fun release(connection: Connection, key: String) {
        connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().close()
        }
    }
}

/** In-process lock for tests and the demo. */
class InMemoryLockService : LockService {
    private val held = mutableSetOf<String>()

    override fun <T> withLock(key: String, block: () -> T): T? {
        synchronized(held) { if (!held.add(key)) return null }
        try {
            return block()
        } finally {
            synchronized(held) { held.remove(key) }
        }
    }
}
