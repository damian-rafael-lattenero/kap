package kap

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RaiseTest {

    // ════════════════════════════════════════════════════════════════════════
    // LAWS: either/bind ≡ flatMap composition
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `law bind right returns the value`() {
        val r: Either<String, Int> = Either.Right(42)
        val out: Either<String, Int> = either { r.bind() }
        assertEquals(Either.Right(42), out)
    }

    @Test
    fun `law bind left yields the error as the result`() {
        val l: Either<String, Int> = Either.Left("boom")
        assertEquals(Either.Left("boom"), either<String, Int> { l.bind() })
    }

    @Test
    fun `law composition - either bind chain equals flatMap chain`() {
        val f: (Int) -> Either<String, String> = { if (it > 0) Either.Right("p$it") else Either.Left("neg") }
        val g: (String) -> Either<String, Int> = { Either.Right(it.length) }

        assertEquals(f(5).flatMap(g), either { g(f(5).bind()).bind() })
        assertEquals(f(-1).flatMap(g), either<String, Int> { g(f(-1).bind()).bind() })
    }

    // ════════════════════════════════════════════════════════════════════════
    // LAWS: short-circuit is a failure path (skipped side must NOT run)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `short-circuit - nothing after a raise runs`() {
        var after = false
        val r: Either<String, Int> = either {
            raise("stop")
            after = true
            1
        }
        assertEquals(Either.Left("stop"), r)
        assertFalse(after)
    }

    @Test
    fun `short-circuit - bind of a Left skips the following step`() {
        var after = false
        val r: Either<String, Int> = either {
            Either.Left("boom").bind()
            after = true
            1
        }
        assertEquals(Either.Left("boom"), r)
        assertFalse(after)
    }

    // ════════════════════════════════════════════════════════════════════════
    // THE CE LAW: cancellation and bugs propagate untouched (structural)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `cancellation exception propagates - never captured as Left`() {
        val thrown = assertFailsWith<CancellationException> {
            either<String, Int> { throw CancellationException("cancelled") }
        }
        assertEquals("cancelled", thrown.message)
    }

    @Test
    fun `bugs propagate - a runtime exception is not a Left`() {
        assertFailsWith<IllegalStateException> {
            either<String, Int> { error("bug") }
        }
    }

    @Test
    fun `suspend builder - cancellation mid-suspend propagates`() = runTest {
        assertFailsWith<CancellationException> {
            eitherSuspend<String, Int> {
                delay(10)
                throw CancellationException("mid-suspend")
            }
        }
    }

    @Test
    fun `suspend builder - suspend calls work and the value returns`() = runTest {
        val r: Either<String, String> = eitherSuspend {
            delay(1)
            "done"
        }
        assertEquals(Either.Right("done"), r)
    }

    @Test
    fun `suspend builder - suspend bind of a Left short-circuits`() = runTest {
        val r: Either<String, Int> = eitherSuspend {
            delay(1)
            Either.Left("late").bind()
        }
        assertEquals(Either.Left("late"), r)
    }

    // ════════════════════════════════════════════════════════════════════════
    // OPERATORS: raise / ensure / ensureNotNull
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raise directly from the receiver`() {
        assertEquals(Either.Left("nope"), either<String, Int> { raise("nope") })
    }

    @Test
    fun `ensure continues on true and raises on false`() {
        val ok: Either<String, Int> = either { ensure(1 > 0) { "bad" }; 1 }
        assertEquals(Either.Right(1), ok)
        val bad: Either<String, Int> = either { ensure(1 < 0) { "bad" }; 1 }
        assertEquals(Either.Left("bad"), bad)
    }

    @Test
    fun `ensureNotNull returns the value or raises on null`() {
        val present: Either<String, String> = either { "v".ensureNotNull { "null!" } }
        assertEquals(Either.Right("v"), present)
        assertEquals(Either.Left("null!"), either<String, String> { (null as String?).ensureNotNull { "null!" } })
    }

    @Test
    fun `nested either - an inner raise is caught by the INNER builder`() {
        val outer: Either<String, Either<String, Int>> = either {
            val inner: Either<String, Int> = either { raise("inner") }
            inner
        }
        assertEquals(Either.Right(Either.Left("inner")), outer)
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTEROP: the produced Either is a first-class value downstream
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `interop - fold and getOrElse consume the result`() {
        val ok: Either<String, Int> = either { 41 + 1 }
        assertEquals(42, ok.getOrElse { 0 })
        assertEquals("err", either<String, Int> { raise("err") }.getOrElse { it })
    }
}
