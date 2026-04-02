package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
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
        val nel = nonEmptyListOf(42)
        assertEquals(42, nel.head)
        assertEquals(emptyList(), nel.tail)
    }

    @Test
    fun `Nel of creates from varargs`() {
        val nonEmptyList = nonEmptyListOf(1, 2, 3)
        assertEquals(1, nonEmptyList.head)
        assertEquals(listOf(2, 3), nonEmptyList.tail)
    }

    @Test
    fun `Nel of with single element`() {
        val nonEmptyList = nonEmptyListOf("only")
        assertEquals("only", nonEmptyList.head)
        assertEquals(emptyList(), nonEmptyList.tail)
    }

    @Test
    fun `size returns 1 for single element`() {
        assertEquals(1, nonEmptyListOf(42).size)
    }

    @Test
    fun `size returns head plus tail`() {
        assertEquals(4, nonEmptyListOf(1, 2, 3, 4).size)
    }

    @Test
    fun `get index 0 returns head`() {
        val nonEmptyList = nonEmptyListOf("a", "b", "c")
        assertEquals("a", nonEmptyList[0])
    }

    @Test
    fun `get index gt 0 returns tail element`() {
        val nonEmptyList = nonEmptyListOf("a", "b", "c")
        assertEquals("b", nonEmptyList[1])
        assertEquals("c", nonEmptyList[2])
    }

    @Test
    fun `isEmpty always returns false`() {
        assertFalse(nonEmptyListOf(1).isEmpty())
        assertFalse(nonEmptyListOf(1, 2, 3).isEmpty())
    }

    @Test
    fun `plus concatenates two Nels preserving order`() {
        val a = nonEmptyListOf(1, 2)
        val b = nonEmptyListOf(3, 4)
        val result = a + b
        assertEquals(listOf(1, 2, 3, 4), result.toList())
    }

    @Test
    fun `plus with single element Nels`() {
        val result = nonEmptyListOf(1) + nonEmptyListOf(2)
        assertEquals(listOf(1, 2), result.toList())
    }

    @Test
    fun `toString formats as NonEmptyList(elements)`() {
        assertEquals("NonEmptyList(1, 2, 3)", nonEmptyListOf(1, 2, 3).toString())
        assertEquals("NonEmptyList(42)", nonEmptyListOf(42).toString())
    }

    @Test
    fun `toList returns all elements in order`() {
        assertEquals(listOf("a", "b", "c"), nonEmptyListOf("a", "b", "c").toList())
    }

    @Test
    fun `can iterate with forEach`() {
        val collected = mutableListOf<Int>()
        nonEmptyListOf(10, 20, 30).forEach { collected.add(it) }
        assertEquals(listOf(10, 20, 30), collected)
    }

    @Test
    fun `can be used in validated computations`() {
        val left: Either<NonEmptyList<String>, Int> = Either.Left(nonEmptyListOf("error"))
        assertTrue(left is Either.Left)
        assertEquals("error", (left as Either.Left).value.head)
    }

    // ════════════════════════════════════════════════════════════════════════
    // map
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map transforms each element`() {
        val result = nonEmptyListOf(1, 2, 3).map { it * 10 }
        assertEquals(listOf(10, 20, 30), result.toList())
    }

    @Test
    fun `map on single element`() {
        val result = nonEmptyListOf(5).map { it + 1 }
        assertEquals(nonEmptyListOf(6), result)
    }

    @Test
    fun `map identity law - map id equals original`() {
        val nel = nonEmptyListOf(1, 2, 3)
        assertEquals(nel, nel.map { it })
    }

    @Test
    fun `map composition law`() {
        val f: (Int) -> String = { "n=$it" }
        val g: (String) -> Int = { it.length }
        val nel = nonEmptyListOf(1, 22, 333)
        assertEquals(nel.map { g(f(it)) }, nel.map(f).map(g))
    }

    // ════════════════════════════════════════════════════════════════════════
    // flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `flatMap expands and flattens`() {
        val result = nonEmptyListOf(1, 2).flatMap { nonEmptyListOf(it, it * 10) }
        assertEquals(listOf(1, 10, 2, 20), result.toList())
    }

    @Test
    fun `flatMap on single element`() {
        val result = nonEmptyListOf(3).flatMap { nonEmptyListOf(it, it + 1) }
        assertEquals(listOf(3, 4), result.toList())
    }

    @Test
    fun `flatMap associativity law`() {
        val f: (Int) -> NonEmptyList<Int> = { nonEmptyListOf(it, it + 1) }
        val g: (Int) -> NonEmptyList<Int> = { nonEmptyListOf(it * 10) }
        val nel = nonEmptyListOf(1, 2, 3)

        val lhs = nel.flatMap(f).flatMap(g)
        val rhs = nel.flatMap { a -> f(a).flatMap(g) }
        assertEquals(lhs, rhs)
    }

    @Test
    fun `flatMap left identity - pure a flatMap f equals f a`() {
        val f: (Int) -> NonEmptyList<String> = { nonEmptyListOf(it.toString(), "${it}!") }
        val a = 42
        assertEquals(f(a), nonEmptyListOf(a).flatMap(f))
    }

    @Test
    fun `flatMap right identity - m flatMap pure equals m`() {
        val m = nonEmptyListOf(1, 2, 3)
        assertEquals(m, m.flatMap { nonEmptyListOf(it) })
    }

    // ════════════════════════════════════════════════════════════════════════
    // zip
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip pairs elements`() {
        val a = nonEmptyListOf(1, 2, 3)
        val b = nonEmptyListOf("a", "b", "c")
        assertEquals(listOf(1 to "a", 2 to "b", 3 to "c"), a.zip(b).toList())
    }

    @Test
    fun `zip truncates to shorter`() {
        val a = nonEmptyListOf(1, 2, 3)
        val b = nonEmptyListOf("x")
        assertEquals(listOf(1 to "x"), a.zip(b).toList())
    }

    @Test
    fun `zip with single-element lists`() {
        val result = nonEmptyListOf(1).zip(nonEmptyListOf("a"))
        assertEquals(nonEmptyListOf(1 to "a"), result)
    }

    @Test
    fun `zip with combine function`() {
        val a = nonEmptyListOf(1, 2)
        val b = nonEmptyListOf(10, 20)
        val result = a.zip(b) { x, y -> x + y }
        assertEquals(listOf(11, 22), result.toList())
    }

    @Test
    fun `zip with combine truncates to shorter`() {
        val a = nonEmptyListOf(1, 2, 3)
        val b = nonEmptyListOf(10, 20)
        val result = a.zip(b) { x, y -> x + y }
        assertEquals(listOf(11, 22), result.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // distinct
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `distinct removes duplicates preserving order`() {
        val result = nonEmptyListOf(1, 2, 1, 3, 2).distinct()
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `distinct on already unique list is identity`() {
        val nel = nonEmptyListOf(1, 2, 3)
        assertEquals(nel, nel.distinct())
    }

    @Test
    fun `distinct on single element`() {
        assertEquals(nonEmptyListOf(5), nonEmptyListOf(5).distinct())
    }

    @Test
    fun `distinct head always stays`() {
        val result = nonEmptyListOf(1, 1, 1).distinct()
        assertEquals(1, result.head)
        assertEquals(1, result.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // sorted / reversed (results compared as List since Arrow may return List)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sorted elements`() {
        val result = nonEmptyListOf(3, 1, 2).sorted()
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `sorted with selector via sortedBy`() {
        val result = nonEmptyListOf("bb", "a", "ccc").sortedBy { it.length }
        assertEquals(listOf("a", "bb", "ccc"), result.toList())
    }

    @Test
    fun `sorted on single element`() {
        val nel = nonEmptyListOf(42)
        assertEquals(listOf(42), nel.sorted().toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // reversed
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `reversed reverses elements`() {
        val result = nonEmptyListOf(1, 2, 3).reversed()
        assertEquals(listOf(3, 2, 1), result.toList())
    }

    @Test
    fun `reversed on single element`() {
        assertEquals(listOf(1), nonEmptyListOf(1).reversed().toList())
    }

    @Test
    fun `reversed twice is identity`() {
        val nel = nonEmptyListOf(1, 2, 3)
        assertEquals(nel.toList(), nel.reversed().reversed().toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // plus(element)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `plus element appends`() {
        val result = nonEmptyListOf(1, 2) + 3
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `plus element on single-element list`() {
        val result = nonEmptyListOf(1) + 2
        assertEquals(listOf(1, 2), result.toList())
    }

    // ════════════════════════════════════════════════════════════════════════
    // fromList (via toNonEmptyListOrNull)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fromList returns null for empty list`() {
        assertNull(emptyList<Int>().toNonEmptyListOrNull())
    }

    @Test
    fun `fromList wraps single-element list`() {
        val result = listOf(42).toNonEmptyListOrNull()
        assertEquals(nonEmptyListOf(42), result)
    }

    @Test
    fun `fromList wraps multi-element list`() {
        val result = listOf(1, 2, 3).toNonEmptyListOrNull()
        assertEquals(nonEmptyListOf(1, 2, 3), result)
    }

    @Test
    fun `fromList preserves order`() {
        val result = listOf(3, 1, 2).toNonEmptyListOrNull()!!
        assertEquals(3, result.head)
        assertEquals(listOf(1, 2), result.tail)
    }

    // ════════════════════════════════════════════════════════════════════════
    // traverseSettled / sequenceSettled (from kap-core, works on Iterable)
    // ════════════════════════════════════════════════════════════════════════

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled collects ALL results including failures`() = runTest {
        val nel = nonEmptyListOf(1, 2, 3, 4, 5)
        val results = nel.traverseSettled { i ->
            Kap {
                if (i % 2 == 0) throw RuntimeException("fail-$i")
                "ok-$i"
            }
        }.executeGraph()

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
        val results = nonEmptyListOf(1, 2, 3, 4, 5).traverseSettled { i ->
            Kap {
                delay(50.milliseconds)
                "done-$i"
            }
        }.executeGraph()

        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in 50ms")
        assertTrue(results.all { it.isSuccess })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled does NOT cancel siblings on failure`() = runTest {
        val completed = mutableListOf<Int>()

        val results = nonEmptyListOf(1, 2, 3).traverseSettled { i ->
            Kap {
                delay(if (i == 1) 10.milliseconds else 50.milliseconds)
                if (i == 1) throw RuntimeException("fast-fail")
                synchronized(completed) { completed.add(i) }
                "ok-$i"
            }
        }.executeGraph()

        assertEquals(3, results.size)
        assertTrue(results[0].isFailure)
        assertEquals(2, results.count { it.isSuccess })
        assertEquals(listOf(2, 3), completed.sorted())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled bounded respects concurrency limit`() = runTest {
        val results = nonEmptyListOf(1, 2, 3, 4, 5, 6).traverseSettled(2) { i ->
            Kap {
                delay(30.milliseconds)
                "ok-$i"
            }
        }.executeGraph()

        assertEquals(90L, currentTime)
        assertTrue(results.all { it.isSuccess })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `traverseSettled on single element`() = runTest {
        val results = nonEmptyListOf(42).traverseSettled { i ->
            Kap { i * 2 }
        }.executeGraph()

        assertEquals(1, results.size)
        assertEquals(84, results[0].getOrThrow())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sequenceSettled collects all results from pre-built computations`() = runTest {
        val computations = listOf(
            Kap { "a" },
            Kap<String> { throw RuntimeException("boom") },
            Kap { "c" },
        )

        val results = computations.sequenceSettled().executeGraph()

        assertEquals(3, results.size)
        assertTrue(results[0].isSuccess)
        assertTrue(results[1].isFailure)
        assertTrue(results[2].isSuccess)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sequenceSettled bounded respects concurrency`() = runTest {
        val computations = listOf(
            Kap { delay(25.milliseconds); "a" },
            Kap { delay(25.milliseconds); "b" },
            Kap { delay(25.milliseconds); "c" },
            Kap { delay(25.milliseconds); "d" },
        )

        val results = computations.sequenceSettled(2).executeGraph()

        assertEquals(50L, currentTime)
        assertTrue(results.all { it.isSuccess })
    }
}
