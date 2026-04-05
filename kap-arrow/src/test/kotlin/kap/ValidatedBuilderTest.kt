package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatedBuilderTest {

    @Test
    fun `validated returns Right on success`() = runTest {
        val result = validated<String, Int> {
            val a = Either.Right(1).bind()
            val b = Either.Right(2).bind()
            a + b
        }.evalGraph()
        assertEquals(Either.Right(3), result)
    }

    @Test
    fun `validated short-circuits on Left`() = runTest {
        var secondCalled = false
        val result = validated<String, Int> {
            val a: Int = (Either.Left(nonEmptyListOf("error1")) as Either<NonEmptyList<String>, Int>).bind()
            secondCalled = true
            a + 1
        }.evalGraph()
        assertTrue(result is Either.Left)
        assertEquals(nonEmptyListOf("error1"), (result as Either.Left).value)
        assertEquals(false, secondCalled)
    }

    @Test
    fun `validated short-circuits on first Left`() = runTest {
        val calls = mutableListOf<String>()
        val result = validated<String, Int> {
            calls.add("first")
            val a: Int = Either.Right(1).bind()
            calls.add("second")
            val b: Int = (Either.Left(nonEmptyListOf("err")) as Either<NonEmptyList<String>, Int>).bind()
            calls.add("third")
            a + b
        }.evalGraph()
        assertEquals(listOf("first", "second"), calls)
        assertTrue(result is Either.Left)
    }

    @Test
    fun `validated preserves all errors from Left`() = runTest {
        val errors = nonEmptyListOf("err1", "err2", "err3")
        val result = validated<String, Int> {
            (Either.Left(errors) as Either<NonEmptyList<String>, Int>).bind()
        }.evalGraph()
        assertEquals(Either.Left(errors), result)
    }

    @Test
    fun `validated composes with zipV — parallel then sequential`() = runTest {
        val result = validated<String, String> {
            // Phase 1: parallel validation via zipV
            val pair: Pair<String, Int> = zipV<String, String, Int, Pair<String, Int>>(
                    { Either.Right("Alice") },
                    { Either.Right(30) },
                ) { name, age -> Pair(name, age) }
                .evalGraph().bind()
            // Phase 2: sequential check using phase 1 result
            val greeting: String = Either.Right("Hello, ${pair.first}!").bind()
            greeting
        }.evalGraph()
        assertEquals(Either.Right("Hello, Alice!"), result)
    }

    @Test
    fun `validated composes with zipV — fails in parallel phase`() = runTest {
        val result = validated<String, String> {
            val pair: Pair<String, Int> = zipV<String, String, Int, Pair<String, Int>>(
                    { Either.Left(nonEmptyListOf("bad name")) },
                    { Either.Left(nonEmptyListOf("bad age")) },
                ) { name, age -> Pair(name, age) }
                .evalGraph().bind()
            "Hello, ${pair.first}"
        }.evalGraph()
        assertTrue(result is Either.Left)
        val errors = (result as Either.Left).value
        assertEquals(2, errors.size)
    }

    @Test
    fun `validated propagates CancellationException`() = runTest {
        val comp = validated<String, String> {
            delay(Long.MAX_VALUE)
            "never"
        }
        val job = launch {
            @Suppress("UNUSED_VARIABLE")
            val ignored = comp.evalGraph()
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }
}
