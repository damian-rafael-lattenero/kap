package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EnsureTest {

    @Test
    fun `ensure passes when predicate holds`() = runTest {
        val graph = Kap.of(42).ensure({ IllegalStateException("nope") }) { it > 0 }
        assertEquals(42, Async { graph })
    }

    @Test
    fun `ensure throws when predicate fails`() = runTest {
        val graph = Kap.of(-1).ensure({ IllegalStateException("negative") }) { it > 0 }
        assertFailsWith<IllegalStateException> { val r = Async { graph } }
    }

    @Test
    fun `ensure preserves the original value`() = runTest {
        data class User(val name: String, val active: Boolean)
        val graph = Kap.of(User("Alice", true))
            .ensure({ IllegalStateException("inactive") }) { it.active }
        assertEquals(User("Alice", true), Async { graph })
    }

    @Test
    fun `ensure composes with andThen`() = runTest {
        val graph = Kap.of(10)
            .ensure({ IllegalArgumentException("too small") }) { it >= 5 }
            .andThen { n -> Kap.of(n * 2) }
        assertEquals(20, Async { graph })
    }

    @Test
    fun `ensure short-circuits andThen chain on failure`() = runTest {
        var andThenExecuted = false
        val graph = Kap.of(-1)
            .ensure({ IllegalStateException("bad") }) { it > 0 }
            .andThen { n -> andThenExecuted = true; Kap.of(n) }
        assertFailsWith<IllegalStateException> { val r = Async { graph } }
        assertTrue(!andThenExecuted)
    }

    @Test
    fun `ensure works inside parallel with branches — concurrency proof`() = runTest {
        val latch1 = CompletableDeferred<Unit>()
        val latch2 = CompletableDeferred<Unit>()

        val compA = Kap<Int> {
            latch1.complete(Unit); latch2.await(); 10
        }.ensure({ error("bad") }) { it > 0 }

        val compB = Kap<Int> {
            latch2.complete(Unit); latch1.await(); 20
        }.ensure({ error("bad") }) { it > 0 }

        val graph = kap { a: Int, b: Int -> a + b }.with(compA).with(compB)
        assertEquals(30, Async { graph })
    }

    @Test
    fun `ensureNotNull extracts non-null value`() = runTest {
        data class User(val name: String, val email: String?)
        val graph = Kap.of(User("Alice", "alice@test.com"))
            .ensureNotNull({ IllegalStateException("no email") }) { it.email }
        assertEquals("alice@test.com", Async { graph })
    }

    @Test
    fun `ensureNotNull throws when extracted value is null`() = runTest {
        data class User(val name: String, val email: String?)
        val graph = Kap.of(User("Bob", null))
            .ensureNotNull({ IllegalStateException("no email") }) { it.email }
        assertFailsWith<IllegalStateException> { val r = Async { graph } }
    }

    @Test
    fun `ensureNotNull chains with further processing`() = runTest {
        data class Wrapper(val inner: String?)
        val graph = Kap.of(Wrapper("hello"))
            .ensureNotNull({ error("null") }) { it.inner }
            .map { it.uppercase() }
        assertEquals("HELLO", Async { graph })
    }

    @Test
    fun `ensureNotNull parallel with — concurrency proof`() = runTest {
        data class Config(val url: String?)
        val latch1 = CompletableDeferred<Unit>()
        val latch2 = CompletableDeferred<Unit>()

        val compA = Kap {
            latch1.complete(Unit); latch2.await(); Config("https://api.example.com")
        }.ensureNotNull({ error("no url") }) { it.url }

        val compB = Kap {
            latch2.complete(Unit); latch1.await(); Config("https://cdn.example.com")
        }.ensureNotNull({ error("no url") }) { it.url }

        val graph = kap { a: String, b: String -> "$a|$b" }.with(compA).with(compB)
        assertEquals("https://api.example.com|https://cdn.example.com", Async { graph })
    }

    @Test
    fun `ensure and ensureNotNull compose in pipeline`() = runTest {
        data class User(val name: String, val active: Boolean, val email: String?)
        val graph = Kap.of(User("Alice", true, "alice@test.com"))
            .ensure({ IllegalStateException("inactive") }) { it.active }
            .ensureNotNull({ IllegalStateException("no email") }) { it.email }
            .map { it.uppercase() }
        assertEquals("ALICE@TEST.COM", Async { graph })
    }

    @Test
    fun `ensure inside recover produces fallback`() = runTest {
        val graph = Kap.of(-1)
            .ensure({ IllegalStateException("negative") }) { it > 0 }
            .recover { 0 }
        assertEquals(0, Async { graph })
    }

    @Test
    fun `ensure inside retry retries on failure`() = runTest {
        var attempts = 0
        val graph = Kap {
            attempts++
            if (attempts < 3) -1 else 42
        }.ensure({ IllegalStateException("bad") }) { it > 0 }.retry(3)
        assertEquals(42, Async { graph })
        assertEquals(3, attempts)
    }
}
