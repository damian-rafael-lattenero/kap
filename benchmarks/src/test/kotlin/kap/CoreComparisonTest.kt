package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// ════════════════════════════════════════════════════════════════════════════════
// Three-Way Comparison: kap-core
//
// Every major kap-core operation implemented three ways:
//   1. Raw Coroutines  — manual async/await
//   2. Arrow           — parZip / arrow-fx
//   3. KAP             — kap + with / combinators
// ════════════════════════════════════════════════════════════════════════════════

private suspend fun fetchUser(): UserProfile { delay(50.milliseconds); return UserProfile("Alice", 1) }
private suspend fun fetchCart(): ShoppingCart { delay(50.milliseconds); return ShoppingCart(3, 99.99) }
private suspend fun fetchPromos(): PromotionBundle { delay(50.milliseconds); return PromotionBundle("SAVE20", 20) }
private suspend fun calcShipping(): ShippingQuote { delay(50.milliseconds); return ShippingQuote(5.99, "ground") }
private suspend fun calcTax(): TaxBreakdown { delay(50.milliseconds); return TaxBreakdown(8.50, 0.085) }
private suspend fun fetchInventory(): InventorySnapshot { delay(50.milliseconds); return InventorySnapshot(true) }
private suspend fun validateStock(): StockConfirmation { delay(50.milliseconds); return StockConfirmation(true) }
private suspend fun reservePayment(): PaymentAuth { delay(50.milliseconds); return PaymentAuth("4242", true) }
private suspend fun fetchPrefs(): Preferences { delay(50.milliseconds); return Preferences("dark", "grid") }
private suspend fun fetchFlags(): FeatureFlags { delay(50.milliseconds); return FeatureFlags(mapOf("beta" to true)) }

private suspend fun fetchUserSlow(): UserProfile { delay(500.milliseconds); return UserProfile("Slow", 99) }
private suspend fun fetchUserFailing(): UserProfile { throw RuntimeException("network error") }
private var attemptCount = 0
private suspend fun fetchUserFlaky(): UserProfile {
    attemptCount++
    if (attemptCount < 3) throw RuntimeException("flaky attempt $attemptCount")
    return UserProfile("Recovered", 1)
}

private suspend fun fetchFromPrimary(): String { delay(200.milliseconds); return "primary" }
private suspend fun fetchFromFallback(): String { delay(50.milliseconds); return "fallback" }
private suspend fun fetchById(id: Long): UserProfile { delay(50.milliseconds); return UserProfile("User$id", id) }

private data class SimpleFanout(
    val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
    val shipping: ShippingQuote, val tax: TaxBreakdown,
)

private data class Phase1Result(
    val user: UserProfile, val cart: ShoppingCart,
    val promos: PromotionBundle, val inventory: InventorySnapshot,
)

private data class CheckoutPhased(
    val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
    val inventory: InventorySnapshot, val stock: StockConfirmation,
    val shipping: ShippingQuote, val tax: TaxBreakdown,
)

