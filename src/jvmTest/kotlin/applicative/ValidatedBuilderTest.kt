package applicative

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatedBuilderTest {

    @Test
    fun `validated returns Right on success`() = runTest {
        val result = Async {
            validated<String, Int> {
                val a = Either.Right(1).bind()
                val b = Either.Right(2).bind()
                a + b
            }
        }
        assertEquals(Either.Right(3), result)
    }

    @Test
    fun `validated short-circuits on Left`() = runTest {
        var secondCalled = false
        val result = Async {
            validated<String, Int> {
                val a: Int = (Either.Left(NonEmptyList.of("error1")) as Either<NonEmptyList<String>, Int>).bind()
                secondCalled = true
                a + 1
            }
        }
        assertTrue(result.isLeft)
        assertEquals(NonEmptyList.of("error1"), (result as Either.Left).value)
        assertEquals(false, secondCalled)
    }

    @Test
    fun `validated short-circuits on first Left`() = runTest {
        val calls = mutableListOf<String>()
        val result = Async {
            validated<String, Int> {
                calls.add("first")
                val a: Int = Either.Right(1).bind()
                calls.add("second")
                val b: Int = (Either.Left(NonEmptyList.of("err")) as Either<NonEmptyList<String>, Int>).bind()
                calls.add("third")
                a + b
            }
        }
        assertEquals(listOf("first", "second"), calls)
        assertTrue(result.isLeft)
    }

    @Test
    fun `validated preserves all errors from Left`() = runTest {
        val errors = NonEmptyList.of("err1", "err2", "err3")
        val result = Async {
            validated<String, Int> {
                (Either.Left(errors) as Either<NonEmptyList<String>, Int>).bind()
            }
        }
        assertEquals(Either.Left(errors), result)
    }

    @Test
    fun `validated composes with zipV — parallel then sequential`() = runTest {
        val result = Async {
            validated<String, String> {
                // Phase 1: parallel validation via zipV
                val pair: Pair<String, Int> = Async {
                    zipV<String, String, Int, Pair<String, Int>>(
                        { Either.Right("Alice") },
                        { Either.Right(30) },
                    ) { name, age -> Pair(name, age) }
                }.bind()
                // Phase 2: sequential check using phase 1 result
                val greeting: String = Either.Right("Hello, ${pair.first}!").bind()
                greeting
            }
        }
        assertEquals(Either.Right("Hello, Alice!"), result)
    }

    @Test
    fun `validated composes with zipV — fails in parallel phase`() = runTest {
        val result = Async {
            validated<String, String> {
                val pair: Pair<String, Int> = Async {
                    zipV<String, String, Int, Pair<String, Int>>(
                        { Either.Left(NonEmptyList.of("bad name")) },
                        { Either.Left(NonEmptyList.of("bad age")) },
                    ) { name, age -> Pair(name, age) }
                }.bind()
                "Hello, ${pair.first}"
            }
        }
        assertTrue(result.isLeft)
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
            val ignored = Async { comp }
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }
}
