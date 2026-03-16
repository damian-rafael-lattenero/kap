package applicative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceTest {

    // ════════════════════════════════════════════════════════════════════════
    // Basic lifecycle
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `resource acquires and releases on success`() = runTest {
        val events = CopyOnWriteArrayList<String>()
        val resource = Resource(
            acquire = { events.add("acquire"); "conn" },
            release = { r -> events.add("release:$r") },
        )

        val result = resource.use<String> { r -> events.add("use:$r"); "result-of-$r" }

        assertEquals("result-of-conn", result)
        assertEquals(listOf("acquire", "use:conn", "release:conn"), events)
    }

    @Test
    fun `resource releases on failure`() = runTest {
        val events = CopyOnWriteArrayList<String>()
        val resource = Resource(
            acquire = { events.add("acquire"); "conn" },
            release = { r -> events.add("release:$r") },
        )

        val result = runCatching {
            resource.use<String> { throw RuntimeException("boom") }
        }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
        assertTrue(events.contains("release:conn"), "Resource must be released on failure")
    }

    @Test
    fun `resource releases on cancellation`() = runTest {
        val released = CompletableDeferred<String>()
        val started = CompletableDeferred<Unit>()
        val resource = Resource(
            acquire = { "handle" },
            release = { r -> released.complete("released:$r") },
        )

        val job = launch {
            resource.use<String> { started.complete(Unit); awaitCancellation() }
        }

        started.await()
        job.cancel()
        job.join()

        assertEquals("released:handle", released.await())
    }

    // ════════════════════════════════════════════════════════════════════════
    // map
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map transforms value and original resource is released`() = runTest {
        val events = CopyOnWriteArrayList<String>()
        val resource = Resource(
            acquire = { events.add("acquire"); 42 },
            release = { events.add("release:$it") },
        ).map { it * 2 }

        val result = resource.use<Int> { it + 1 }

        assertEquals(85, result)
        assertTrue(events.contains("release:42"), "Original resource must be released")
    }

    // ════════════════════════════════════════════════════════════════════════
    // flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `flatMap composes resources and releases inner then outer`() = runTest {
        val events = CopyOnWriteArrayList<String>()

        val outer = Resource(
            acquire = { events.add("acquire:outer"); "outer" },
            release = { events.add("release:$it") },
        )
        val composed = outer.flatMap { o ->
            Resource(
                acquire = { events.add("acquire:inner"); "$o+inner" },
                release = { events.add("release:$it") },
            )
        }

        val result = composed.use<String> { events.add("use:$it"); it }

        assertEquals("outer+inner", result)
        assertEquals(
            listOf("acquire:outer", "acquire:inner", "use:outer+inner", "release:outer+inner", "release:outer"),
            events,
            "Inner must release before outer",
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // zip
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip composes resources and releases all`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val r1 = Resource({ "db" }, { releases.add("release:$it") })
        val r2 = Resource({ "cache" }, { releases.add("release:$it") })
        val r3 = Resource({ "api" }, { releases.add("release:$it") })

        val combined = Resource.zip(r1, r2, r3) { a, b, c -> "$a|$b|$c" }
        val result = combined.use<String> { it }

        assertEquals("db|cache|api", result)
        assertEquals(3, releases.size, "All 3 resources must be released")
        assertTrue(releases.containsAll(listOf("release:db", "release:cache", "release:api")))
    }

    @Test
    fun `zip releases acquired resources when later acquisition fails`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val r1 = Resource({ "db" }, { releases.add("release:$it") })
        val r2 = Resource<String>({ throw RuntimeException("cache failed") }, { releases.add("release:$it") })

        val combined = Resource.zip(r1, r2) { a, b -> "$a|$b" }
        val result = runCatching { combined.use<String> { it } }

        assertTrue(result.isFailure)
        assertEquals("cache failed", result.exceptionOrNull()?.message)
        assertTrue(releases.contains("release:db"), "First resource must be released when second fails to acquire")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration with Computation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `use with Computation integrates with ap chains`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val dbResource = Resource({ "db-conn" }, { releases.add("release:$it") })
        val cacheResource = Resource({ "cache-conn" }, { releases.add("release:$it") })

        val combined = Resource.zip(dbResource, cacheResource) { db, cache -> db to cache }

        val result = Async {
            combined.useComputation { (db, cache) ->
                lift2 { a: String, b: String -> "$a|$b" }
                    .ap { delay(40); "data-from-$db" }
                    .ap { delay(40); "data-from-$cache" }
            }
        }

        assertEquals("data-from-db-conn|data-from-cache-conn", result)
        assertEquals(40, currentTime, "ap branches should run in parallel")
        assertEquals(2, releases.size, "Both resources must be released")
    }

    @Test
    fun `use with Computation releases on ap failure`() = runTest {
        val releases = CopyOnWriteArrayList<String>()
        val resource = Resource({ "conn" }, { releases.add("release:$it") })

        val result = runCatching {
            Async {
                resource.useComputation { _ ->
                    lift2 { a: String, b: String -> "$a|$b" }
                        .ap { "ok" }
                        .ap { throw RuntimeException("branch failed") }
                }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(releases.contains("release:conn"), "Resource must be released on computation failure")
    }
}
