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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [ValidatedScope.bindV] — the shorthand that executes a validated
 * [Kap] and unwraps the result inside [validated] blocks, eliminating
 * the need for nested [Async] calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BindVTest {

    @Test
    fun `bindV unwraps Right from zipV without nested Async`() = runTest {
        val result = Async {
            validated<String, String> {
                val pair = zipV<String, String, Int, Pair<String, Int>>(
                    { Either.Right("Alice") },
                    { Either.Right(30) },
                ) { name, age -> name to age }
                    .bindV()

                "Hello, ${pair.first} (${pair.second})"
            }
        }
        assertEquals(Either.Right("Hello, Alice (30)"), result)
    }

    @Test
    fun `bindV short-circuits on Left from zipV`() = runTest {
        var secondPhaseExecuted = false
        val result = Async {
            validated<String, String> {
                val pair = zipV<String, String, Int, Pair<String, Int>>(
                    { Either.Left(nonEmptyListOf("bad name")) },
                    { Either.Left(nonEmptyListOf("bad age")) },
                ) { name, age -> name to age }
                    .bindV()

                secondPhaseExecuted = true
                "Hello, ${pair.first}"
            }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("bad name", "bad age"), result.value.toList())
        assertEquals(false, secondPhaseExecuted, "Second phase should not execute after bindV short-circuit")
    }

    @Test
    fun `bindV chains multiple parallel phases sequentially`() = runTest {
        val result = Async {
            validated<String, String> {
                // Phase 1: parallel validation
                val identity = zipV<String, String, String, Pair<String, String>>(
                    { delay(30); Either.Right("Alice") },
                    { delay(30); Either.Right("alice@example.com") },
                ) { name, email -> name to email }
                    .bindV()

                // Phase 2: parallel checks using phase 1 results
                val clearance = zipV<String, Boolean, Boolean, Pair<Boolean, Boolean>>(
                    { delay(30); Either.Right(identity.first != "blocked") },
                    { delay(30); Either.Right(identity.second.contains("@")) },
                ) { notBlocked, validEmail -> notBlocked to validEmail }
                    .bindV()

                "Registered: ${identity.first}, cleared=${clearance.first}"
            }
        }
        assertEquals(Either.Right("Registered: Alice, cleared=true"), result)
        // Phase 1: 30ms parallel. Phase 2: 30ms parallel. Total: 60ms.
        assertEquals(60, currentTime, "Two parallel phases should take 60ms total")
    }

    @Test
    fun `bindV short-circuits at first failing phase`() = runTest {
        var phase2Executed = false
        val result = Async {
            validated<String, String> {
                // Phase 1: fails
                val identity = zipV<String, String, String, Pair<String, String>>(
                    { Either.Left(nonEmptyListOf("name too short")) },
                    { Either.Right("alice@example.com") },
                ) { name, email -> name to email }
                    .bindV()

                // Phase 2: should never execute
                phase2Executed = true
                val cleared = zipV<String, Boolean, Boolean, Boolean>(
                    { Either.Right(true) },
                    { Either.Right(true) },
                ) { a, b -> a && b }
                    .bindV()

                "result: $identity, $cleared"
            }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("name too short"), result.value.toList())
        assertEquals(false, phase2Executed, "Phase 2 should not execute when phase 1 fails")
    }

    @Test
    fun `bindV works with single valid computation`() = runTest {
        val result = Async {
            validated<String, Int> {
                val x = valid<String, Int>(42).bindV()
                x * 2
            }
        }
        assertEquals(Either.Right(84), result)
    }

    @Test
    fun `bindV works with single invalid computation`() = runTest {
        val result = Async {
            validated<String, Int> {
                val x = invalid<String, Int>("oops").bindV()
                x * 2
            }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(listOf("oops"), result.value.toList())
    }

    @Test
    fun `bindV preserves accumulated errors across phases`() = runTest {
        val result = Async {
            validated<String, String> {
                // Phase 1: accumulates 3 errors
                zipV<String, String, String, String, Triple<String, String, String>>(
                    { Either.Left(nonEmptyListOf("err1")) },
                    { Either.Left(nonEmptyListOf("err2")) },
                    { Either.Left(nonEmptyListOf("err3")) },
                ) { a, b, c -> Triple(a, b, c) }
                    .bindV()

                "unreachable"
            }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(3, result.value.size)
        assertEquals(listOf("err1", "err2", "err3"), result.value.toList())
    }

    @Test
    fun `bindV interleaves with plain bind`() = runTest {
        val result = Async {
            validated<String, String> {
                // bindV from a validated computation
                val name = valid<String, String>("Alice").bindV()

                // plain bind from an Either value
                val age = Either.Right(nonEmptyListOf(30)).bind()

                "$name is ${age.head}"
            }
        }
        assertEquals(Either.Right("Alice is 30"), result)
    }
}
