@file:KapBridge(ThirdPartyDto::class)

import kap.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ═══════════════════════════════════════════════════════════════════════
//  Test data classes — each annotated with @KapTypeSafe
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
data class SimpleTwo(val name: String, val age: Int)

@KapTypeSafe
data class SimpleThree(val a: String, val b: Int, val c: Boolean)

@KapTypeSafe
data class SingleParam(val value: String)

@KapTypeSafe
data class FiveParams(
    val p1: String,
    val p2: Int,
    val p3: Boolean,
    val p4: Double,
    val p5: Long,
)

@KapTypeSafe
data class WithNullable(val required: String, val optional: String?)

@KapTypeSafe
data class WithGeneric(val items: List<String>, val count: Int)

@KapTypeSafe
data class AllSameType(val first: String, val second: String, val third: String)

@KapTypeSafe
data class PhaseDemo(
    val user: String,
    val cart: String,
    val validated: Boolean,
    val shipping: Double,
    val tax: Double,
)

// ── Function annotations ────────────────────────────────────────────

@KapTypeSafe
fun buildGreeting(name: String, age: Int): String = "Hello $name, you are $age"

@KapTypeSafe(prefix = "PrefixA")
fun buildA(x: String, y: Int): String = "$x-$y"

@KapTypeSafe(prefix = "PrefixB")
fun buildB(x: String, y: Int): String = "$x+$y"

// ═══════════════════════════════════════════════════════════════════════
//  Tests
// ═══════════════════════════════════════════════════════════════════════

class KapTypeSafeTest {

    // ── Basic: 2 params ─────────────────────────────────────────────

    @Test
    fun `named builders work for 2-param class`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .executeGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    // ── Basic: 3 params ─────────────────────────────────────────────

    @Test
    fun `named builders work for 3-param class`() = runTest {
        val result = kap(::SimpleThree)
            .withA { "hello" }
            .withB { 42 }
            .withC { true }
            .executeGraph()

        assertEquals(SimpleThree("hello", 42, true), result)
    }

    // ── Single param ────────────────────────────────────────────────

    @Test
    fun `single param class works`() = runTest {
        val result = kap(::SingleParam)
            .withValue { "only-one" }
            .executeGraph()

        assertEquals(SingleParam("only-one"), result)
    }

    // ── 5 params ────────────────────────────────────────────────────

    @Test
    fun `5-param class works`() = runTest {
        val result = kap(::FiveParams)
            .withP1 { "str" }
            .withP2 { 42 }
            .withP3 { true }
            .withP4 { 3.14 }
            .withP5 { 999L }
            .executeGraph()

        assertEquals(FiveParams("str", 42, true, 3.14, 999L), result)
    }

    // ── Parallel execution ──────────────────────────────────────────

    @Test
    fun `withX runs in parallel`() = runTest {
        val result = kap(::SimpleThree)
            .withA { delay(50); "a" }
            .withB { delay(50); 1 }
            .withC { delay(50); true }
            .executeGraph()

        assertEquals(SimpleThree("a", 1, true), result)
    }

    // ── Phase barriers with thenX ───────────────────────────────────

    @Test
    fun `thenX creates phase barrier`() = runTest {
        val result = kap(::PhaseDemo)
            .withUser { delay(30); "Alice" }        // ┐ phase 1: parallel
            .withCart { delay(30); "3 items" }       // ┘
            .thenValidated { delay(10); true }       // ── barrier
            .withShipping { delay(20); 9.99 }        // ┐ phase 2: parallel
            .withTax { delay(20); 1.50 }             // ┘
            .executeGraph()

        assertEquals(PhaseDemo("Alice", "3 items", true, 9.99, 1.50), result)
    }

    // ── All same type (the core safety guarantee) ───────────────────

    @Test
    fun `all same type params have distinct named methods`() = runTest {
        val result = kap(::AllSameType)
            .withFirst { "one" }
            .withSecond { "two" }
            .withThird { "three" }
            .executeGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
        // Can't accidentally swap because each step has a unique method name
    }

    // ── Nullable params ─────────────────────────────────────────────

    @Test
    fun `nullable param with value`() = runTest {
        val result = kap(::WithNullable)
            .withRequired { "hello" }
            .withOptional { "world" }
            .executeGraph()

        assertEquals(WithNullable("hello", "world"), result)
    }

    @Test
    fun `nullable param with null`() = runTest {
        val result = kap(::WithNullable)
            .withRequired { "hello" }
            .withOptional { null }
            .executeGraph()

        assertEquals(WithNullable("hello", null), result)
    }

    // ── Generic params ──────────────────────────────────────────────

    @Test
    fun `generic param types work`() = runTest {
        val result = kap(::WithGeneric)
            .withItems { listOf("a", "b", "c") }
            .withCount { 3 }
            .executeGraph()

        assertEquals(WithGeneric(listOf("a", "b", "c"), 3), result)
    }

