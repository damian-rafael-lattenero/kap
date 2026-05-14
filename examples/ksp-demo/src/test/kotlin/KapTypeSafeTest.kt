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
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    // ── Basic: 3 params ─────────────────────────────────────────────

    @Test
    fun `named builders work for 3-param class`() = runTest {
        val result = kapDsl(::SimpleThree)
            .withA { "hello" }
            .withB { 42 }
            .withC { true }
            .evalGraph()

        assertEquals(SimpleThree("hello", 42, true), result)
    }

    // ── Single param ────────────────────────────────────────────────

    @Test
    fun `single param class works`() = runTest {
        val result = kapDsl(::SingleParam)
            .withValue { "only-one" }
            .evalGraph()

        assertEquals(SingleParam("only-one"), result)
    }

    // ── 5 params ────────────────────────────────────────────────────

    @Test
    fun `5-param class works`() = runTest {
        val result = kapDsl(::FiveParams)
            .withP1 { "str" }
            .withP2 { 42 }
            .withP3 { true }
            .withP4 { 3.14 }
            .withP5 { 999L }
            .evalGraph()

        assertEquals(FiveParams("str", 42, true, 3.14, 999L), result)
    }

    // ── Parallel execution ──────────────────────────────────────────

    @Test
    fun `withX runs in parallel`() = runTest {
        val result = kapDsl(::SimpleThree)
            .withA { delay(50); "a" }
            .withB { delay(50); 1 }
            .withC { delay(50); true }
            .evalGraph()

        assertEquals(SimpleThree("a", 1, true), result)
    }

    // ── Phase barriers with thenX ───────────────────────────────────

    @Test
    fun `thenX creates phase barrier`() = runTest {
        val result = kapDsl(::PhaseDemo)
            .withUser { delay(30); "Alice" }        // ┐ phase 1: parallel
            .withCart { delay(30); "3 items" }       // ┘
            .thenValidated { delay(10); true }       // ── barrier
            .withShipping { delay(20); 9.99 }        // ┐ phase 2: parallel
            .withTax { delay(20); 1.50 }             // ┘
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "3 items", true, 9.99, 1.50), result)
    }

    // ── All same type (the core safety guarantee) ───────────────────

    @Test
    fun `all same type params have distinct named methods`() = runTest {
        val result = kapDsl(::AllSameType)
            .withFirst { "one" }
            .withSecond { "two" }
            .withThird { "three" }
            .evalGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
        // Can't accidentally swap because each step has a unique method name
    }

    // ── Nullable params ─────────────────────────────────────────────

    @Test
    fun `nullable param with value`() = runTest {
        val result = kapDsl(::WithNullable)
            .withRequired { "hello" }
            .withOptional { "world" }
            .evalGraph()

        assertEquals(WithNullable("hello", "world"), result)
    }

    @Test
    fun `nullable param with null`() = runTest {
        val result = kapDsl(::WithNullable)
            .withRequired { "hello" }
            .withOptional { null }
            .evalGraph()

        assertEquals(WithNullable("hello", null), result)
    }

    // ── Generic params ──────────────────────────────────────────────

    @Test
    fun `generic param types work`() = runTest {
        val result = kapDsl(::WithGeneric)
            .withItems { listOf("a", "b", "c") }
            .withCount { 3 }
            .evalGraph()

        assertEquals(WithGeneric(listOf("a", "b", "c"), 3), result)
    }

    // ── Kap overload (passing pre-built Kap instead of lambda) ──────

    @Test
    fun `withX accepts Kap value`() = runTest {
        val nameKap = Kap { delay(30); "Alice" }
        val ageKap = Kap { delay(20); 30 }

        val result = kapDsl(::SimpleTwo)
            .withName(nameKap)
            .withAge(ageKap)
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `thenX accepts Kap value`() = runTest {
        val validatedKap = Kap { delay(10); true }

        val result = kapDsl(::PhaseDemo)
            .withUser { "Alice" }
            .withCart { "items" }
            .thenValidated(validatedKap)
            .withShipping { 5.0 }
            .withTax { 1.0 }
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "items", true, 5.0, 1.0), result)
    }

    // ── Function annotation ─────────────────────────────────────────

    @Test
    fun `annotated function generates named builders`() = runTest {
        val result = kapDsl(BuildGreeting)
            .withName { "Bob" }
            .withAge { 25 }
            .evalGraph()

        assertEquals("Hello Bob, you are 25", result)
    }

    // ── Prefix collision avoidance ──────────────────────────────────

    @Test
    fun `prefix avoids name collision between functions with same param names`() = runTest {
        val a = kapDsl(BuildA)
            .withPrefixAX { "hello" }
            .withPrefixAY { 1 }
            .evalGraph()

        val b = kapDsl(BuildB)
            .withPrefixBX { "hello" }
            .withPrefixBY { 1 }
            .evalGraph()

        assertEquals("hello-1", a)
        assertEquals("hello+1", b)
    }

    // ── @KapBridge for third-party classes ───────────────────────────

    @Test
    fun `KapBridge generates builders for third-party class`() = runTest {
        val result = kapDsl(::ThirdPartyDto)
            .withId { 42 }
            .withName { "bridged" }
            .withActive { true }
            .evalGraph()

        assertEquals(ThirdPartyDto(42, "bridged", true), result)
    }

    // ── Composition: andThen after last step ────────────────────────

    @Test
    fun `last step returns Kap so andThen works natively`() = runTest {
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .andThen { user ->
                Kap.of("${user.name} is ${user.age}")
            }
            .evalGraph()

        assertEquals("Alice is 30", result)
    }

    // ── Composition: map after last step ────────────────────────────

    @Test
    fun `last step returns Kap so map works natively`() = runTest {
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .map { "${it.name}(${it.age})" }
            .evalGraph()

        assertEquals("Alice(30)", result)
    }

    // ── Error propagation ───────────────────────────────────────────

    @Test
    fun `exception in one branch cancels siblings`() = runTest {
        val result = runCatching {
            kapDsl(::SimpleTwo)
                .withName { delay(100); "should be cancelled" }
                .withAge { throw IllegalStateException("boom") }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    // ── Resilience combinators compose with named builders ──────────

    @Test
    fun `recover composes with named builder result`() = runTest {
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .recover { SimpleTwo("fallback", 0) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `timeout composes with named builder result`() = runTest {
        val result = kapDsl(::SimpleTwo)
            .withName { delay(10); "Alice" }
            .withAge { delay(10); 30 }
            .timeout(1000.milliseconds)
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    // ── Memoize composes with named builders ────────────────────────

    @Test
    fun `memoize works with named builder result`() = runTest {
        var callCount = 0
        val memoized = kapDsl(::SimpleTwo)
            .withName { callCount++; "Alice" }
            .withAge { 30 }
            .memoize()

        val r1 = memoized.evalGraph()
        val r2 = memoized.evalGraph()

        assertEquals(SimpleTwo("Alice", 30), r1)
        assertEquals(r1, r2)
        assertEquals(1, callCount, "Should only execute once due to memoize")
    }

    // ── Settled composes ────────────────────────────────────────────

    @Test
    fun `settled wraps result without cancelling`() = runTest {
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .settled()
            .evalGraph()

        assertTrue(result.isSuccess)
        assertEquals(SimpleTwo("Alice", 30), result.getOrNull())
    }

    // ── Multiple barriers ───────────────────────────────────────────

    @Test
    fun `multiple barriers chain correctly`() = runTest {
        val result = kapDsl(::FiveParams)
            .withP1 { delay(10); "a" }
            .thenP2 { delay(10); 1 }        // barrier 1
            .withP3 { delay(10); true }
            .thenP4 { delay(10); 2.0 }       // barrier 2
            .withP5 { delay(10); 3L }
            .evalGraph()

        assertEquals(FiveParams("a", 1, true, 2.0, 3L), result)
    }
    // ── Opaque types: generic .with + type-level safety ────────────

    @Test
    fun `opaque types work with generic with operator`() = runTest {
        val result = Kap.of { a: SimpleTwoName -> { b: SimpleTwoAge -> SimpleTwo(a.value, b.value) } }
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `opaque types prevent swapping same-typed params`() = runTest {
        // SimpleTwo has (name: String, age: Int)
        // Generated: SimpleTwoName(String), SimpleTwoAge(Int)
        //
        // If you try to swap:
        //   .with { SimpleTwoAge(30) }    // COMPILE ERROR: expected SimpleTwoName
        //   .with { SimpleTwoName("x") }  // COMPILE ERROR: expected SimpleTwoAge
        //
        // The types enforce the correct order.

        val result = Kap.of { name: SimpleTwoName -> { age: SimpleTwoAge -> SimpleTwo(name.value, age.value) } }
            .with { SimpleTwoName("Bob") }
            .with { SimpleTwoAge(25) }
            .evalGraph()

        assertEquals(SimpleTwo("Bob", 25), result)
    }

    @Test
    fun `opaque types for all-same-type class prevent swaps`() = runTest {
        // AllSameType has (first: String, second: String, third: String)
        // Generated: AllSameTypeFirst(String), AllSameTypeSecond(String), AllSameTypeThird(String)
        // All wrap String but are distinct types — can't swap.

        val result = Kap.of { a: AllSameTypeFirst -> { b: AllSameTypeSecond -> { c: AllSameTypeThird ->
            AllSameType(a.value, b.value, c.value)
        } } }
            .with { AllSameTypeFirst("one") }
            .with { AllSameTypeSecond("two") }
            .with { AllSameTypeThird("three") }
            .evalGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
    }

    // ══════════════════════════════════════════════════════════════════
    //  kapTyped() entry point tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `kap returns curried Kap with opaque types`() = runTest {
        val result = kap(::SimpleTwo)
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `kap with 5 params`() = runTest {
        val result = kap(::FiveParams)
            .with { FiveParamsP1("str") }
            .with { FiveParamsP2(42) }
            .with { FiveParamsP3(true) }
            .with { FiveParamsP4(3.14) }
            .with { FiveParamsP5(999L) }
            .evalGraph()

        assertEquals(FiveParams("str", 42, true, 3.14, 999L), result)
    }

    @Test
    fun `kap runs in parallel`() = runTest {
        val result = kap(::SimpleThree)
            .with { delay(50); SimpleThreeA("a") }
            .with { delay(50); SimpleThreeB(1) }
            .with { delay(50); SimpleThreeC(true) }
            .evalGraph()

        assertEquals(SimpleThree("a", 1, true), result)
    }

    @Test
    fun `kap with phase barriers via then`() = runTest {
        val result = kap(::PhaseDemo)
            .with { PhaseDemoUser("Alice") }
            .with { PhaseDemoCart("items") }
            .then { PhaseDemoValidated(true) }
            .with { PhaseDemoShipping(9.99) }
            .with { PhaseDemoTax(1.50) }
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "items", true, 9.99, 1.50), result)
    }

    @Test
    fun `kap composes with andThen`() = runTest {
        val result = kap(::SimpleTwo)
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .andThen { user ->
                Kap.of("${user.name} is ${user.age}")
            }
            .evalGraph()

        assertEquals("Alice is 30", result)
    }

    @Test
    fun `kap composes with map`() = runTest {
        val result = kap(::SimpleTwo)
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .map { "${it.name}(${it.age})" }
            .evalGraph()

        assertEquals("Alice(30)", result)
    }

    @Test
    fun `kap error propagation cancels siblings`() = runTest {
        val result = runCatching {
            kap(::SimpleTwo)
                .with { delay(100); SimpleTwoName("should be cancelled") }
                .with { throw IllegalStateException("boom") }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `kap composes with recover`() = runTest {
        val result = kap(::SimpleTwo)
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .recover { SimpleTwo("fallback", 0) }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `kap composes with settled`() = runTest {
        val result = kap(::SimpleTwo)
            .with { SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .settled()
            .evalGraph()

        assertTrue(result.isSuccess)
        assertEquals(SimpleTwo("Alice", 30), result.getOrNull())
    }

    @Test
    fun `kap composes with memoize`() = runTest {
        var callCount = 0
        val memoized = kap(::SimpleTwo)
            .with { callCount++; SimpleTwoName("Alice") }
            .with { SimpleTwoAge(30) }
            .memoize()

        val r1 = memoized.evalGraph()
        val r2 = memoized.evalGraph()

        assertEquals(r1, r2)
        assertEquals(1, callCount)
    }

    @Test
    fun `kap for functions uses kap{FunctionName} entry point`() = runTest {
        val result = kapBuildGreeting(::buildGreeting)
            .with { BuildGreetingName("Bob") }
            .with { BuildGreetingAge(25) }
            .evalGraph()

        assertEquals("Hello Bob, you are 25", result)
    }

    @Test
    fun `kap for prefixed functions`() = runTest {
        val a = kapBuildA(::buildA)
            .with { BuildAX("hello") }
            .with { BuildAY(1) }
            .evalGraph()

        assertEquals("hello-1", a)
    }

    @Test
    fun `kap with nullable opaque type`() = runTest {
        val result = kap(::WithNullable)
            .with { WithNullableRequired("hello") }
            .with { WithNullableOptional(null) }
            .evalGraph()

        assertEquals(WithNullable("hello", null), result)
    }

    @Test
    fun `kap with generic opaque type`() = runTest {
        val result = kap(::WithGeneric)
            .with { WithGenericItems(listOf("a", "b", "c")) }
            .with { WithGenericCount(3) }
            .evalGraph()

        assertEquals(WithGeneric(listOf("a", "b", "c"), 3), result)
    }

    @Test
    fun `kap for KapBridge third-party class`() = runTest {
        val result = kap(::ThirdPartyDto)
            .with { ThirdPartyDtoId(42) }
            .with { ThirdPartyDtoName("bridged") }
            .with { ThirdPartyDtoActive(true) }
            .evalGraph()

        assertEquals(ThirdPartyDto(42, "bridged", true), result)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Mixed usage: named builders + opaque types on same class
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `named builders (kapDsl) and kap produce identical results`() = runTest {
        val named = kapDsl(::SimpleThree)
            .withA { "hello" }
            .withB { 42 }
            .withC { true }
            .evalGraph()

        val typed = kap(::SimpleThree)
            .with { SimpleThreeA("hello") }
            .with { SimpleThreeB(42) }
            .with { SimpleThreeC(true) }
            .evalGraph()

        assertEquals(named, typed)
    }

    @Test
    fun `named builders and opaque types compose in chain`() = runTest {
        // Named builder for first phase, opaque kapTyped for second via andThen
        val result = kapDsl(::SimpleTwo)
            .withName { "Alice" }
            .withAge { 30 }
            .andThen { user ->
                kap(::SimpleThree)
                    .with { SimpleThreeA(user.name) }
                    .with { SimpleThreeB(user.age) }
                    .with { SimpleThreeC(true) }
            }
            .evalGraph()

        assertEquals(SimpleThree("Alice", 30, true), result)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Graph as data: lazy, passable, dynamically completable
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `partial graph can be stored in a val and completed later`() = runTest {
        // The graph is just data — nothing runs until .evalGraph()
        val partial = kapDsl(::SimpleThree)
            .withA { "hello" }

        // Later, somewhere else, complete it:
        val result = partial
            .withB { 42 }
            .withC { true }
            .evalGraph()

        assertEquals(SimpleThree("hello", 42, true), result)
    }

    @Test
    fun `partial graph can be passed to a function that completes it`() = runTest {
        // Start building the graph
        val partial: SimpleTwoStep1 = kapDsl(::SimpleTwo)
            .withName { "Alice" }

        // A function receives the partial graph and completes it based on logic
        fun completeBasedOnRole(graph: SimpleTwoStep1, isAdmin: Boolean): Kap<SimpleTwo> =
            if (isAdmin) graph.withAge { 99 }
            else graph.withAge { 25 }

        val admin = completeBasedOnRole(partial, isAdmin = true).evalGraph()
        val regular = completeBasedOnRole(partial, isAdmin = false).evalGraph()

        assertEquals(SimpleTwo("Alice", 99), admin)
        assertEquals(SimpleTwo("Alice", 25), regular)
    }

    enum class CartType { STANDARD, PREMIUM, GUEST }

    @Test
    fun `graph branches dynamically based on runtime conditions`() = runTest {
        fun buildCheckout(type: CartType): Kap<SimpleThree> {
            val base = kapDsl(::SimpleThree)
                .withA { "user-data" }

            return when (type) {
                CartType.STANDARD -> base
                    .withB { 100 }
                    .withC { false }
                CartType.PREMIUM -> base
                    .withB { 500 }
                    .withC { true }   // premium flag
                CartType.GUEST -> base
                    .withB { 0 }
                    .withC { false }
            }
        }

        // Nothing has executed yet — just built 3 different graphs
        val standard = buildCheckout(CartType.STANDARD).evalGraph()
        val premium = buildCheckout(CartType.PREMIUM).evalGraph()
        val guest = buildCheckout(CartType.GUEST).evalGraph()

        assertEquals(SimpleThree("user-data", 100, false), standard)
        assertEquals(SimpleThree("user-data", 500, true), premium)
        assertEquals(SimpleThree("user-data", 0, false), guest)
    }

    @Test
    fun `same partial graph reused with different completions`() = runTest {
        // A shared base that fetches the expensive common data once
        val base = kapDsl(::SimpleThree)
            .withA { delay(50); "expensive-shared-data" }

        // Two different completions — the base is reused, not re-executed per se
        // (each .evalGraph() runs from scratch, but the STRUCTURE is shared)
        val resultA = base.withB { 1 }.withC { true }.evalGraph()
        val resultB = base.withB { 2 }.withC { false }.evalGraph()

        assertEquals("expensive-shared-data", resultA.a)
        assertEquals("expensive-shared-data", resultB.a)
        assertEquals(1, resultA.b)
        assertEquals(2, resultB.b)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Typed-applicative API with infix `from`
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `from infix with raw values`() = runTest {
        // Scoped builder: tag vals come from the lambda's implicit receiver —
        // no `with(...)` or `import` needed. Reads like prose:
        // "with name from 'Alice', with age from 30"
        val result = kap(::SimpleTwo)
            .with { name from "Alice" }
            .with { age from 30 }
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `from infix with Kap-decorated values composes combinators`() = runTest {
        // The Kap<T> overload of `from` lets timeout/retry/etc. stay inside the
        // graph instead of escaping via .evalGraph() in the lambda. Use parens
        // and qualify the tag via the wrapper's companion (`SimpleTwoKap.name`)
        // so it's accessible outside the `.with { ... }` lambda receiver.
        val result = kap(::SimpleTwo)
            .with(SimpleTwoKap.name from Kap { delay(10); "Alice" })
            .with(SimpleTwoKap.age from Kap { delay(10); 30 })
            .evalGraph()

        assertEquals(SimpleTwo("Alice", 30), result)
    }

    @Test
    fun `from infix runs in parallel like the rest of the applicative API`() = runTest {
        val result = kap(::SimpleThree)
            .with(SimpleThreeKap.a from Kap { delay(50); "a" })
            .with(SimpleThreeKap.b from Kap { delay(50); 1 })
            .with(SimpleThreeKap.c from Kap { delay(50); true })
            .evalGraph()

        assertEquals(SimpleThree("a", 1, true), result)
    }

    @Test
    fun `from infix with phase barrier via then`() = runTest {
        val result = kap(::PhaseDemo)
            .with { user from "Alice" }
            .with { cart from "items" }
            .then { validated from true }
            .with { shipping from 9.99 }
            .with { tax from 1.50 }
            .evalGraph()

        assertEquals(PhaseDemo("Alice", "items", true, 9.99, 1.50), result)
    }

    @Test
    fun `from infix on all-same-type class still distinguishes fields by tag`() = runTest {
        val result = kap(::AllSameType)
            .with { first from "one" }
            .with { second from "two" }
            .with { third from "three" }
            .evalGraph()

        assertEquals(AllSameType("one", "two", "three"), result)
        // Swapping the order would not compile — each slot expects its own
        // wrapper type (AllSameTypeFirst, AllSameTypeSecond, AllSameTypeThird).
    }

    @Test
    fun `graph built across multiple functions composes cleanly`() = runTest {
        // Function 1: starts the graph
        fun createBase(): FiveParamsStep0 = kapDsl(::FiveParams)

        // Function 2: fills in the user context
        fun addUserContext(graph: FiveParamsStep0): FiveParamsStep2 =
            graph.withP1 { "user-alice" }.withP2 { 42 }

        // Function 3: fills in the config based on environment
        fun addConfig(graph: FiveParamsStep2, isProd: Boolean): Kap<FiveParams> =
            if (isProd)
                graph.withP3 { true }.withP4 { 99.9 }.withP5 { 1000L }
            else
                graph.withP3 { false }.withP4 { 0.0 }.withP5 { 0L }

        // Assemble and execute
        val base = createBase()
        val withUser = addUserContext(base)
        val prod = addConfig(withUser, isProd = true).evalGraph()
        val dev = addConfig(withUser, isProd = false).evalGraph()

        assertEquals(FiveParams("user-alice", 42, true, 99.9, 1000L), prod)
        assertEquals(FiveParams("user-alice", 42, false, 0.0, 0L), dev)
    }
}

// ── Helpers used by @KapBridge test ─────────────────────────────────
// ThirdPartyDto is defined in Main.kt