private data class FullCheckout(
    val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
    val stock: StockConfirmation,
    val shipping: ShippingQuote, val tax: TaxBreakdown,
    val payment: PaymentAuth,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CoreComparisonTest {

    // ═══════════════════════════════════════════════════════════════════════
    // MAP
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `map - raw`() = runTest {
        val user = fetchUser()
        assertEquals("ALICE", user.copy(name = user.name.uppercase()).name)
    }

    @Test fun `map - kap`() = runTest {
        val result = Kap { fetchUser() }.map { it.copy(name = it.name.uppercase()) }.executeGraph()
        assertEquals("ALICE", result.name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLATMAP
    // ═══════════════════════════════════════════════════════════════════════

    data class FriendList(val friends: List<String>)
    private suspend fun fetchFriends(userId: Long): FriendList {
        delay(50.milliseconds); return FriendList(listOf("Bob", "Charlie"))
    }

    @Test fun `andThen - raw`() = runTest {
        val user = fetchUser(); val friends = fetchFriends(user.id)
        assertEquals(listOf("Bob", "Charlie"), friends.friends)
    }

    @Test fun `andThen - kap - value-dependent then fan-out`() = runTest {
        data class Result(val user: UserProfile, val friends: FriendList, val prefs: Preferences)
        val result = Kap { fetchUser() }.andThen { user ->
            kap(::Result)
                .with(Kap.of(user))
                .with { fetchFriends(user.id) }
                .with { fetchPrefs() }
        }.executeGraph()
        assertEquals("Alice", result.user.name)
        assertEquals(listOf("Bob", "Charlie"), result.friends.friends)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ZIP
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `zip - raw`() = runTest {
        val result = coroutineScope {
            val a = async { fetchUser() }; val b = async { fetchCart() }
            a.await() to b.await()
        }
        assertEquals("Alice", result.first.name)
    }

    @Test fun `zip - arrow`() = runTest {
        val result = parZip({ fetchUser() }, { fetchCart() }) { u, c -> u to c }
        assertEquals("Alice", result.first.name)
    }

    @Test fun `zip - kap`() = runTest {
        val result = Kap { fetchUser() }.zip(Kap { fetchCart() }).executeGraph()
        assertEquals("Alice", result.first.name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SIMPLE FAN-OUT (5 calls)
    // ═══════════════════════════════════════════════════════════════════════

    private val expectedFanout = SimpleFanout(
        UserProfile("Alice", 1), ShoppingCart(3, 99.99), PromotionBundle("SAVE20", 20),
        ShippingQuote(5.99, "ground"), TaxBreakdown(8.50, 0.085),
    )

    @Test fun `fan-out 5 - raw`() = runTest {
        val result = coroutineScope {
            val a = async { fetchUser() }; val b = async { fetchCart() }
            val c = async { fetchPromos() }; val d = async { calcShipping() }; val e = async { calcTax() }
            SimpleFanout(a.await(), b.await(), c.await(), d.await(), e.await())
        }
        assertEquals(expectedFanout, result)
    }

    @Test fun `fan-out 5 - arrow`() = runTest {
        val result = parZip(
            { fetchUser() }, { fetchCart() }, { fetchPromos() },
            { calcShipping() }, { calcTax() },
        ) { u, c, p, s, t -> SimpleFanout(u, c, p, s, t) }
        assertEquals(expectedFanout, result)
    }

    @Test fun `fan-out 5 - kap`() = runTest {
        val result = kap(::SimpleFanout)
            .with { fetchUser() }.with { fetchCart() }.with { fetchPromos() }
            .with { calcShipping() }.with { calcTax() }.executeGraph()
        assertEquals(expectedFanout, result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MULTI-PHASE CHECKOUT
    // ═══════════════════════════════════════════════════════════════════════

    private val expectedPhased = CheckoutPhased(
        UserProfile("Alice", 1), ShoppingCart(3, 99.99), PromotionBundle("SAVE20", 20),
        InventorySnapshot(true), StockConfirmation(true),
        ShippingQuote(5.99, "ground"), TaxBreakdown(8.50, 0.085),
    )

    @Test fun `multi-phase - raw`() = runTest {
        val result = coroutineScope {
            val a = async { fetchUser() }; val b = async { fetchCart() }
            val c = async { fetchPromos() }; val d = async { fetchInventory() }
            val u = a.await(); val ca = b.await(); val p = c.await(); val i = d.await()
            val stock = validateStock()
            val e = async { calcShipping() }; val f = async { calcTax() }
            CheckoutPhased(u, ca, p, i, stock, e.await(), f.await())
        }
        assertEquals(expectedPhased, result)
    }

    @Test fun `multi-phase - arrow`() = runTest {
        val p1 = parZip(
            { fetchUser() }, { fetchCart() }, { fetchPromos() }, { fetchInventory() },
        ) { u, c, p, i -> Phase1Result(u, c, p, i) }
        val stock = validateStock()
        val result = parZip({ calcShipping() }, { calcTax() }) { s, t ->
            CheckoutPhased(p1.user, p1.cart, p1.promos, p1.inventory, stock, s, t)
        }
        assertEquals(expectedPhased, result)
    }

    @Test fun `multi-phase - kap`() = runTest {
        val result = kap(::CheckoutPhased)
            .with { fetchUser() }.with { fetchCart() }.with { fetchPromos() }.with { fetchInventory() }
            .then { validateStock() }
            .with { calcShipping() }.with { calcTax() }.executeGraph()
        assertEquals(expectedPhased, result)
    }

    private val expectedCheckout = FullCheckout(
        UserProfile("Alice", 1), ShoppingCart(3, 99.99), PromotionBundle("SAVE20", 20),
        StockConfirmation(true), ShippingQuote(5.99, "ground"), TaxBreakdown(8.50, 0.085),
        PaymentAuth("4242", true),
    )

    @Test fun `multi-phase 4-phases - raw`() = runTest {
        val result = coroutineScope {
            val a = async { fetchUser() }; val b = async { fetchCart() }; val c = async { fetchPromos() }
            val u = a.await(); val ca = b.await(); val p = c.await()
            val stock = validateStock()
            val d = async { calcShipping() }; val e = async { calcTax() }
            val s = d.await(); val t = e.await()
            val payment = reservePayment()
            FullCheckout(u, ca, p, stock, s, t, payment)
        }
        assertEquals(expectedCheckout, result)
    }

    @Test fun `multi-phase 4-phases - arrow`() = runTest {
        data class P1(val u: UserProfile, val c: ShoppingCart, val p: PromotionBundle)
        val p1 = parZip({ fetchUser() }, { fetchCart() }, { fetchPromos() }) { u, c, p -> P1(u, c, p) }
        val stock = validateStock()
        val (s, t) = parZip({ calcShipping() }, { calcTax() }) { a, b -> a to b }
        val payment = reservePayment()
        assertEquals(expectedCheckout, FullCheckout(p1.u, p1.c, p1.p, stock, s, t, payment))
    }

    @Test fun `multi-phase 4-phases - kap`() = runTest {
        val result = kap(::FullCheckout)
            .with { fetchUser() }.with { fetchCart() }.with { fetchPromos() }
            .then { validateStock() }
            .with { calcShipping() }.with { calcTax() }
            .then { reservePayment() }.executeGraph()
        assertEquals(expectedCheckout, result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRAVERSE
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `traverse - raw`() = runTest {
        val ids = listOf(1L, 2L, 3L)
        val results = coroutineScope { ids.map { async { fetchById(it) } }.map { it.await() } }
        assertEquals(3, results.size)
    }

    @Test fun `traverse - kap`() = runTest {
        val results = listOf(1L, 2L, 3L).traverse { Kap { fetchById(it) } }.executeGraph()
        assertEquals(3, results.size)
    }

    @Test fun `traverse bounded - kap`() = runTest {
        val results = (1L..20L).toList().traverse(concurrency = 5) { Kap { fetchById(it) } }.executeGraph()
        assertEquals(20, results.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEQUENCE
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `sequence - raw`() = runTest {
        val fns = listOf<suspend () -> UserProfile>(
            { fetchUser() }, { UserProfile("Bob", 2) }, { UserProfile("Charlie", 3) },
        )
        val results = coroutineScope { fns.map { fn -> async { fn() } }.map { it.await() } }
        assertEquals(3, results.size)
    }

    @Test fun `sequence - kap`() = runTest {
        val results = listOf(
            Kap { fetchUser() }, Kap.of(UserProfile("Bob", 2)), Kap.of(UserProfile("Charlie", 3)),
        ).sequence().executeGraph()
        assertEquals(3, results.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RACE
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `race - raw - select`() = runTest {
        val result = supervisorScope {
            val a = async { fetchFromPrimary() }; val b = async { fetchFromFallback() }
            val w = select<String> { a.onAwait { it }; b.onAwait { it } }
            a.cancel(); b.cancel(); w
        }
        assertEquals("fallback", result)
    }

    @Test fun `race - arrow - raceN`() = runTest {
        val result = arrow.fx.coroutines.raceN(
            { fetchFromPrimary() }, { fetchFromFallback() },
        ).fold({ it }, { it })
        assertEquals("fallback", result)
    }

    @Test fun `race - kap`() = runTest {
        val result = race(Kap { fetchFromPrimary() }, Kap { fetchFromFallback() }).executeGraph()
        assertEquals("fallback", result)
    }

    @Test fun `raceN - kap - three-way`() = runTest {
        val result = raceN(
            Kap { delay(300.milliseconds); "slow" },
            Kap { delay(50.milliseconds); "fast" },
            Kap { delay(200.milliseconds); "medium" },
        ).executeGraph()
        assertEquals("fast", result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TIMEOUT
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `timeout - raw`() = runTest {
        assertEquals(null, withTimeoutOrNull(100.milliseconds) { fetchUserSlow() })
    }

    @Test fun `timeout - kap - with default`() = runTest {
        val result = Kap { fetchUserSlow() }.timeout(100.milliseconds, UserProfile("Default", 0)).executeGraph()
        assertEquals("Default", result.name)
    }

    @Test fun `timeout - kap - with fallback computation`() = runTest {
        val result = Kap { fetchUserSlow() }
            .timeout(100.milliseconds, Kap { UserProfile("FromCache", 0) }).executeGraph()
        assertEquals("FromCache", result.name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RECOVER
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `recover - raw`() = runTest {
        val result = try { fetchUserFailing() } catch (_: RuntimeException) { UserProfile("Fallback", 0) }
        assertEquals("Fallback", result.name)
    }

    @Test fun `recover - kap`() = runTest {
        val result = Kap<UserProfile> { fetchUserFailing() }
            .recover { UserProfile("Recovered: ${it.message}", 0) }.executeGraph()
        assertEquals("Recovered: network error", result.name)
    }

    @Test fun `recoverWith - kap`() = runTest {
        val result = Kap<UserProfile> { fetchUserFailing() }
            .recoverWith { Kap { UserProfile("FromFallbackService", 0) } }.executeGraph()
        assertEquals("FromFallbackService", result.name)
    }

    @Test fun `fallback - kap`() = runTest {
        val result = Kap<UserProfile> { fetchUserFailing() }
            .fallback(Kap.of(UserProfile("Default", 0))).executeGraph()
        assertEquals("Default", result.name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RETRY (simple, from kap-core)
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `retry - raw - manual loop`() = runTest {
        var attempts = 0; var result: String? = null
        repeat(3) {
            try { attempts++; result = if (attempts < 3) throw RuntimeException("fail") else "success"; return@repeat }
            catch (_: RuntimeException) { delay(10.milliseconds) }
        }
        assertEquals("success", result)
    }

    @Test fun `retry - kap`() = runTest {
        attemptCount = 0
        val result = Kap { fetchUserFlaky() }
            .retry(maxAttempts = 3, delay = 10.milliseconds, backoff = exponential).executeGraph()
        assertEquals("Recovered", result.name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTEROP — Deferred, Flow, suspend
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `interop - Deferred toKap`() = runTest {
        val result = coroutineScope {
            val d = async { fetchUser() }
            d.toKap().executeGraph()
        }
        assertEquals("Alice", result.name)
    }

    @Test fun `interop - Flow firstAsKap`() = runTest {
        val f = flow { emit(UserProfile("FromFlow", 1)); emit(UserProfile("Second", 2)) }
        assertEquals("FromFlow", f.firstAsKap().executeGraph().name)
    }

    @Test fun `interop - suspend toKap`() = runTest {
        val fn: suspend () -> UserProfile = { fetchUser() }
        assertEquals("Alice", fn.toKap().executeGraph().name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ON — dispatcher switching
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `on - kap - switch dispatcher`() = runTest {
        val result = kap { a: UserProfile, b: ShoppingCart -> a.name to b.items }
            .with(Kap { fetchUser() }.on(kotlinx.coroutines.Dispatchers.Default))
            .with(Kap { fetchCart() }.on(kotlinx.coroutines.Dispatchers.Default)).executeGraph()
        assertEquals("Alice" to 3, result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRACED — observability hooks
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `traced - raw - manual timing`() = runTest {
        val events = mutableListOf<String>()
        val result = coroutineScope {
            val start = System.nanoTime()
            val user = fetchUser()
            events += "fetchUser: ${(System.nanoTime() - start) / 1_000_000}ms"
            user
        }
        assertEquals("Alice", result.name)
        assertTrue(events.isNotEmpty())
    }

    @Test fun `traced - kap - composable`() = runTest {
        val events = mutableListOf<String>()
        val result = kap { a: UserProfile, b: ShoppingCart -> a.name to b.items }
            .with(Kap { fetchUser() }.traced("user",
                onStart = { events += "start:$it" }, onSuccess = { n, _ -> events += "done:$n" }))
            .with(Kap { fetchCart() }.traced("cart",
                onStart = { events += "start:$it" }, onSuccess = { n, _ -> events += "done:$n" })).executeGraph()
        assertEquals("Alice" to 3, result)
        assertTrue("start:user" in events && "done:user" in events)
    }

    @Test fun `traced - kap - KapTracer`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = KapTracer { events += it }
                kap { a: UserProfile, b: ShoppingCart -> a.name to b.items }
            .with(Kap { fetchUser() }.traced("user", tracer))
            .with(Kap { fetchCart() }.traced("cart", tracer)).executeGraph()
        assertEquals(4, events.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AP-OR-NULL
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `withOrNull - raw`() = runTest {
        val insurance: (suspend () -> InsurancePlan)? = null
        val result = coroutineScope {
            fetchUser().name to insurance?.invoke()?.provider
        }
        assertEquals("Alice" to null, result)
    }

    @Test fun `withOrNull - kap`() = runTest {
        val insurance: Kap<InsurancePlan>? = null
        val result = kap { u: UserProfile, i: InsurancePlan? -> u.name to i?.provider }
            .with { fetchUser() }.withOrNull(insurance).executeGraph()
        assertEquals("Alice" to null, result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPOSED COMBINATORS — timeout + retry + recover + traced
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `composed - raw - deeply nested`() = runTest {
        var attempts = 0
        val result = run {
            var res: UserProfile? = null
            repeat(3) {
                try {
                    attempts++
                    res = withTimeoutOrNull(200.milliseconds) {
                        if (attempts < 3) throw RuntimeException("flaky")
                        fetchUser()
                    }
                    if (res != null) return@repeat
                } catch (_: RuntimeException) { delay(10.milliseconds) }
            }
            res ?: UserProfile("Cache", 0)
        }
        assertNotNull(result)
    }

    @Test fun `composed - kap - flat chain`() = runTest {
        attemptCount = 0
        val events = mutableListOf<String>()
        val result = Kap { fetchUserFlaky() }
            .timeout(200.milliseconds)
            .retry(maxAttempts = 3, delay = 10.milliseconds, backoff = exponential)
            .recover { UserProfile("Cache: ${it.message}", 0) }
            .traced("fetchUser", onStart = { events += "start" }, onSuccess = { _, _ -> events += "success" }).executeGraph()
        assertNotNull(result)
        assertTrue("start" in events)
    }
}
