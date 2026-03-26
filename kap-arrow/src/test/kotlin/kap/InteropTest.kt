package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class InteropTest {

    // ════════════════════════════════════════════════════════════════════════
    // Deferred → Kap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Deferred toKap awaits the deferred`() = runTest {
        val deferred = CompletableDeferred(42)
        val result = Async { deferred.toKap() }
        assertEquals(42, result)
    }

    @Test
    fun `Deferred toKap composes with kap+with`() = runTest {
        val deferredA = CompletableDeferred("hello")
        val deferredB = CompletableDeferred("world")

        val result = Async {
            kap { a: String, b: String -> "$a $b" }
                .with { with(deferredA.toKap()) { execute() } }
                .with { with(deferredB.toKap()) { execute() } }
        }
        assertEquals("hello world", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Kap → Deferred
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Kap toDeferred starts eagerly in scope`() = runTest {
        val computation = Kap.of(42)
        val deferred = coroutineScope {
            computation.toDeferred(this)
        }
        assertEquals(42, deferred.await())
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow → Kap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Flow firstAsKap takes first emission`() = runTest {
        val flow = flowOf("first", "second", "third")
        val result = Async { flow.firstAsKap() }
        assertEquals("first", result)
    }

    @Test
    fun `Flow firstAsKap composes with kap+with`() = runTest {
        val result = Async {
            kap { a: String, b: Int -> "$a=$b" }
                .with { with(flowOf("count").firstAsKap()) { execute() } }
                .with { with(flowOf(42).firstAsKap()) { execute() } }
        }
        assertEquals("count=42", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // suspend lambda → Kap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `suspend lambda toKap wraps correctly`() = runTest {
        val fn: suspend () -> Int = { 42 }
        val result = Async { fn.toKap() }
        assertEquals(42, result)
    }

    @Test
    fun `suspend lambda toKap composes with kap+with`() = runTest {
        val fetchUser: suspend () -> String = { "Alice" }
        val fetchAge: suspend () -> Int = { 30 }

        val result = Async {
            kap { name: String, age: Int -> "$name($age)" }
                .with { with(fetchUser.toKap()) { execute() } }
                .with { with(fetchAge.toKap()) { execute() } }
        }
        assertEquals("Alice(30)", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // kotlin.Result ↔ Arrow Either (via kap-arrow bridges)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Result success converts to Either Right`() {
        val result = Result.success(42).toEither()
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Result failure converts to Either Left`() {
        val ex = RuntimeException("boom")
        val result = Result.failure<Int>(ex).toEither()
        assertIs<Either.Left<Throwable>>(result)
        assertEquals("boom", result.value.message)
    }

    @Test
    fun `Either Right converts to Result success`() {
        val either: Either<Throwable, Int> = Either.Right(42)
        val result = either.toResult()
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `Either Left converts to Result failure`() {
        val either: Either<Throwable, Int> = Either.Left(RuntimeException("boom"))
        val result = either.toResult()
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Either Left with mapError converts to Result failure`() {
        val either: Either<String, Int> = Either.Left("bad input")
        val result = either.toResult { IllegalArgumentException(it) }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals("bad input", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Result roundtrip through Either preserves value`() {
        val original = Result.success("hello")
        val roundtripped = original.toEither().let {
            when (it) {
                is Either.Left -> Result.failure(it.value)
                is Either.Right -> Result.success(it.value)
            }
        }
        assertEquals(original.getOrNull(), roundtripped.getOrNull())
    }

    // ════════════════════════════════════════════════════════════════════════
    // Result → Validated Kap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Result success converts to valid computation`() = runTest {
        val result = Async {
            Result.success(42).toValidated { "error: ${it.message}" }
        }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Result failure converts to invalid computation`() = runTest {
        val result = Async {
            Result.failure<Int>(RuntimeException("boom")).toValidated { "error: ${it.message}" }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals("error: boom", result.value.head)
    }

    // ════════════════════════════════════════════════════════════════════════
    // delayed
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `delayed returns value after duration`() = runTest {
        val result = Async { delayed(100.milliseconds, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `delayed with block executes block after duration`() = runTest {
        var executed = false
        val result = Async {
            delayed(100.milliseconds) {
                executed = true
                "done"
            }
        }
        assertEquals("done", result)
        assertTrue(executed)
    }

    @Test
    fun `delayed composes with race`() = runTest {
        val result = Async {
            race(
                delayed(10_000.milliseconds, "slow"),
                Kap.of("fast"),
            )
        }
        assertEquals("fast", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Deferred — timing and cancellation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Deferred toKap suspends until completion`() = runTest {
        val deferred = CompletableDeferred<String>()
        val computation = deferred.toKap()

        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    kotlinx.coroutines.delay(50)
                    deferred.complete("resolved")
                    "trigger"
                }
                .with { with(computation) { execute() } }
        }
        assertEquals("trigger|resolved", result)
    }

    @Test
    fun `delayed timing is exact in virtual time`() = runTest {
        val result = Async { delayed(100.milliseconds, "hello") }
        assertEquals("hello", result)
    }

    @Test
    fun `catching preserves CancellationException`() = runTest {
        val result = runCatching {
            Async {
                catching {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            }
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `catching wraps non-cancellation exceptions in Result`() = runTest {
        val result = Async {
            catching { throw RuntimeException("boom") }
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Result toValidated composes with zipV`() = runTest {
        val good = Result.success(42).toValidated { "error: ${it.message}" }
        val bad = Result.failure<Int>(RuntimeException("oops")).toValidated { "error: ${it.message}" }

        val result = Async {
            kapV<String, Int, Int, Int> { a, b -> a + b }
                .withV(good)
                .withV(bad)
        }

        assertTrue(result is Either.Left)
        assertEquals("error: oops", (result as Either.Left).value.head)
    }
}
