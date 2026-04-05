package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for the [accumulate]/[validated] builder,
 * verifying parallel-within-phases and short-circuit-between-phases semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccumulateBuilderTest {

    data class Identity(val name: String, val email: String, val age: Int)
    data class Clearance(val notBlocked: Boolean, val available: Boolean)
    data class Registration(val identity: Identity, val clearance: Clearance)

    @Test
    fun `accumulate with all valid phases completes successfully`() = runTest {
        val result = accumulate<String, Registration> {
            val identity = zipV(
                { delay(50); Either.Right("Alice") as Either<Nel<String>, String> },
                { delay(60); Either.Right("alice@test.com") as Either<Nel<String>, String> },
                { delay(40); Either.Right(25) as Either<Nel<String>, Int> },
            ) { name, email, age -> Identity(name, email, age) }
                .bindV()

            val clearance = zipV(
                { delay(30); Either.Right(true) as Either<Nel<String>, Boolean> },
                { delay(20); Either.Right(true) as Either<Nel<String>, Boolean> },
            ) { a, b -> Clearance(a, b) }
                .bindV()

            Registration(identity, clearance)
        }.evalGraph()

        assertTrue(result is Either.Right)
        val reg = (result as Either.Right).value
        assertEquals("Alice", reg.identity.name)
    }

    @Test
    fun `accumulate short-circuits on phase 1 failure - phase 2 never runs`() = runTest {
        var phase2Executed = false

        val result = accumulate<String, Registration> {
            val identity = zipV(
                { delay(50); Either.Left(nonEmptyListOf("Name too short")) as Either<Nel<String>, String> },
                { delay(60); Either.Left(nonEmptyListOf("Invalid email")) as Either<Nel<String>, String> },
                { delay(40); Either.Left(nonEmptyListOf("Too young")) as Either<Nel<String>, Int> },
            ) { name, email, age -> Identity(name, email, age) }
                .bindV()

            // Phase 2 should NOT execute
            phase2Executed = true
            val clearance = zipV(
                { delay(30); Either.Right(true) as Either<Nel<String>, Boolean> },
                { delay(20); Either.Right(true) as Either<Nel<String>, Boolean> },
            ) { a, b -> Clearance(a, b) }
                .bindV()

            Registration(identity, clearance)
        }.evalGraph()

        assertTrue(result is Either.Left)
        val errors = (result as Either.Left).value
        assertEquals(3, errors.size, "All 3 phase 1 errors should be collected")
        assertFalse(phase2Executed, "Phase 2 should NOT execute when phase 1 fails")
    }

    @Test
    fun `accumulate parallel within phases - verified by virtual time`() = runTest {
        val result = accumulate<String, Registration> {
            // Phase 1: 3 validators each 50ms → should take ~50ms (parallel)
            val identity = zipV(
                { delay(50); Either.Right("Alice") as Either<Nel<String>, String> },
                { delay(50); Either.Right("alice@test.com") as Either<Nel<String>, String> },
                { delay(50); Either.Right(25) as Either<Nel<String>, Int> },
            ) { name, email, age -> Identity(name, email, age) }
                .bindV()

            // Phase 2: 2 checks each 30ms → should take ~30ms (parallel)
            val clearance = zipV(
                { delay(30); Either.Right(true) as Either<Nel<String>, Boolean> },
                { delay(30); Either.Right(true) as Either<Nel<String>, Boolean> },
            ) { a, b -> Clearance(a, b) }
                .bindV()

            Registration(identity, clearance)
        }.evalGraph()

        assertTrue(result is Either.Right)
        // Phase 1: 50ms + Phase 2: 30ms = 80ms (not 50*3 + 30*2 = 210ms sequential)
        assertEquals(80, currentTime)
    }

    @Test
    fun `accumulate with call for non-validated side effects`() = runTest {
        var sideKapRan = false

        val result = accumulate<String, String> {
            val name = zipV(
                { delay(30); Either.Right("Bob") as Either<Nel<String>, String> },
                { delay(20); Either.Right("bob@test.com") as Either<Nel<String>, String> },
            ) { n, _ -> n }.bindV()

            call { sideKapRan = true; delay(10) }

            name
        }.evalGraph()

        assertTrue(result is Either.Right)
        assertEquals("Bob", (result as Either.Right).value)
        assertTrue(sideKapRan)
    }

    @Test
    fun `validated and accumulate are aliases with identical behavior`() = runTest {
        val v: Either<NonEmptyList<String>, Int> = Either.Right(42)

        val result1 = accumulate<String, Int> { v.bind() }.evalGraph()
        val result2 = validated<String, Int> { v.bind() }.evalGraph()
        assertEquals(result1, result2)
    }

    @Test
    fun `accumulate with phase 1 success and phase 2 failure`() = runTest {
        val result = accumulate<String, String> {
            // Phase 1 succeeds
            val name = zipV(
                { delay(30); Either.Right("Alice") as Either<Nel<String>, String> },
                { delay(30); Either.Right("alice@test.com") as Either<Nel<String>, String> },
            ) { n, e -> "$n|$e" }.bindV()

            // Phase 2 fails
            val check = zipV(
                { delay(20); Either.Left(nonEmptyListOf("blocked")) as Either<Nel<String>, Boolean> },
                { delay(20); Either.Left(nonEmptyListOf("unavailable")) as Either<Nel<String>, Boolean> },
            ) { a, b -> "$a|$b" }.bindV()

            "$name|$check"
        }.evalGraph()

        assertTrue(result is Either.Left)
        val errors = (result as Either.Left).value
        assertEquals(2, errors.size)
    }

    @Test
    fun `accumulate three phases with progressive short-circuit`() = runTest {
        var phase2Ran = false
        var phase3Ran = false

        val result = accumulate<String, Int> {
            // Phase 1: success
            val p1 = zipV(
                { delay(20); Either.Right(1) as Either<Nel<String>, Int> },
                { delay(20); Either.Right(2) as Either<Nel<String>, Int> },
            ) { a, b -> a + b }.bindV()

            // Phase 2: fails
            phase2Ran = true
            val p2 = zipV(
                { delay(10); Either.Left(nonEmptyListOf("err1")) as Either<Nel<String>, Int> },
                { delay(10); Either.Left(nonEmptyListOf("err2")) as Either<Nel<String>, Int> },
            ) { a, b -> a + b }.bindV()

            // Phase 3: should NOT run
            phase3Ran = true
            p1 + p2
        }.evalGraph()

        assertTrue(result is Either.Left)
        assertTrue(phase2Ran, "Phase 2 should run (phase 1 succeeded)")
        assertFalse(phase3Ran, "Phase 3 should NOT run (phase 2 failed)")
    }
}
