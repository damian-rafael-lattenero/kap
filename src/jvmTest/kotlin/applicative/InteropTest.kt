package applicative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
    // Deferred → Computation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Deferred toComputation awaits the deferred`() = runTest {
        val deferred = CompletableDeferred(42)
        val result = Async { deferred.toComputation() }
        assertEquals(42, result)
    }

    @Test
    fun `Deferred toComputation composes with lift+ap`() = runTest {
        val deferredA = CompletableDeferred("hello")
        val deferredB = CompletableDeferred("world")

        val result = Async {
            lift2 { a: String, b: String -> "$a $b" }
                .ap { with(deferredA.toComputation()) { execute() } }
                .ap { with(deferredB.toComputation()) { execute() } }
        }
        assertEquals("hello world", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Computation → Deferred
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Computation toDeferred starts eagerly in scope`() = runTest {
        val computation = pure(42)
        val deferred = coroutineScope {
            computation.toDeferred(this)
        }
        assertEquals(42, deferred.await())
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow → Computation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Flow firstAsComputation takes first emission`() = runTest {
        val flow = flowOf("first", "second", "third")
        val result = Async { flow.firstAsComputation() }
        assertEquals("first", result)
    }

    @Test
    fun `Flow firstAsComputation composes with lift+ap`() = runTest {
        val result = Async {
            lift2 { a: String, b: Int -> "$a=$b" }
                .ap { with(flowOf("count").firstAsComputation()) { execute() } }
                .ap { with(flowOf(42).firstAsComputation()) { execute() } }
        }
        assertEquals("count=42", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // suspend lambda → Computation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `suspend lambda toComputation wraps correctly`() = runTest {
        val fn: suspend () -> Int = { 42 }
        val result = Async { fn.toComputation() }
        assertEquals(42, result)
    }

    @Test
    fun `suspend lambda toComputation composes with lift+ap`() = runTest {
        val fetchUser: suspend () -> String = { "Alice" }
        val fetchAge: suspend () -> Int = { 30 }

        val result = Async {
            lift2 { name: String, age: Int -> "$name($age)" }
                .ap { with(fetchUser.toComputation()) { execute() } }
                .ap { with(fetchAge.toComputation()) { execute() } }
        }
        assertEquals("Alice(30)", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // kotlin.Result ↔ Either
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
    // Result → Validated Computation
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
        assertIs<Either.Left<Nel<String>>>(result)
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
                pure("fast"),
            )
        }
        assertEquals("fast", result)
    }
}
