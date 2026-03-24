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
    fun `use with Computation integrates with with chains`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val dbResource = Resource({ "db-conn" }, { releases.add("release:$it") })
        val cacheResource = Resource({ "cache-conn" }, { releases.add("release:$it") })

        val combined = Resource.zip(dbResource, cacheResource) { db, cache -> db to cache }

        val result = Async {
            combined.useComputation { (db, cache) ->
                kap { a: String, b: String -> "$a|$b" }
                    .with { delay(40); "data-from-$db" }
                    .with { delay(40); "data-from-$cache" }
            }
        }

        assertEquals("data-from-db-conn|data-from-cache-conn", result)
        assertEquals(40, currentTime, "ap branches should run in parallel")
        assertEquals(2, releases.size, "Both resources must be released")
    }

    @Test
    fun `zip5 composes and releases all 5 resources`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val combined = Resource.zip(
            Resource({ "a" }, { releases.add("release:$it") }),
            Resource({ "b" }, { releases.add("release:$it") }),
            Resource({ "c" }, { releases.add("release:$it") }),
            Resource({ "d" }, { releases.add("release:$it") }),
            Resource({ "e" }, { releases.add("release:$it") }),
        ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        val result = combined.use<String> { it }

        assertEquals("a|b|c|d|e", result)
        assertEquals(5, releases.size, "All 5 resources must be released")
    }

    @Test
    fun `zip8 composes and releases all 8 resources`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val combined = Resource.zip(
            Resource({ "a" }, { releases.add("release:$it") }),
            Resource({ "b" }, { releases.add("release:$it") }),
            Resource({ "c" }, { releases.add("release:$it") }),
            Resource({ "d" }, { releases.add("release:$it") }),
            Resource({ "e" }, { releases.add("release:$it") }),
            Resource({ "f" }, { releases.add("release:$it") }),
            Resource({ "g" }, { releases.add("release:$it") }),
            Resource({ "h" }, { releases.add("release:$it") }),
        ) { a, b, c, d, e, f, g, h -> "$a|$b|$c|$d|$e|$f|$g|$h" }
        val result = combined.use<String> { it }

        assertEquals("a|b|c|d|e|f|g|h", result)
        assertEquals(8, releases.size, "All 8 resources must be released")
    }

    @Test
    fun `zip6 releases acquired resources when later acquisition fails`() = runTest {
        val releases = CopyOnWriteArrayList<String>()

        val combined = Resource.zip(
            Resource({ "a" }, { releases.add("release:$it") }),
            Resource({ "b" }, { releases.add("release:$it") }),
            Resource({ "c" }, { releases.add("release:$it") }),
            Resource<String>({ throw RuntimeException("d failed") }, { releases.add("release:$it") }),
            Resource({ "e" }, { releases.add("release:$it") }),
            Resource({ "f" }, { releases.add("release:$it") }),
        ) { a, b, c, d, e, f -> "$a|$b|$c|$d|$e|$f" }

        val result = runCatching { combined.use<String> { it } }
        assertTrue(result.isFailure)
        assertEquals("d failed", result.exceptionOrNull()?.message)
        assertEquals(3, releases.size, "a, b, c acquired before d fails → all 3 released")
    }

    @Test
    fun `use with Computation releases on with failure`() = runTest {
        val releases = CopyOnWriteArrayList<String>()
        val resource = Resource({ "conn" }, { releases.add("release:$it") })

        val result = runCatching {
            Async {
                resource.useComputation { _ ->
                    kap { a: String, b: String -> "$a|$b" }
                        .with { "ok" }
                        .with { throw RuntimeException("branch failed") }
                }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(releases.contains("release:conn"), "Resource must be released on computation failure")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Resource.defer
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `defer lazily constructs resource`() = runTest {
        var constructed = false
        val resource = Resource.defer {
            constructed = true
            Resource({ "conn" }, { })
        }
        // Not constructed until used
        assertEquals(false, constructed)

        val result = resource.use { it }
        assertEquals(true, constructed)
        assertEquals("conn", result)
    }

    @Test
    fun `defer selects resource conditionally`() = runTest {
        val events = CopyOnWriteArrayList<String>()
        fun makeResource(useRedis: Boolean): Resource<String> = Resource.defer {
            if (useRedis)
                Resource({ events.add("acquire:redis"); "redis" }, { events.add("release:redis") })
            else
                Resource({ events.add("acquire:mem"); "mem" }, { events.add("release:mem") })
        }

        val result = makeResource(useRedis = true).use { it }
        assertEquals("redis", result)
        assertTrue(events.contains("acquire:redis"))
        assertTrue(events.contains("release:redis"))
    }

    @Test
    fun `defer releases on failure`() = runTest {
        val events = CopyOnWriteArrayList<String>()
        val resource = Resource.defer {
            Resource({ events.add("acquire"); "conn" }, { events.add("release") })
        }

        val result = runCatching {
            resource.use { throw RuntimeException("boom") }
        }

        assertTrue(result.isFailure)
        assertTrue(events.contains("release"), "Deferred resource must release on failure")
    }
}