    // ── Kap overload (passing pre-built Kap instead of lambda) ──────

    @Test
    fun `withX accepts Kap value`() = runTest {
        val nameKap = Kap { delay(30); "Alice" }
        val ageKap = Kap { delay(20); 30 }

        val result = kap(::SimpleTwo)
            .withName(nameKap)
            .withAge(ageKap)
            .executeGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `thenX accepts Kap value`() = runTest {
        val validatedKap = Kap { delay(10); true }

        val result = kap(::PhaseDemo)
            .withUser { "Alice" }
            .withCart { "items" }
            .thenValidated(validatedKap)
            .withShipping { 5.0 }
            .withTax { 1.0 }
            .executeGraph()

        assertEquals(PhaseDemo("Alice", "items", true, 5.0, 1.0), result)
    }

    // ── Function annotation ─────────────────────────────────────────

    @Test
    fun `annotated function generates named builders`() = runTest {
        val result = kap(BuildGreeting)
            .withName { "Bob" }
            .withAge { 25 }
            .executeGraph()

        assertEquals("Hello Bob, you are 25", result)
    }

    // ── Prefix collision avoidance ──────────────────────────────────

    @Test
    fun `prefix avoids name collision between functions with same param names`() = runTest {
        val a = kap(BuildA)
            .withPrefixAX { "hello" }
            .withPrefixAY { 1 }
            .executeGraph()

        val b = kap(BuildB)
            .withPrefixBX { "hello" }
            .withPrefixBY { 1 }
            .executeGraph()

        assertEquals("hello-1", a)
        assertEquals("hello+1", b)
    }

    // ── @KapBridge for third-party classes ───────────────────────────

    @Test
    fun `KapBridge generates builders for third-party class`() = runTest {
        val result = kap(::ThirdPartyDto)
            .withId { 42 }
            .withName { "bridged" }
            .withActive { true }
            .executeGraph()

        assertEquals(ThirdPartyDto(42, "bridged", true), result)
    }

    // ── Composition: andThen after last step ────────────────────────

    @Test
    fun `last step returns Kap so andThen works natively`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .andThen { user ->
                Kap.of("${user.name} is ${user.age}")
            }
            .executeGraph()

        assertEquals("Alice is 30", result)
    }

    // ── Composition: map after last step ────────────────────────────

    @Test
    fun `last step returns Kap so map works natively`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .map { "${it.name}(${it.age})" }
            .executeGraph()

        assertEquals("Alice(30)", result)
    }

    // ── Error propagation ───────────────────────────────────────────

    @Test
    fun `exception in one branch cancels siblings`() = runTest {
        val result = runCatching {
            kap(::SimpleTwo)
                .withName { delay(100); "should be cancelled" }
                .withAge { throw IllegalStateException("boom") }
                .executeGraph()
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    // ── Resilience combinators compose with named builders ──────────

    @Test
    fun `recover composes with named builder result`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .recover { SimpleTwo("fallback", 0) }
            .executeGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `timeout composes with named builder result`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { delay(10); "Alice" }
            .withAge { delay(10); 30 }
            .timeout(1000.milliseconds)
            .executeGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    // ── Memoize composes with named builders ────────────────────────

    @Test
    fun `memoize works with named builder result`() = runTest {
        var callCount = 0
        val memoized = kap(::SimpleTwo)
            .withName { callCount++; "Alice" }
            .withAge { 30 }
            .memoize()

        val r1 = memoized.executeGraph()
        val r2 = memoized.executeGraph()

        assertEquals(SimpleTwo("Alice", 30), r1)
        assertEquals(r1, r2)
        assertEquals(1, callCount, "Should only execute once due to memoize")
    }

    // ── Settled composes ────────────────────────────────────────────

    @Test
    fun `settled wraps result without cancelling`() = runTest {
        val result = kap(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .settled()
            .executeGraph()

        assertTrue(result.isSuccess)
        assertEquals(SimpleTwo("Alice", 30), result.getOrNull())
    }

    // ── Multiple barriers ───────────────────────────────────────────

    @Test
    fun `multiple barriers chain correctly`() = runTest {
        val result = kap(::FiveParams)
            .withP1 { delay(10); "a" }
            .thenP2 { delay(10); 1 }        // barrier 1
            .withP3 { delay(10); true }
            .thenP4 { delay(10); 2.0 }       // barrier 2
            .withP5 { delay(10); 3L }
            .executeGraph()

        assertEquals(FiveParams("a", 1, true, 2.0, 3L), result)
    }
}

// ── Helpers used by @KapBridge test ─────────────────────────────────
// ThirdPartyDto is defined in Main.kt
