package kap

import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boundary tests for [Resource.zip] at various arities,
 * verifying release order and cleanup guarantees.
 */
class ResourceZipBoundaryTest {

    @Test
    fun `Resource zip 2 releases in reverse order`() = runTest {
        val releaseOrder = mutableListOf<String>()

        val result = Resource.zip(
            Resource({ "A" }, { releaseOrder.add("A") }),
            Resource({ "B" }, { releaseOrder.add("B") }),
        ) { a, b -> "$a$b" }.use { it }

        assertEquals("AB", result)
        assertEquals(listOf("B", "A"), releaseOrder)
    }

    @Test
    fun `Resource zip 5 releases in reverse order`() = runTest {
        val releaseOrder = mutableListOf<Int>()

        val result = Resource.zip(
            Resource({ 1 }, { releaseOrder.add(1) }),
            Resource({ 2 }, { releaseOrder.add(2) }),
            Resource({ 3 }, { releaseOrder.add(3) }),
            Resource({ 4 }, { releaseOrder.add(4) }),
            Resource({ 5 }, { releaseOrder.add(5) }),
        ) { a, b, c, d, e -> a + b + c + d + e }.use { it }

        assertEquals(15, result)
        assertEquals(listOf(5, 4, 3, 2, 1), releaseOrder)
    }

    @Test
    fun `Resource zip releases all resources on use failure`() = runTest {
        val acquireCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        fun makeResource(id: Int): Resource<Int> = Resource(
            acquire = { acquireCount.incrementAndGet(); id },
            release = { releaseCount.incrementAndGet() },
        )

        val combined = Resource.zip(
            makeResource(1),
            makeResource(2),
            makeResource(3),
            makeResource(4),
        ) { a, b, c, d -> a + b + c + d }

        val result = runCatching {
            combined.use { throw IllegalStateException("use failed") }
        }

        assertTrue(result.isFailure)
        assertEquals(4, acquireCount.get(), "All 4 should be acquired")
        assertEquals(4, releaseCount.get(), "All 4 should be released even on failure")
    }

    @Test
    fun `Resource useKap works with with chains inside`() = runTest {
        val releaseOrder = mutableListOf<String>()

        val infra = Resource.zip(
            Resource({ "db" }, { releaseOrder.add("db") }),
            Resource({ "cache" }, { releaseOrder.add("cache") }),
        ) { db, cache -> db to cache }

        val result = infra.useKap { (db, cache) ->
            kap { a: String, b: String -> "$a|$b" }
                .with(Kap { "query:$db" })
                .with(Kap { "get:$cache" })
        }.executeGraph()

        assertEquals("query:db|get:cache", result)
        assertEquals(listOf("cache", "db"), releaseOrder)
    }

    @Test
    fun `Resource defer selects resource conditionally`() = runTest {
        val releaseLog = mutableListOf<String>()

        val resource = Resource.defer {
            Resource({ "selected" }, { releaseLog.add("selected-released") })
        }

        val result = resource.use { it }
        assertEquals("selected", result)
        assertEquals(listOf("selected-released"), releaseLog)
    }

    @Test
    fun `Resource map transforms value while preserving release`() = runTest {
        val released = AtomicInteger(0)

        val result = Resource(
            acquire = { 42 },
            release = { released.incrementAndGet() },
        ).map { it * 2 }.use { it }

        assertEquals(84, result)
        assertEquals(1, released.get())
    }

    @Test
    fun `Resource andThen composes and releases both in reverse`() = runTest {
        val releaseOrder = mutableListOf<String>()

        val result = Resource(
            acquire = { "outer" },
            release = { releaseOrder.add("outer") },
        ).andThen { outer ->
            Resource(
                acquire = { "$outer+inner" },
                release = { releaseOrder.add("inner") },
            )
        }.use { it }

        assertEquals("outer+inner", result)
        assertEquals(listOf("inner", "outer"), releaseOrder)
    }
}
