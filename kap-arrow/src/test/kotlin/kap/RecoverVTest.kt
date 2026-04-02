package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecoverVTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. recoverV converts exception to validation error
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV converts exception to validation error`() = runTest {
        val result = Kap<Either<NonEmptyList<String>, Int>> {
            throw RuntimeException("boom")
        }.recoverV { e -> "caught: ${e.message}" }.executeGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(1, result.value.size)
        assertEquals("caught: boom", result.value[0])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. recoverV preserves success
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV preserves success`() = runTest {
        val result = Kap<Either<NonEmptyList<String>, Int>> {
            Either.Right(42)
        }.recoverV { e -> "should not happen: ${e.message}" }.executeGraph()

        assertEquals(Either.Right(42), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. recoverV does not catch CancellationException
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV does not catch CancellationException`() = runTest {
        val result = runCatching {
                        Kap<Either<NonEmptyList<String>, Int>> {
                throw CancellationException("cancelled")
            }.recoverV { "should not catch" }.executeGraph()
        }

        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
        assertEquals("cancelled", result.exceptionOrNull()!!.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. recoverV inside zipV - exception becomes accumulated error
    //    instead of cancelling siblings (barrier proof of concurrency)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV inside zipV - exception becomes accumulated error instead of cancelling siblings`() = runTest {
        val started = (0 until 3).map { CompletableDeferred<Unit>() }

        // Build each branch as a Kap so we can apply recoverV to branch B
        val branchA = Kap<Either<NonEmptyList<String>, String>> {
            started[0].complete(Unit)
            started[1].await(); started[2].await()
            Either.Left(nonEmptyListOf("err-A"))
        }
        val branchB = Kap<Either<NonEmptyList<String>, String>> {
            started[1].complete(Unit)
            started[0].await(); started[2].await()
            throw RuntimeException("err-B")
        }.recoverV { e -> "recovered: ${e.message}" }

        val branchC = Kap<Either<NonEmptyList<String>, String>> {
            started[2].complete(Unit)
            started[0].await(); started[1].await()
            Either.Left(nonEmptyListOf("err-C"))
        }

        val result = kapV<String, String, String, String, String> { a, b, c -> "$a|$b|$c" }
            .withV(branchA)
            .withV(branchB)
            .withV(branchC).executeGraph()

        // All 3 branches ran concurrently (barrier proof — would deadlock otherwise)
        // All 3 errors accumulated instead of B's exception cancelling A and C
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(3, result.value.size)
        assertEquals("err-A", result.value[0])
        assertEquals("recovered: err-B", result.value[1])
        assertEquals("err-C", result.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. recoverV inside zipV - timing proof that siblings are not cancelled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV inside zipV - timing proof that siblings are not cancelled`() = runTest {
        val branchA = Kap<Either<NonEmptyList<String>, String>> {
            delay(50)
            Either.Left(nonEmptyListOf("timeout-A"))
        }
        val branchB = Kap<Either<NonEmptyList<String>, String>> {
            delay(50)
            throw RuntimeException("crash-B")
        }.recoverV { e -> "recovered: ${e.message}" }

        val branchC = Kap<Either<NonEmptyList<String>, String>> {
            delay(50)
            Either.Left(nonEmptyListOf("timeout-C"))
        }

        val result = kapV<String, String, String, String, String> { a, b, c -> "$a|$b|$c" }
            .withV(branchA)
            .withV(branchB)
            .withV(branchC).executeGraph()

        // All 3 run in parallel at 50ms each. If B's exception cancelled siblings,
        // we'd only see 1 error (or a crash). With recoverV, all 3 complete at 50ms.
        assertEquals(50, currentTime,
            "Expected 50ms virtual time (parallel). Got ${currentTime}ms — " +
            "if siblings were cancelled, timing or error count would differ")

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(3, result.value.size)
        assertEquals("timeout-A", result.value[0])
        assertEquals("recovered: crash-B", result.value[1])
        assertEquals("timeout-C", result.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. recoverV with liftV+apV chain - exception in one branch
    //    accumulates with validation errors from others
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV with liftV+apV chain - exception in one branch accumulates with validation errors from others`() = runTest {
        val branchA = Kap<Either<NonEmptyList<String>, String>> {
            Either.Left(nonEmptyListOf("validation-err-A"))
        }

        // This branch throws, but recoverV converts it to a validation error
        val branchB = Kap<Either<NonEmptyList<String>, String>> {
            throw IllegalStateException("service unavailable")
        }.recoverV { e -> "exception: ${e.message}" }

        val branchC = Kap<Either<NonEmptyList<String>, String>> {
            Either.Left(nonEmptyListOf("validation-err-C"))
        }

        val result = kapV<String, String, String, String, String> { a, b, c -> "$a|$b|$c" }
            .withV(branchA)
            .withV(branchB)
            .withV(branchC).executeGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(3, result.value.size)
        assertEquals("validation-err-A", result.value[0])
        assertEquals("exception: service unavailable", result.value[1])
        assertEquals("validation-err-C", result.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. recoverV maps exception to domain error type
    // ════════════════════════════════════════════════════════════════════════

    private sealed class DomainError(val msg: String) {
        class NetworkFailure(val cause: String, val type: String) : DomainError("network: $cause ($type)")
        class ValidationFailure(msg: String) : DomainError(msg)

        override fun equals(other: Any?) = other is DomainError && msg == other.msg
        override fun hashCode() = msg.hashCode()
        override fun toString() = msg
    }

    @Test
    fun `recoverV maps exception to domain error type`() = runTest {
        val result = Kap<Either<NonEmptyList<DomainError>, String>> {
            throw java.net.SocketTimeoutException("Connection timed out after 5000ms")
        }.recoverV { e ->
            // Verify we receive the actual exception and can inspect it
            DomainError.NetworkFailure(
                cause = e.message ?: "unknown",
                type = e::class.simpleName ?: "Unknown"
            )
        }.executeGraph()

        assertIs<Either.Left<NonEmptyList<DomainError>>>(result)
        assertEquals(1, result.value.size)
        val error = result.value[0]
        assertIs<DomainError.NetworkFailure>(error)
        assertEquals("Connection timed out after 5000ms", error.cause)
        assertEquals("SocketTimeoutException", error.type)
        assertEquals("network: Connection timed out after 5000ms (SocketTimeoutException)", error.msg)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. recoverV with andThenV - exception in phase 2 short-circuits
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recoverV with andThenV - exception in phase 2 short-circuits correctly`() = runTest {
        val result = // Phase 1: succeeds with a validated value
        valid<String, Int>(42)
            // Phase 2: andThenV chains into a computation that throws
            .andThenV { n ->
                Kap<Either<NonEmptyList<String>, String>> {
                    throw RuntimeException("phase 2 failed for n=$n")
                }.recoverV { e -> "phase2-error: ${e.message}" }
            }.executeGraph()

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(1, result.value.size)
        assertEquals("phase2-error: phase 2 failed for n=42", result.value[0])
    }

}
