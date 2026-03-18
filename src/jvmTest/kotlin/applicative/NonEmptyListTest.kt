package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class NonEmptyListTest {

    @Test
    fun `nel wraps single value`() {
        val nel = 42.toNonEmptyList()
        assertEquals(42, nel.head)
        assertEquals(emptyList(), nel.tail)
    }

    @Test
    fun `Nel of creates from varargs`() {
        val nonEmptyList = NonEmptyList.of(1, 2, 3)
        assertEquals(1, nonEmptyList.head)
        assertEquals(listOf(2, 3), nonEmptyList.tail)
    }

    @Test
    fun `Nel of with single element`() {
        val nonEmptyList = NonEmptyList.of("only")
        assertEquals("only", nonEmptyList.head)
        assertEquals(emptyList(), nonEmptyList.tail)
    }

    @Test
    fun `size returns 1 for single element`() {
        assertEquals(1, NonEmptyList(42).size)
    }

    @Test
    fun `size returns head plus tail`() {
        assertEquals(4, NonEmptyList.of(1, 2, 3, 4).size)
    }

    @Test
    fun `get index 0 returns head`() {
        val nonEmptyList = NonEmptyList.of("a", "b", "c")
        assertEquals("a", nonEmptyList[0])
    }

    @Test
    fun `get index gt 0 returns tail element`() {
        val nonEmptyList = NonEmptyList.of("a", "b", "c")
        assertEquals("b", nonEmptyList[1])
        assertEquals("c", nonEmptyList[2])
    }

    @Test
    fun `isEmpty always returns false`() {
        assertFalse(NonEmptyList(1).isEmpty())
        assertFalse(NonEmptyList.of(1, 2, 3).isEmpty())
    }

    @Test
    fun `plus concatenates two Nels preserving order`() {
        val a = NonEmptyList.of(1, 2)
        val b = NonEmptyList.of(3, 4)
        val result = a + b
        assertEquals(listOf(1, 2, 3, 4), result.toList())
    }

    @Test
    fun `plus with single element Nels`() {
        val result = NonEmptyList(1) + NonEmptyList(2)
        assertEquals(listOf(1, 2), result.toList())
    }

    @Test
    fun `toString formats as NonEmptyList(elements)`() {
        assertEquals("NonEmptyList(1, 2, 3)", NonEmptyList.of(1, 2, 3).toString())
        assertEquals("NonEmptyList(42)", NonEmptyList(42).toString())
    }

    @Test
    fun `toList returns all elements in order`() {
        assertEquals(listOf("a", "b", "c"), NonEmptyList.of("a", "b", "c").toList())
    }

    @Test
    fun `can iterate with forEach`() {
        val collected = mutableListOf<Int>()
        NonEmptyList.of(10, 20, 30).forEach { collected.add(it) }
        assertEquals(listOf(10, 20, 30), collected)
    }

    @Test
    fun `can be used in validated computations`() {
        val left: Either<NonEmptyList<String>, Int> = Either.Left(NonEmptyList("error"))
        assertTrue(left is Either.Left)
        assertEquals("error", (left as Either.Left).value.head)
    }

    // ════════════════════════════════════════════════════════════════════════
    // map
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map transforms each element`() {
        val result = NonEmptyList.of(1, 2, 3).map { it * 10 }
        assertEquals(listOf(10, 20, 30), result.toList())
    }

    @Test
    fun `map on single element`() {
        val result = NonEmptyList(5).map { it + 1 }
        assertEquals(NonEmptyList(6), result)
    }

    @Test
    fun `map identity law - map id equals original`() {
        val nel = NonEmptyList.of(1, 2, 3)
        assertEquals(nel, nel.map { it })
    }

    @Test
    fun `map composition law`() {
        val f: (Int) -> String = { "n=$it" }
        val g: (String) -> Int = { it.length }
        val nel = NonEmptyList.of(1, 22, 333)
        assertEquals(nel.map { g(f(it)) }, nel.map(f).map(g))
    }

    // ════════════════════════════════════════════════════════════════════════
    // flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `flatMap expands and flattens`() {
        val result = NonEmptyList.of(1, 2).flatMap { NonEmptyList.of(it, it * 10) }
        assertEquals(listOf(1, 10, 2, 20), result.toList())
    }

    @Test
    fun `flatMap on single element`() {
        val result = NonEmptyList(3).flatMap { NonEmptyList.of(it, it + 1) }
        assertEquals(listOf(3, 4), result.toList())
    }

    @Test
    fun `flatMap associativity law`() {
        val f: (Int) -> NonEmptyList<Int> = { NonEmptyList.of(it, it + 1) }
        val g: (Int) -> NonEmptyList<Int> = { NonEmptyList.of(it * 10) }
        val nel = NonEmptyList.of(1, 2, 3)

        val lhs = nel.flatMap(f).flatMap(g)
        val rhs = nel.flatMap { a -> f(a).flatMap(g) }
        assertEquals(lhs, rhs)
    }

    @Test
    fun `flatMap left identity - pure a flatMap f equals f a`() {
        val f: (Int) -> NonEmptyList<String> = { NonEmptyList.of(it.toString(), "${it}!") }
        val a = 42
        assertEquals(f(a), NonEmptyList(a).flatMap(f))
    }

    @Test
    fun `flatMap right identity - m flatMap pure equals m`() {
        val m = NonEmptyList.of(1, 2, 3)
        assertEquals(m, m.flatMap { NonEmptyList(it) })
    }

    // ════════════════════════════════════════════════════════════════════════
    // zip
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip pairs elements`() {
        val a = NonEmptyList.of(1, 2, 3)
        val b = NonEmptyList.of("a", "b", "c")
        assertEquals(listOf(1 to "a", 2 to "b", 3 to "c"), a.zip(b).toList())
    }

    @Test
    fun `zip truncates to shorter`() {
        val a = NonEmptyList.of(1, 2, 3)
        val b = NonEmptyList.of("x")
        assertEquals(listOf(1 to "x"), a.zip(b).toList())
    }

    @Test
    fun `zip with single-element lists`() {
        val result = NonEmptyList(1).zip(NonEmptyList("a"))
        assertEquals(NonEmptyList(1 to "a"), result)
    }

    @Test
    fun `zip with combine function`() {
        val a = NonEmptyList.of(1, 2)
        val b = NonEmptyList.of(10, 20)
        val result = a.zip(b) { x, y -> x + y }
        assertEquals(listOf(11, 22), result.toList())
    }

    @Test
    fun `zip with combine truncates to shorter`() {
        val a = NonEmptyList.of(1, 2, 3)
        val b = NonEmptyList.of(10, 20)
        val result = a.zip(b) { x, y -> x + y }
        assertEquals(listOf(11, 22), result.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // distinct
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `distinct removes duplicates preserving order`() {
        val result = NonEmptyList.of(1, 2, 1, 3, 2).distinct()
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `distinct on already unique list is identity`() {
        val nel = NonEmptyList.of(1, 2, 3)
        assertEquals(nel, nel.distinct())
    }

    @Test
    fun `distinct on single element`() {
        assertEquals(NonEmptyList(5), NonEmptyList(5).distinct())
    }

    @Test
    fun `distinct head always stays`() {
        val result = NonEmptyList.of(1, 1, 1).distinct()
        assertEquals(1, result.head)
        assertEquals(1, result.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // sortedBy
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sortedBy sorts elements`() {
        val result = NonEmptyList.of(3, 1, 2).sortedBy { it }
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `sortedBy with selector`() {
        val result = NonEmptyList.of("bb", "a", "ccc").sortedBy { it.length }
        assertEquals(listOf("a", "bb", "ccc"), result.toList())
    }

    @Test
    fun `sortedBy on single element`() {
        val nel = NonEmptyList(42)
        assertEquals(nel, nel.sortedBy { it })
    }

    // ════════════════════════════════════════════════════════════════════════
    // reversed
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `reversed reverses elements`() {
        val result = NonEmptyList.of(1, 2, 3).reversed()
        assertEquals(listOf(3, 2, 1), result.toList())
    }

    @Test
    fun `reversed on single element`() {
        assertEquals(NonEmptyList(1), NonEmptyList(1).reversed())
    }

    @Test
    fun `reversed twice is identity`() {
        val nel = NonEmptyList.of(1, 2, 3)
        assertEquals(nel, nel.reversed().reversed())
    }

    // ════════════════════════════════════════════════════════════════════════
    // plus(element)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `plus element appends`() {
        val result = NonEmptyList.of(1, 2) + 3
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `plus element on single-element list`() {
        val result = NonEmptyList(1) + 2
        assertEquals(listOf(1, 2), result.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // fromList
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fromList returns null for empty list`() {
        assertNull(NonEmptyList.fromList(emptyList<Int>()))
    }

    @Test
    fun `fromList wraps single-element list`() {
        val result = NonEmptyList.fromList(listOf(42))
        assertEquals(NonEmptyList(42), result)
    }

    @Test
    fun `fromList wraps multi-element list`() {
        val result = NonEmptyList.fromList(listOf(1, 2, 3))
        assertEquals(NonEmptyList.of(1, 2, 3), result)
    }

    @Test
    fun `fromList preserves order`() {
        val result = NonEmptyList.fromList(listOf(3, 1, 2))!!
        assertEquals(3, result.head)
        assertEquals(listOf(1, 2), result.tail)
    }

    // ════════════════════════════════════════════════════════════════════════
    // traverseSettled / sequenceSettled
    // ════════════════════════════════════════════════════════════════════════

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled collects ALL results including failures`() = runTest {
        val nel = NonEmptyList.of(1, 2, 3, 4, 5)
        val results = Async {
            nel.traverseSettled { i ->
                Computation {
                    if (i % 2 == 0) throw RuntimeException("fail-$i")
                    "ok-$i"
                }
            }
        }

        assertIs<NonEmptyList<Result<String>>>(results)
        assertEquals(5, results.size)
        assertTrue(results[0].isSuccess)
        assertEquals("ok-1", results[0].getOrThrow())
        assertTrue(results[1].isFailure)
        assertEquals("fail-2", results[1].exceptionOrNull()!!.message)
        assertTrue(results[2].isSuccess)
        assertTrue(results[3].isFailure)
        assertTrue(results[4].isSuccess)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled runs in parallel — proven by virtual time`() = runTest {
        val results = Async {
            NonEmptyList.of(1, 2, 3, 4, 5).traverseSettled { i ->
                Computation {
                    delay(50.milliseconds)
                    "done-$i"
                }
            }
        }

        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in 50ms")
        assertTrue(results.all { it.isSuccess })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled does NOT cancel siblings on failure`() = runTest {
        val completed = mutableListOf<Int>()

        val results = Async {
            NonEmptyList.of(1, 2, 3).traverseSettled { i ->
                Computation {
                    delay(if (i == 1) 10.milliseconds else 50.milliseconds)
                    if (i == 1) throw RuntimeException("fast-fail")
                    synchronized(completed) { completed.add(i) }
                    "ok-$i"
                }
            }
        }

        assertEquals(3, results.size)
        assertTrue(results[0].isFailure)
        assertEquals(2, results.count { it.isSuccess })
        assertEquals(listOf(2, 3), completed.sorted())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled bounded respects concurrency limit`() = runTest {
        val results = Async {
            NonEmptyList.of(1, 2, 3, 4, 5, 6).traverseSettled(2) { i ->
                Computation {
                    delay(30.milliseconds)
                    "ok-$i"
                }
            }
        }

        // 6 items / 2 concurrency = 3 batches × 30ms = 90ms
        assertEquals(90L, currentTime)
        assertTrue(results.all { it.isSuccess })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled on single element`() = runTest {
        val results = Async {
            NonEmptyList(42).traverseSettled { i ->
                Computation { i * 2 }
            }
        }

        assertEquals(1, results.size)
        assertEquals(84, results.head.getOrThrow())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sequenceSettled collects all results from pre-built computations`() = runTest {
        val computations = NonEmptyList.of(
            Computation { "a" },
            Computation<String> { throw RuntimeException("boom") },
            Computation { "c" },
        )

        val results = Async { computations.sequenceSettled() }

        assertIs<NonEmptyList<Result<String>>>(results)
        assertEquals(3, results.size)
        assertTrue(results[0].isSuccess)
        assertTrue(results[1].isFailure)
        assertTrue(results[2].isSuccess)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sequenceSettled bounded respects concurrency`() = runTest {
        val computations = NonEmptyList.of(
            Computation { delay(25.milliseconds); "a" },
            Computation { delay(25.milliseconds); "b" },
            Computation { delay(25.milliseconds); "c" },
            Computation { delay(25.milliseconds); "d" },
        )

        val results = Async { computations.sequenceSettled(2) }

        // 4 items / 2 concurrency = 2 batches × 25ms = 50ms
        assertEquals(50L, currentTime)
        assertTrue(results.all { it.isSuccess })
    }
}
