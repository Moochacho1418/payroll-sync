package payrollsync

import com.mysql.cj.jdbc.MysqlDataSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs only when PAYROLL_SYNC_MYSQL_URL points at a MySQL server (user and password in the URL). */
class MySqlLockServiceTest {
    private fun locks(): MySqlLockService {
        val url = System.getenv("PAYROLL_SYNC_MYSQL_URL")
        assumeTrue(url != null, "PAYROLL_SYNC_MYSQL_URL not set")
        return MySqlLockService(MysqlDataSource().apply { setURL(url) })
    }

    @Test
    fun `a second instance cannot take a held lock`() {
        val first = locks()
        val second = locks()
        val inner = first.withLock("test-lock") { second.withLock("test-lock") { "ran" } }
        assertNull(inner)
    }

    @Test
    fun `the lock is released after the block`() {
        val locks = locks()
        locks.withLock("test-lock") { }
        assertEquals("ran", locks.withLock("test-lock") { "ran" })
    }
}
