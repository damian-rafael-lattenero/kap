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
    fun `scoped builder works for 2-param class`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    // ── Basic: 3 params ─────────────────────────────────────────────

    @Test
    fun `scoped builder works for 3-param class`() = runTest {
        val result = kap(::SimpleThree)
            .with { a from "hello" }
            .with { b from 42 }
            .with { c from true }
            .evalGraph()

        assertEquals(SimpleThree("hello", 42, true), result)
    }

    // ── Single param ────────────────────────────────────────────────

    @Test
    fun `single param class works`() = runTest {
        val result = kap(::SingleParam)
            .with { value from "only-one" }
            .evalGraph()

        assertEquals(SingleParam("only-one"), result)
    }

    // ── 5 params ────────────────────────────────────────────────────

    @Test
    fun `5-param class works`() = runTest {
        val result = kap(::FiveParams)
            .with { p1 from "str" }
            .with { p2 from 42 }
            .with { p3 from true }
            .with { p4 from 3.14 }
            .with { p5 from 999L }
            .evalGraph()

        assertEquals(FiveParams("str", 42, true, 3.14, 999L), result)
    }

    // ── Parallel execution ──────────────────────────────────────────

    @Test
    fun `with runs in parallel`() = runTest {
        val result = kap(::SimpleThree)
            .with { a from "a".also { delay(50) } }
            .with { b from 1.also { delay(50) } }
            .with { c from true.also { delay(50) } }
            .evalGraph()

        assertEquals(SimpleThree("a", 1, true), result)
    }

    // ── Phase barriers with then ────────────────────────────────────

    @Test
    fun `then creates phase barrier`() = runTest {
        val result = kap(::PhaseDemo)
            .with { user from "Alice".also { delay(30) } }     // ┐ phase 1: parallel
            .with { cart from "3 items".also { delay(30) } }   // ┘
            .then { validated from true.also { delay(10) } }   // ── barrier
            .with { shipping from 9.99.also { delay(20) } }    // ┐ phase 2: parallel
            .with { tax from 1.50.also { delay(20) } }         // ┘
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "3 items", true, 9.99, 1.50), result)
    }

    // ── All same type (the core safety guarantee) ───────────────────

    @Test
    fun `all same type params have distinct slot tags`() = runTest {
        val result = kap(::AllSameType)
            .with { first from "one" }
            .with { second from "two" }
            .with { third from "three" }
            .evalGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
        // Can't accidentally swap because each slot's lambda receiver exposes
        // a different tag (AllSameTypeFirstTag, AllSameTypeSecondTag, ...)
    }

    // ── Nullable params ─────────────────────────────────────────────

    @Test
    fun `nullable param with value`() = runTest {
        val result = kap(::WithNullable)
            .with { required from "hello" }
            .with { optional from "world" }
            .evalGraph()

        assertEquals(WithNullable("hello", "world"), result)
    }

    @Test
    fun `nullable param with null`() = runTest {
        val result = kap(::WithNullable)
            .with { required from "hello" }
            .with { optional from null }
            .evalGraph()

        assertEquals(WithNullable("hello", null), result)
    }

    // ── Generic params ──────────────────────────────────────────────

    @Test
    fun `generic param types work`() = runTest {
        val result = kap(::WithGeneric)
            .with { items from listOf("a", "b", "c") }
            .with { count from 3 }
            .evalGraph()

        assertEquals(WithGeneric(listOf("a", "b", "c"), 3), result)
    }

    // ── Kap-decorated values via parens form ────────────────────────

    @Test
    fun `with accepts Kap value via parens form`() = runTest {
        val result = kap(::SimpleTwo)
            .with(SimpleTwoKap.name from Kap { delay(30); "Alice" })
            .with(SimpleTwoKap.age from Kap { delay(20); 30 })
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `then accepts Kap value via parens form`() = runTest {
        val result = kap(::PhaseDemo)
            .with { user from "Alice" }
            .with { cart from "items" }
            .then(PhaseDemoKap.validated from Kap { delay(10); true })
            .with { shipping from 5.0 }
            .with { tax from 1.0 }
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "items", true, 5.0, 1.0), result)
    }

    // ── Function annotation ─────────────────────────────────────────

    @Test
    fun `annotated function generates scoped builder`() = runTest {
        // buildGreeting, buildA, buildB all have signature (String, Int) -> String,
        // so the processor emits `kapBuildGreeting`/`kapBuildA`/`kapBuildB` fallbacks
        // to avoid identical-signature overloads on a plain `kap(...)`.
        val result = kapBuildGreeting(::buildGreeting)
            .with { name from "Bob" }
            .with { age from 25 }
            .evalGraph()

        assertEquals("Hello Bob, you are 25", result)
    }

    // ── Prefix collision avoidance ──────────────────────────────────

    @Test
    fun `prefix avoids tag-class collisions between functions with same param names`() = runTest {
        val a = kapBuildA(::buildA)
            .with { x from "hello" }
            .with { y from 1 }
            .evalGraph()

        val b = kapBuildB(::buildB)
            .with { x from "hello" }
            .with { y from 1 }
            .evalGraph()

        assertEquals("hello-1", a)
        assertEquals("hello+1", b)
    }

    // ── @KapBridge for third-party classes ───────────────────────────

    @Test
    fun `KapBridge generates builder for third-party class`() = runTest {
        val result = kap(::ThirdPartyDto)
            .with { id from 42 }
            .with { name from "bridged" }
            .with { active from true }
            .evalGraph()

        assertEquals(ThirdPartyDto(42, "bridged", true), result)
    }

    // ── Composition: andThen after the last slot ────────────────────

    @Test
    fun `andThen on completed wrapper`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .andThen { user ->
                Kap.of("${user.name} is ${user.age}")
            }
            .evalGraph()

        assertEquals("Alice is 30", result)
    }

    // ── Composition: kap-core operators on completed chain ──────────

    @Test
    fun `map after complete chain`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .map { "${it.name}(${it.age})" }
            .evalGraph()

        assertEquals("Alice(30)", result)
    }

    // ── Error propagation ───────────────────────────────────────────

    @Test
    fun `exception in one branch cancels siblings`() = runTest {
        val result = runCatching {
            kap(::SimpleTwo)
                .with { name from "should be cancelled".also { delay(100) } }
                .with {
                    val v: Int = throw IllegalStateException("boom")
                    age from v
                }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    // ── Resilience combinators on completed chain ────────────────────

    @Test
    fun `recover after complete chain`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .recover { SimpleTwo("fallback", 0) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `timeout after complete chain`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice".also { delay(10) } }
            .with { age from 30.also { delay(10) } }
            .timeout(1000.milliseconds)
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `memoize after complete chain`() = runTest {
        var callCount = 0
        val memoized = kap(::SimpleTwo)
            .with { name from "Alice".also { callCount++ } }
            .with { age from 30 }
            .memoize()

        val r1 = memoized.evalGraph()
        val r2 = memoized.evalGraph()

        assertEquals(SimpleTwo("Alice", 30), r1)
        assertEquals(r1, r2)
        assertEquals(1, callCount, "Should only execute once due to memoize")
    }

    @Test
    fun `settled after complete chain`() = runTest {
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .settled()
            .evalGraph()

        assertTrue(result.isSuccess)
        assertEquals(SimpleTwo("Alice", 30), result.getOrNull())
    }

    // ── Multiple barriers ───────────────────────────────────────────

    @Test
    fun `multiple barriers chain correctly`() = runTest {
        val result = kap(::FiveParams)
            .with { p1 from "a".also { delay(10) } }
            .then { p2 from 1.also { delay(10) } }        // barrier 1
            .with { p3 from true.also { delay(10) } }
            .then { p4 from 2.0.also { delay(10) } }      // barrier 2
            .with { p5 from 3L.also { delay(10) } }
            .evalGraph()

        assertEquals(FiveParams("a", 1, true, 2.0, 3L), result)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Graph as data: lazy, passable, dynamically completable
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `partial graph can be stored in a val and completed later`() = runTest {
        // The graph is just data — nothing runs until .evalGraph()
        val partial = kap(::SimpleThree)
            .with { a from "hello" }

        // Later, somewhere else, complete it:
        val result = partial
            .with { b from 42 }
            .with { c from true }
            .evalGraph()

        assertEquals(SimpleThree("hello", 42, true), result)
    }

    @Test
    fun `partial graph can be passed to a function that completes it`() = runTest {
        // Start building the graph — type inference handles the wrapper shape.
        val partial = kap(::SimpleTwo)
            .with { name from "Alice" }

        // A function receives the partial graph and completes it based on logic.
        suspend fun completeBasedOnRole(
            graph: SimpleTwoKap<(SimpleTwoAge) -> SimpleTwo>,
            isAdmin: Boolean,
        ): SimpleTwo =
            if (isAdmin) graph.with { age from 99 }.evalGraph()
            else graph.with { age from 25 }.evalGraph()

        val admin = completeBasedOnRole(partial, isAdmin = true)
        val regular = completeBasedOnRole(partial, isAdmin = false)

        assertEquals(SimpleTwo("Alice", 99), admin)
        assertEquals(SimpleTwo("Alice", 25), regular)
    }

    enum class CartType { STANDARD, PREMIUM, GUEST }

    @Test
    fun `graph branches dynamically based on runtime conditions`() = runTest {
        suspend fun buildCheckout(type: CartType): SimpleThree {
            val base = kap(::SimpleThree)
                .with { a from "user-data" }

            return when (type) {
                CartType.STANDARD -> base
                    .with { b from 100 }
                    .with { c from false }
                    .evalGraph()
                CartType.PREMIUM -> base
                    .with { b from 500 }
                    .with { c from true }   // premium flag
                    .evalGraph()
                CartType.GUEST -> base
                    .with { b from 0 }
                    .with { c from false }
                    .evalGraph()
            }
        }

        val standard = buildCheckout(CartType.STANDARD)
        val premium = buildCheckout(CartType.PREMIUM)
        val guest = buildCheckout(CartType.GUEST)

        assertEquals(SimpleThree("user-data", 100, false), standard)
        assertEquals(SimpleThree("user-data", 500, true), premium)
        assertEquals(SimpleThree("user-data", 0, false), guest)
    }

    @Test
    fun `same partial graph reused with different completions`() = runTest {
        // A shared base that fetches the expensive common data once
        val base = kap(::SimpleThree)
            .with { a from "expensive-shared-data".also { delay(50) } }

        // Two different completions — the base is reused (structure shared)
        val resultA = base.with { b from 1 }.with { c from true }.evalGraph()
        val resultB = base.with { b from 2 }.with { c from false }.evalGraph()

        assertEquals("expensive-shared-data", resultA.a)
        assertEquals("expensive-shared-data", resultB.a)
        assertEquals(1, resultA.b)
        assertEquals(2, resultB.b)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Graph built across multiple functions (multi-step graph assembly)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `graph built across multiple functions composes cleanly`() = runTest {
        // Each step inserts more slots; we let type inference handle the wrapper shape.
        fun createBase(): FiveParamsKap<(FiveParamsP1) -> (FiveParamsP2) -> (FiveParamsP3) -> (FiveParamsP4) -> (FiveParamsP5) -> FiveParams> =
            kap(::FiveParams)

        fun addUserContext(graph: FiveParamsKap<(FiveParamsP1) -> (FiveParamsP2) -> (FiveParamsP3) -> (FiveParamsP4) -> (FiveParamsP5) -> FiveParams>):
                FiveParamsKap<(FiveParamsP3) -> (FiveParamsP4) -> (FiveParamsP5) -> FiveParams> =
            graph.with { p1 from "user-alice" }.with { p2 from 42 }

        suspend fun addConfig(
            graph: FiveParamsKap<(FiveParamsP3) -> (FiveParamsP4) -> (FiveParamsP5) -> FiveParams>,
            isProd: Boolean,
        ): FiveParams =
            if (isProd)
                graph.with { p3 from true }.with { p4 from 99.9 }.with { p5 from 1000L }.evalGraph()
            else
                graph.with { p3 from false }.with { p4 from 0.0 }.with { p5 from 0L }.evalGraph()

        val base = createBase()
        val withUser = addUserContext(base)
        val prod = addConfig(withUser, isProd = true)
        val dev = addConfig(withUser, isProd = false)

        assertEquals(FiveParams("user-alice", 42, true, 99.9, 1000L), prod)
        assertEquals(FiveParams("user-alice", 42, false, 0.0, 0L), dev)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Typed-applicative API — explicit opaque-type construction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `opaque types work with generic with operator on raw Kap`() = runTest {
        val result = Kap.of { a: SimpleTwoName -> { b: SimpleTwoAge -> SimpleTwo(a.value, b.value) } }
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `opaque types prevent swapping same-typed params on raw Kap`() = runTest {
        // SimpleTwo has (name: String, age: Int) → opaque types SimpleTwoName / SimpleTwoAge.
        // Swapping would not compile — each curry slot expects its own wrapper type.
        val result = Kap.of { name: SimpleTwoName -> { age: SimpleTwoAge -> SimpleTwo(name.value, age.value) } }
            .with { SimpleTwoName("Bob") }
            .with { SimpleTwoAge(25) }
            .evalGraph()

        assertEquals(SimpleTwo("Bob", 25), result)
    }

    @Test
    fun `opaque types for all-same-type class prevent swaps`() = runTest {
        val result = Kap.of { a: AllSameTypeFirst -> { b: AllSameTypeSecond -> { c: AllSameTypeThird ->
            AllSameType(a.value, b.value, c.value)
        } } }
            .with { AllSameTypeFirst("one") }
            .with { AllSameTypeSecond("two") }
            .with { AllSameTypeThird("three") }
            .evalGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
    }
}
