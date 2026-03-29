@file:Suppress("unused", "RedundantSuspendModifier", "UNUSED_VARIABLE")

import kap.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ═══════════════════════════════════════════════════════════════════════
//  Shared domain types & simulated services
// ═══════════════════════════════════════════════════════════════════════

data class UserProfile(val name: String, val id: Long)
data class ShoppingCart(val items: Int, val total: Double)
data class PromotionBundle(val code: String, val discountPct: Int)
data class InventorySnapshot(val allInStock: Boolean)
data class StockConfirmation(val confirmed: Boolean)
data class ShippingQuote(val amount: Double, val method: String)
data class TaxBreakdown(val amount: Double, val rate: Double)
data class DiscountSummary(val amount: Double, val promoApplied: String)
data class PaymentAuth(val cardLast4: String, val authorized: Boolean)
data class OrderConfirmation(val orderId: String)
data class EmailReceipt(val sentTo: String, val orderId: String)

data class CheckoutResult(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val inventory: InventorySnapshot,
    val stock: StockConfirmation,
    val shipping: ShippingQuote,
    val tax: TaxBreakdown,
    val discounts: DiscountSummary,
    val payment: PaymentAuth,
    val confirmation: OrderConfirmation,
    val email: EmailReceipt,
)

suspend fun fetchUser(): UserProfile { delay(50); return UserProfile("Alice", 42) }
suspend fun fetchCart(): ShoppingCart { delay(40); return ShoppingCart(3, 147.50) }
suspend fun fetchPromos(): PromotionBundle { delay(30); return PromotionBundle("SUMMER20", 20) }
suspend fun fetchInventory(): InventorySnapshot { delay(50); return InventorySnapshot(allInStock = true) }
suspend fun validateStock(): StockConfirmation { delay(20); return StockConfirmation(confirmed = true) }
suspend fun calcShipping(): ShippingQuote { delay(30); return ShippingQuote(5.99, "standard") }
suspend fun calcTax(): TaxBreakdown { delay(20); return TaxBreakdown(12.38, 0.08) }
suspend fun calcDiscounts(): DiscountSummary { delay(15); return DiscountSummary(29.50, "SUMMER20") }
suspend fun reservePayment(): PaymentAuth { delay(40); return PaymentAuth("4242", authorized = true) }
suspend fun generateConfirmation(): OrderConfirmation { delay(30); return OrderConfirmation("order-#90142") }
suspend fun sendEmail(): EmailReceipt { delay(20); return EmailReceipt("alice@example.com", "order-#90142") }

suspend fun fetchName(): String { delay(30); return "Alice" }
suspend fun fetchAge(): Int { delay(20); return 30 }

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchDashUser(): String { delay(30); return "Alice" }
suspend fun fetchDashCart(): String { delay(20); return "3 items" }
suspend fun fetchDashPromos(): String { delay(10); return "SAVE20" }

// ═══════════════════════════════════════════════════════════════════════
//  Section: Hero — KAP Checkout (11 services, 5 phases)
// ═══════════════════════════════════════════════════════════════════════

suspend fun heroCheckout() {
    println("=== Hero: KAP Checkout (11 services, 5 phases) ===\n")

    val checkout: CheckoutResult = Async {
        kap(::CheckoutResult)
            .with { fetchUser() }              // ┐
            .with { fetchCart() }               // ├─ phase 1: parallel
            .with { fetchPromos() }             // │
            .with { fetchInventory() }          // ┘
            .then { validateStock() }     // ── phase 2: barrier
            .with { calcShipping() }            // ┐
            .with { calcTax() }                 // ├─ phase 3: parallel
            .with { calcDiscounts() }           // ┘
            .then { reservePayment() }    // ── phase 4: barrier
            .with { generateConfirmation() }    // ┐ phase 5: parallel
            .with { sendEmail() }               // ┘
    }

    println("  Result: $checkout\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Raw Coroutines Pain (before/after)
// ═══════════════════════════════════════════════════════════════════════

suspend fun rawCoroutinesCheckout() {
    println("=== Raw Coroutines Checkout (for comparison) ===\n")

    val checkout = coroutineScope {
        val dUser = async { fetchUser() }
        val dCart = async { fetchCart() }
        val dPromos = async { fetchPromos() }
        val dInventory = async { fetchInventory() }
        val user = dUser.await()
        val cart = dCart.await()
        val promos = dPromos.await()
        val inventory = dInventory.await()

        val stock = validateStock()

        val dShipping = async { calcShipping() }
        val dTax = async { calcTax() }
        val dDiscounts = async { calcDiscounts() }
        val shipping = dShipping.await()
        val tax = dTax.await()
        val discounts = dDiscounts.await()

        val payment = reservePayment()

        val dConfirmation = async { generateConfirmation() }
        val dEmail = async { sendEmail() }

        CheckoutResult(
            user, cart, promos, inventory, stock,
            shipping, tax, discounts, payment,
            dConfirmation.await(), dEmail.await()
        )
    }

    println("  Result: $checkout\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Arrow Checkout (for comparison)
// ═══════════════════════════════════════════════════════════════════════

data class Phase1(val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle, val inventory: InventorySnapshot)
data class Phase3(val shipping: ShippingQuote, val tax: TaxBreakdown, val discounts: DiscountSummary)

suspend fun arrowCheckout() {
    println("=== Arrow Checkout (for comparison) ===\n")

    val p1 = arrow.fx.coroutines.parZip(
        { fetchUser() }, { fetchCart() }, { fetchPromos() }, { fetchInventory() },
    ) { u, c, p, i -> Phase1(u, c, p, i) }
    val stock = validateStock()
    val p3 = arrow.fx.coroutines.parZip(
        { calcShipping() }, { calcTax() }, { calcDiscounts() },
    ) { s, t, d -> Phase3(s, t, d) }
    val payment = reservePayment()
    val p5 = arrow.fx.coroutines.parZip(
        { generateConfirmation() }, { sendEmail() },
    ) { c, e -> Pair(c, e) }

    val checkout = CheckoutResult(
        p1.user, p1.cart, p1.promos, p1.inventory,
        stock,
        p3.shipping, p3.tax, p3.discounts,
        payment,
        p5.first, p5.second,
    )

    println("  Result: $checkout\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Constructor is a Function
// ═══════════════════════════════════════════════════════════════════════

data class Greeting(val text: String, val target: String)

suspend fun constructorIsAFunction() {
    println("=== A constructor is a function ===\n")

    // ::Greeting has type (String, String) -> Greeting
    val g1: Greeting = Async {
        kap(::Greeting)
            .with { fetchName() }
            .with { "hello" }
    }
    println("  Constructor ref: $g1")

    // Any (A, B) -> R function works:
    val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
    val g2: String = Async {
        kap(greet)
            .with { fetchName() }
            .with { fetchAge() }
    }
    println("  Lambda function: $g2")

    // A regular function reference:
    fun buildSummary(name: String, items: Int): String = "$name has $items items"

    val g3: String = Async {
        kap(::buildSummary)
            .with { fetchName() }
            .with { 5 }
    }
    println("  Function ref:   $g3\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Nothing Runs Until Async {}
// ═══════════════════════════════════════════════════════════════════════

suspend fun nothingRunsUntilAsync() {
    println("=== Nothing runs until Async {} ===\n")

    val plan: Kap<Dashboard> = kap(::Dashboard)
        .with { fetchDashUser() }
        .with { fetchDashCart() }
        .with { fetchDashPromos() }

    println("  Plan built. Nothing has executed yet.")
    println("  plan is: ${plan::class.simpleName}")

    val result: Dashboard = Async { plan }
    println("  After Async: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: All val, no null
// ═══════════════════════════════════════════════════════════════════════

data class DashboardView(val user: String, val cart: String)

suspend fun allValsNoNulls() {
    println("=== All val, no null ===\n")

    // Raw coroutines: vars and nulls
    var user: String? = null
    var cart: String? = null
    coroutineScope {
        launch { user = fetchDashUser() }
        launch { cart = fetchDashCart() }
    }
    val rawResult = DashboardView(user!!, cart!!)
    println("  Raw (var/null!!): $rawResult")

    // KAP: all val, no nulls
    val kapResult: DashboardView = Async {
        kap(::DashboardView)
            .with { fetchDashUser() }
            .with { fetchDashCart() }
    }
    println("  KAP (val, safe):  $kapResult\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Three Primitives
// ═══════════════════════════════════════════════════════════════════════

suspend fun fetchA(): String { delay(30); return "A" }
suspend fun fetchB(): String { delay(20); return "B" }
suspend fun validate(): String { delay(10); return "valid" }

data class AB(val a: String, val b: String)
data class R3(val a: String, val b: String, val c: String)

suspend fun threePrimitiveWith() {
    println("=== Primitive: .with (parallel) ===\n")

    val result = Async {
        kap(::AB)
            .with { fetchA() }   // ┐ parallel
            .with { fetchB() }   // ┘
    }
    println("  .with result: $result\n")
}

suspend fun threePrimitiveFollowedBy() {
    println("=== Primitive: .then (barrier) ===\n")

    val result = Async {
        kap(::R3)
            .with { fetchA() }             // ┐ parallel
            .with { fetchB() }             // ┘
            .then { validate() }     // waits for A and B
    }
    println("  .then result: $result\n")
}

data class UserContext(val profile: String, val prefs: String, val tier: String)
data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

suspend fun fetchProfile(id: String): String { delay(50); return "profile-$id" }
suspend fun fetchPreferences(id: String): String { delay(30); return "prefs-dark" }
suspend fun fetchLoyaltyTier(id: String): String { delay(40); return "gold" }
suspend fun fetchRecommendations(profile: String): String { delay(40); return "recs-for-$profile" }
suspend fun fetchPromotions(tier: String): String { delay(30); return "promos-$tier" }
suspend fun fetchTrending(prefs: String): String { delay(20); return "trending-$prefs" }

suspend fun threePrimitiveFlatMap() {
    println("=== Primitive: .andThen (value-dependent phases) ===\n")

    val userId = "user-1"
    val dashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }       // ┐ phase 1: parallel
            .with { fetchPreferences(userId) }   // │
            .with { fetchLoyaltyTier(userId) }   // ┘
            .andThen { ctx ->                     // ── barrier: phase 2 NEEDS ctx
                kap(::PersonalizedDashboard)
                    .with { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
                    .with { fetchPromotions(ctx.tier) }           // │
                    .with { fetchTrending(ctx.prefs) }            // ┘
            }
    }
    println("  .andThen result: $dashboard\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Value-Dependent Phases (raw vs KAP)
// ═══════════════════════════════════════════════════════════════════════

data class EnrichedContent(val recs: String, val promos: String, val trending: String, val history: String)
data class FinalDashboard(val layout: String, val analytics: String)

suspend fun fetchHistory(profile: String): String { delay(35); return "history-$profile" }
suspend fun renderLayout(ctx: UserContext, content: EnrichedContent): String {
    delay(25); return "layout(${ctx.profile}, ${content.recs})"
}
suspend fun trackAnalytics(ctx: UserContext, content: EnrichedContent): String {
    delay(15); return "tracked(${ctx.tier})"
}

suspend fun phasedFlatMapRaw() {
    println("=== Phase Dependencies: Raw Coroutines ===\n")
    val userId = "user-42"

    val ctx = coroutineScope {
        val dProfile = async { fetchProfile(userId) }
        val dPrefs = async { fetchPreferences(userId) }
        val dTier = async { fetchLoyaltyTier(userId) }
        UserContext(dProfile.await(), dPrefs.await(), dTier.await())
    }

    val enriched = coroutineScope {
        val dRecs = async { fetchRecommendations(ctx.profile) }
        val dPromos = async { fetchPromotions(ctx.tier) }
        val dTrending = async { fetchTrending(ctx.prefs) }
        val dHistory = async { fetchHistory(ctx.profile) }
        EnrichedContent(dRecs.await(), dPromos.await(), dTrending.await(), dHistory.await())
    }

    val dashboard = coroutineScope {
        val dLayout = async { renderLayout(ctx, enriched) }
        val dTrack = async { trackAnalytics(ctx, enriched) }
        FinalDashboard(dLayout.await(), dTrack.await())
    }

    println("  Raw result: $dashboard\n")
}

suspend fun phasedFlatMapKap() {
    println("=== Phase Dependencies: KAP andThen ===\n")
    val userId = "user-42"

    val dashboard: FinalDashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }
            .with { fetchPreferences(userId) }
            .with { fetchLoyaltyTier(userId) }
            .andThen { ctx ->
                kap(::EnrichedContent)
                    .with { fetchRecommendations(ctx.profile) }
                    .with { fetchPromotions(ctx.tier) }
                    .with { fetchTrending(ctx.prefs) }
                    .with { fetchHistory(ctx.profile) }
                    .andThen { enriched ->
                        kap(::FinalDashboard)
                            .with { renderLayout(ctx, enriched) }
                            .with { trackAnalytics(ctx, enriched) }
                    }
            }
    }

    println("  KAP result: $dashboard\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Quick Start — Basic
// ═══════════════════════════════════════════════════════════════════════

suspend fun quickStartBasic() {
    println("=== Quick Start: Basic ===\n")

    val result = Async {
        kap(::Dashboard)
            .with { fetchDashUser() }    // ┐ all three in parallel
            .with { fetchDashCart() }     // │ total time = max(individual)
            .with { fetchDashPromos() }   // ┘ not sum
    }
    println("  Dashboard: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Quick Start — Resilience
// ═══════════════════════════════════════════════════════════════════════

suspend fun fetchFromSlowApi(): String { delay(300); return "slow-result" }
suspend fun fetchFromCache(): String { delay(20); return "cached-result" }

suspend fun quickStartResilience() {
    println("=== Quick Start: Resilience ===\n")

    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)
    val retryPolicy = Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds)

    val result = Async {
        kap(::Dashboard)
            .with(Kap { fetchDashUser() }
                .withCircuitBreaker(breaker)
                .retry(retryPolicy))
            .with(Kap { fetchFromSlowApi() }
                .timeoutRace(100.milliseconds, Kap { fetchFromCache() }))
            .with { fetchDashPromos() }
    }
    println("  Resilient dashboard: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Quick Start — Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

sealed class RegError(val message: String) {
    class NameTooShort(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class AgeTooLow(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> {
    delay(20)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.NameTooShort("Name must be >= 2 chars")))
}

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> {
    delay(15)
    return if ("@" in email) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Invalid email: $email")))
}

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> {
    delay(10)
    return if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.AgeTooLow("Must be >= 18, got $age")))
}

suspend fun checkUsername(username: String): Either<NonEmptyList<RegError>, ValidUsername> {
    delay(25)
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}

suspend fun quickStartValidation() {
    println("=== Quick Start: Validation ===\n")

    val valid: Either<NonEmptyList<RegError>, User> = Async {
        kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("Alice") }
            .withV { validateEmail("alice@example.com") }
            .withV { validateAge(25) }
            .withV { checkUsername("alice") }
    }
    when (valid) {
        is Either.Right -> println("  Valid: ${valid.value}")
        is Either.Left -> println("  Errors: ${valid.value.map { it.message }}")
    }

    val invalid: Either<NonEmptyList<RegError>, User> = Async {
        kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("A") }
            .withV { validateEmail("bad") }
            .withV { validateAge(10) }
            .withV { checkUsername("al") }
    }
    when (invalid) {
        is Either.Right -> println("  Valid: ${invalid.value}")
        is Either.Left -> println("  ${invalid.value.size} errors: ${invalid.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Choose Your Style
// ═══════════════════════════════════════════════════════════════════════

suspend fun chooseYourStyle() {
    println("=== Choose Your Style ===\n")

    // Style 1: kap + with — compile-time parameter order safety
    val s1 = Async {
        kap(::Dashboard)
            .with { fetchDashUser() }
            .with { fetchDashCart() }
            .with { fetchDashPromos() }
    }
    println("  kap+with:  $s1")

    // Style 2: combine with suspend lambdas
    val s2 = Async {
        combine(
            { fetchDashUser() },
            { fetchDashCart() },
            { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
    }
    println("  combine:   $s2")

    // Style 3: combine with pre-built Kaps
    val s3 = Async {
        combine(
            Kap { fetchDashUser() },
            Kap { fetchDashCart() },
            Kap { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
    }
    println("  zip:       $s3")

    // Bonus: pair
    val (user, cart) = Async { pair({ fetchDashUser() }, { fetchDashCart() }) }
    println("  pair:      ($user, $cart)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Partial Failure with .settled() (kap-core)
// ═══════════════════════════════════════════════════════════════════════

// Domain type that tolerates partial failure
data class PartialDashboard(val user: String, val cart: String, val config: String)

// Services: one is unreliable, the others always succeed
suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

// Builder function: receives Result<String> for the unreliable service, uses fallback
fun buildPartialDashboard(user: Result<String>, cart: String, config: String): PartialDashboard =
    PartialDashboard(
        user = user.getOrDefault("anonymous"),  // failed? use fallback value
        cart = cart,
        config = config,
    )

suspend fun featureSettled() {
    println("=== Feature: Partial Failure with .settled() ===\n")

    // settled { } wraps the result in Result<T> — failure doesn't cancel siblings
    val dashboard = Async {
        kap(::buildPartialDashboard)
            .with(settled { fetchUserMayFail() })  // Result<String> — won't cancel siblings
            .with { fetchCartAlways() }             // normal String — failure here cancels all
            .with { fetchConfigAlways() }            // normal String
    }
    println("  settled: $dashboard")
    // PartialDashboard(user=anonymous, cart=cart-ok, config=config-ok)
    // fetchUserMayFail() threw → settled { } wrapped as Result.failure → buildPartialDashboard used fallback

    // traverseSettled: process ALL items, no cancellation on failure
    val ids = listOf(1, 2, 3, 4, 5)
    val results: List<Result<String>> = Async {
        ids.traverseSettled { id ->
            Kap {
                if (id % 2 == 0) throw RuntimeException("fail-$id")
                "user-$id"
            }
        }
    }
    val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
    val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!!.message }
    println("  traverseSettled: successes=$successes, failures=$failures\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Timeout with Parallel Fallback (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTimeoutRace() {
    println("=== Feature: Timeout with Parallel Fallback ===\n")

    suspend fun fetchFromPrimary(): String { delay(200); return "primary-data" }
    suspend fun fetchFromFallback(): String { delay(30); return "fallback-data" }

    val start = System.currentTimeMillis()
    val result = Async {
        Kap { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
    }
    val elapsed = System.currentTimeMillis() - start
    println("  timeoutRace: $result (${elapsed}ms — fallback won)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Retry with Schedule (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetrySchedule() {
    println("=== Feature: Retry with Schedule ===\n")

    var attempts = 0
    suspend fun flakyService(): String {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        return "success on attempt $attempts"
    }

    val policy = Schedule.times<Throwable>(5) and
        Schedule.exponential(10.milliseconds) and
        Schedule.doWhile<Throwable> { it is RuntimeException }

    val result = Async {
        Kap { flakyService() }.retry(policy)
    }
    println("  Result: $result")

    // Inline retry with the simple core overload
    attempts = 0
    data class RetryResult(val user: String, val service: String)
    val result2 = Async {
        kap { user: String, service: String -> RetryResult(user, service) }
            .with { fetchDashUser() }
            .with(Kap { flakyService() }
                .retry(3, delay = 10.milliseconds))
    }
    println("  Inline:  $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Resource Safety (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

class MockConnection(val name: String) {
    var closed = false
    suspend fun query(q: String): String { delay(20); return "$name:result-of-$q" }
    suspend fun get(key: String): String { delay(15); return "$name:$key" }
    fun close() { closed = true }
}

suspend fun openDbConnection(): MockConnection { delay(10); return MockConnection("db") }
suspend fun openCacheConnection(): MockConnection { delay(10); return MockConnection("cache") }
suspend fun openHttpClient(): MockConnection { delay(10); return MockConnection("http") }

suspend fun featureResourceSafety() {
    println("=== Feature: Resource Safety ===\n")

    // bracket: acquire, use in parallel, guaranteed release
    val result = Async {
        kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
            .with(bracket(
                acquire = { openDbConnection() },
                use = { conn -> Kap { conn.query("SELECT 1") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openCacheConnection() },
                use = { conn -> Kap { conn.get("key") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openHttpClient() },
                use = { client -> Kap { client.get("/api") } },
                release = { client -> client.close() },
            ))
    }
    println("  bracket: $result")

    // Resource monad: compose first, use later
    data class DashboardData(val db: String, val cache: String, val http: String)

    val infra = Resource.zip(
        Resource({ openDbConnection() }, { it.close() }),
        Resource({ openCacheConnection() }, { it.close() }),
        Resource({ openHttpClient() }, { it.close() }),
    ) { db, cache, http -> Triple(db, cache, http) }

    val result2 = Async {
        infra.useKap { (db, cache, http) ->
            kap(::DashboardData)
                .with { db.query("SELECT 1") }
                .with { cache.get("user:prefs") }
                .with { http.get("/recommendations") }
        }
    }
    println("  Resource: $result2")

    // bracketCase: release behavior depends on outcome
    val result3 = Async {
        bracketCase(
            acquire = { openDbConnection() },
            use = { tx -> Kap { tx.query("INSERT 1") } },
            release = { tx, case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    bracketCase: commit")
                    else -> println("    bracketCase: rollback")
                }
                tx.close()
            },
        )
    }
    println("  bracketCase: $result3\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Racing (kap-core + kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRacing() {
    println("=== Feature: Racing ===\n")

    suspend fun fetchFromRegionUS(): String { delay(100); return "US-data" }
    suspend fun fetchFromRegionEU(): String { delay(30); return "EU-data" }
    suspend fun fetchFromRegionAP(): String { delay(60); return "AP-data" }

    val fastest = Async {
        raceN(
            Kap { fetchFromRegionUS() },
            Kap { fetchFromRegionEU() },
            Kap { fetchFromRegionAP() },
        )
    }
    println("  raceN winner: $fastest")

    // raceEither with different types
    val raceResult: Either<String, Int> = Async {
        raceEither(
            fa = Kap { delay(30); "fast-string" },
            fb = Kap { delay(100); 42 },
        )
    }
    println("  raceEither: $raceResult\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Bounded Parallel Collection Processing (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverse() {
    println("=== Feature: Bounded Parallel Traverse ===\n")

    val userIds = (1..10).toList()

    val results = Async {
        userIds.traverse(concurrency = 3) { id ->
            Kap { delay(20); "user-$id" }
        }
    }
    println("  traverse(c=3): ${results.size} users fetched\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Quorum Consensus (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRaceQuorum() {
    println("=== Feature: Quorum Consensus ===\n")

    suspend fun fetchReplicaA(): String { delay(50); return "replica-A" }
    suspend fun fetchReplicaB(): String { delay(20); return "replica-B" }
    suspend fun fetchReplicaC(): String { delay(80); return "replica-C" }

    val quorum: List<String> = Async {
        raceQuorum(
            required = 2,
            Kap { fetchReplicaA() },
            Kap { fetchReplicaB() },
            Kap { fetchReplicaC() },
        )
    }
    println("  raceQuorum(2 of 3): $quorum\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Circuit Breaker (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureCircuitBreaker() {
    println("=== Feature: Circuit Breaker ===\n")

    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

    val result = Async {
        Kap { fetchDashUser() }
            .timeout(500.milliseconds)
            .withCircuitBreaker(breaker)
            .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
            .recover { "cached-user" }
    }
    println("  Composable chain: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Memoization (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMemoize() {
    println("=== Feature: Memoization ===\n")

    var callCount = 0
    val fetchOnce = Kap { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

    val a = Async { fetchOnce }
    val b = Async { fetchOnce }
    println("  memoizeOnSuccess: a=$a, b=$b, callCount=$callCount\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Parallel Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureParallelValidation() {
    println("=== Feature: Parallel Validation ===\n")

    val result: Either<NonEmptyList<RegError>, User> = Async {
        zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
            { checkUsername("alice") },
        ) { name, email, age, username -> User(name, email, age, username) }
    }
    when (result) {
        is Either.Right -> println("  All pass: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }

    val allFail: Either<NonEmptyList<RegError>, User> = Async {
        zipV(
            { validateName("A") },
            { validateEmail("bad") },
            { validateAge(10) },
            { checkUsername("al") },
        ) { name, email, age, username -> User(name, email, age, username) }
    }
    when (allFail) {
        is Either.Right -> println("  All pass: ${allFail.value}")
        is Either.Left -> println("  ${allFail.value.size} errors: ${allFail.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Phased Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

data class Identity(val name: ValidName, val email: ValidEmail, val age: ValidAge)
data class Clearance(val notBlocked: Boolean, val available: Boolean)
data class Registration(val identity: Identity, val clearance: Clearance)

suspend fun checkNotBlacklisted(id: Identity): Either<NonEmptyList<RegError>, Boolean> {
    delay(20); return Either.Right(true)
}

suspend fun checkUsernameAvailable(email: String): Either<NonEmptyList<RegError>, Boolean> {
    delay(15); return Either.Right(true)
}

suspend fun featurePhasedValidation() {
    println("=== Feature: Phased Validation ===\n")

    val result: Either<NonEmptyList<RegError>, Registration> = Async {
        accumulate {
            val identity = zipV(
                { validateName("Alice") },
                { validateEmail("alice@example.com") },
                { validateAge(25) },
            ) { name, email, age -> Identity(name, email, age) }
                .bindV()

            val cleared = zipV(
                { checkNotBlacklisted(identity) },
                { checkUsernameAvailable(identity.email.value) },
            ) { a, b -> Clearance(a, b) }
                .bindV()

            Registration(identity, cleared)
        }
    }

    when (result) {
        is Either.Right -> println("  Phased: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: attempt() (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureAttempt() {
    println("=== Feature: attempt() ===\n")

    val success: Either<Throwable, String> = Async {
        Kap { "hello" }.attempt()
    }
    println("  attempt success: $success")

    val failure: Either<Throwable, String> = Async {
        Kap<String> { throw RuntimeException("boom") }.attempt()
    }
    println("  attempt failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: firstSuccessOf & orElse (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFallbacks() {
    println("=== Feature: firstSuccessOf & orElse ===\n")

    val result = Async {
        Kap<String> { throw RuntimeException("fail-1") }
            .orElse(Kap { "fallback-ok" })
    }
    println("  orElse: $result")

    val result2 = Async {
        firstSuccessOf(
            Kap { throw RuntimeException("fail-1") },
            Kap { throw RuntimeException("fail-2") },
            Kap { "third-wins" },
        )
    }
    println("  firstSuccessOf: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation {} builder (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKapBuilder() {
    println("=== Feature: computation {} builder ===\n")

    val result = Async {
        computation {
            val user = Kap { fetchDashUser() }.bind()
            val cart = Kap { fetchDashCart() }.bind()
            "$user has $cart"
        }
    }
    println("  computation {}: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Execution Model — then vs thenValue
// ═══════════════════════════════════════════════════════════════════════

fun combineThree(a: String, b: String, c: String): String = "$a+$b+$c"

suspend fun executionModel() {
    println("=== Execution Model ===\n")

    val graph = kap(::combineThree)
        .with { fetchA() }
        .with { fetchB() }

    println("  graph built, not executed")
    val result = Async { graph.with { "C" } }
    println("  Async { graph }: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Reordered execution: execute params out of constructor order
// ═══════════════════════════════════════════════════════════════════════

data class Page(val a: String, val b: String, val c: String, val d: String)

suspend fun fetchParamA(): String { delay(30); return "A-val" }
suspend fun fetchParamB(): String { delay(20); return "B-val" }
suspend fun fetchParamC(): String { delay(40); return "C-val" }
suspend fun fetchParamD(): String { delay(10); return "D-val" }

suspend fun reorderedWithoutBarrier() {
    println("=== Reordered: No barrier (all parallel, assemble freely) ===\n")

    val result = Async {
        combine(
            pair({ fetchParamC() }, { fetchParamD() }),
            pair({ fetchParamA() }, { fetchParamB() }),
        ) { (c, d), (a, b) -> Page(a, b, c, d) }
    }
    println("  result: $result\n")
}

suspend fun reorderedWithBarrier() {
    println("=== Reordered: With barrier (phase 1 -> barrier -> phase 2) ===\n")

    val result = Async {
        computation {
            val (c, d) = pair({ fetchParamC() }, { fetchParamD() }).bind()
            val (a, b) = pair({ fetchParamA() }, { fetchParamB() }).bind()
            Page(a, b, c, d)
        }
    }
    println("  result: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  BFF Example — mobile app endpoint aggregating multiple backend services
// ═══════════════════════════════════════════════════════════════════════

data class UserSession(val userId: String, val tier: String, val prefs: List<String>)
data class ProductFeed(val items: List<String>, val sponsored: List<String>)
data class MobileHomePage(val session: UserSession, val feed: ProductFeed, val notifications: Int)

suspend fun fetchSession(token: String): UserSession {
    delay(40); return UserSession("u-123", "gold", listOf("electronics", "books"))
}
suspend fun fetchProductFeed(prefs: List<String>): List<String> {
    delay(35); return prefs.map { "top-$it" }
}
suspend fun fetchSponsored(tier: String): List<String> {
    delay(25); return listOf("sponsored-for-$tier")
}
suspend fun fetchNotifications(userId: String): Int {
    delay(20); return 7
}

suspend fun bffMobileApp() {
    println("=== BFF: Mobile Home Page ===\n")

    val homePage: MobileHomePage = Async {
        Kap { fetchSession("tok-abc") }         // phase 1: authenticate
            .andThen { session ->                        // ── barrier: session ready
                combine(                                 // phase 2: fan-out (all parallel)
                    { fetchProductFeed(session.prefs) },
                    { fetchSponsored(session.tier) },
                    { fetchNotifications(session.userId) },
                ) { items, sponsored, notifs ->
                    MobileHomePage(
                        session = session,
                        feed = ProductFeed(items, sponsored),
                        notifications = notifs,
                    )
                }
            }
    }

    println("  homePage: $homePage")
    assert(homePage.session.userId == "u-123")
    assert(homePage.feed.items.size == 2)
    assert(homePage.notifications == 7)
    println("  ✓ BFF example passed\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: thenValue — fill a slot without a barrier
// ═══════════════════════════════════════════════════════════════════════

data class Enriched(val a: String, val enriched: String, val c: String)

suspend fun featureThenValue() {
    println("=== Feature: thenValue (no barrier) ===\n")

    val result = Async {
        kap(::Enriched)
            .with { delay(30); "data-A" }        // launched at t=0
            .thenValue { delay(50); "enriched" }  // sequential value, no barrier
            .with { delay(20); "data-C" }         // launched at t=0 (overlaps!)
    }
    println("  thenValue: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: traverseDiscard — fire-and-forget parallel processing
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverseDiscard() {
    println("=== Feature: traverseDiscard ===\n")

    val ids = listOf(1, 2, 3, 4, 5)
    val processed = mutableListOf<Int>()

    Async {
        ids.traverseDiscard(concurrency = 3) { id ->
            Kap { delay(10); synchronized(processed) { processed.add(id) } }
        }
    }
    println("  traverseDiscard processed: $processed (fire-and-forget, results discarded)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: sequence — execute a list of Kap computations
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureSequence() {
    println("=== Feature: sequence ===\n")

    val computations = listOf(
        Kap { delay(30); "alpha" },
        Kap { delay(20); "beta" },
        Kap { delay(10); "gamma" },
    )

    val results: List<String> = Async { computations.sequence(concurrency = 2) }
    println("  sequence(c=2): $results\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: raceAll — race a dynamic list of computations
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRaceAll() {
    println("=== Feature: raceAll ===\n")

    val replicas = listOf(
        Kap { delay(100); "replica-slow" },
        Kap { delay(20); "replica-fast" },
        Kap { delay(60); "replica-medium" },
    )

    val winner = Async { replicas.raceAll() }
    println("  raceAll winner: $winner\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: timeout — .timeout(duration) { fallback }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTimeout() {
    println("=== Feature: timeout ===\n")

    // timeout with default value
    val result1 = Async {
        Kap { delay(200); "slow-value" }
            .timeout(50.milliseconds, "default-value")
    }
    println("  timeout(default): $result1")

    // timeout with fallback computation
    val result2 = Async {
        Kap { delay(200); "slow-value" }
            .timeout(50.milliseconds, Kap { delay(10); "fallback-value" })
    }
    println("  timeout(fallback): $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: recover — .recover { } and .recoverWith { }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRecover() {
    println("=== Feature: recover / recoverWith ===\n")

    val result1 = Async {
        Kap<String> { throw RuntimeException("oops") }
            .recover { err -> "recovered: ${err.message}" }
    }
    println("  recover: $result1")

    val result2 = Async {
        Kap<String> { throw RuntimeException("oops") }
            .recoverWith { err -> Kap { delay(10); "recovered-with: ${err.message}" } }
    }
    println("  recoverWith: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: retry (simple) — .retry(maxAttempts, delay)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetrySimple() {
    println("=== Feature: retry (simple) ===\n")

    var attempts = 0
    val result = Async {
        Kap {
            attempts++
            if (attempts < 3) throw RuntimeException("flake #$attempts")
            "success on attempt $attempts"
        }.retry(maxAttempts = 5, delay = 10.milliseconds, backoff = exponential)
    }
    println("  retry: $result (took $attempts attempts)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: ensure / ensureNotNull
// ═══════════════════════════════════════════════════════════════════════

data class UserRecord(val name: String, val profile: String?)

suspend fun featureEnsure() {
    println("=== Feature: ensure / ensureNotNull ===\n")

    val result1 = Async {
        Kap { delay(10); 42 }
            .ensure({ IllegalStateException("must be positive") }) { it > 0 }
    }
    println("  ensure (pass): $result1")

    val failResult = try {
        Async {
            Kap { delay(10); -1 }
                .ensure({ IllegalStateException("must be positive") }) { it > 0 }
        }
    } catch (e: IllegalStateException) {
        "caught: ${e.message}"
    }
    println("  ensure (fail): $failResult")

    val result2 = Async {
        Kap { delay(10); UserRecord("Alice", "premium") }
            .ensureNotNull({ IllegalStateException("profile is null") }) { it.profile }
    }
    println("  ensureNotNull: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: catching — Result wrapping
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureCatching() {
    println("=== Feature: catching ===\n")

    val success: Result<String> = Async { catching { delay(10); "hello" } }
    println("  catching success: $success")

    val failure: Result<String> = Async { catching { throw RuntimeException("boom") } }
    println("  catching failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Flow.mapKap (concurrent flow mapping)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFlowMapEffect() {
    println("=== Feature: Flow.mapKap (concurrent) ===\n")

    val results = (1..5).asFlow()
        .mapKap(concurrency = 3) { id -> Kap { delay(20); "user-$id" } }
        .toList()
    println("  mapKap(c=3): $results\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Flow.mapKapOrdered (concurrent, preserves order)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFlowMapEffectOrdered() {
    println("=== Feature: Flow.mapKapOrdered (ordered) ===\n")

    val results = flowOf("a", "b", "c", "d", "e")
        .mapKapOrdered(concurrency = 3) { s ->
            Kap { delay((50 - s[0].code % 5 * 10).toLong()); s.uppercase() }
        }
        .toList()
    println("  mapKapOrdered(c=3): $results (order preserved)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Flow.firstAsKap
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFlowFirstAsKap() {
    println("=== Feature: Flow.firstAsKap ===\n")

    val result = Async {
        flowOf("first", "second", "third").firstAsKap()
    }
    println("  firstAsKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Deferred.toKap
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDeferredToKap() {
    println("=== Feature: Deferred.toKap ===\n")

    val result = coroutineScope {
        val deferred = async { delay(30); "deferred-value" }
        Async { deferred.toKap() }
    }
    println("  Deferred.toKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation { bind() } builder
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureComputation() {
    println("=== Feature: computation { bind() } ===\n")

    val result = Async {
        computation {
            val userId = bind { delay(20); "user-42" }
            val profile = Kap { delay(15); "profile-for-$userId" }.bind()
            val cart = bind { delay(10); "cart-of-$userId" }
            "$profile | $cart"
        }
    }
    println("  computation: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: keepFirst / keepSecond
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKeepFirst() {
    println("=== Feature: keepFirst / keepSecond ===\n")

    val user = Async {
        Kap { delay(30); "Alice" }
            .keepFirst(Kap { delay(20); println("    (side-effect: logged access)") })
    }
    println("  keepFirst: $user")

    val cart = Async {
        Kap { delay(10); println("    (side-effect: tracked event)") }
            .keepSecond(Kap { delay(20); "3 items" })
    }
    println("  keepSecond: $cart\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: discard() and peek { }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDiscardAndPeek() {
    println("=== Feature: discard / peek ===\n")

    val unit: Unit = Async {
        Kap { delay(10); "some-value" }.discard()
    }
    println("  discard: $unit (value discarded)")

    val peeked = Async {
        Kap { delay(10); "peek-value" }
            .peek { v -> println("    (peeked: $v)") }
    }
    println("  peek returned: $peeked\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: .on(Dispatchers.IO) and .named("name")
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureOnAndNamed() {
    println("=== Feature: on / named ===\n")

    val result = Async {
        kap { a: String, b: String -> "$a|$b" }
            .with(Kap { delay(20); "io-result" }
                .on(Dispatchers.IO)
                .named("io-task"))
            .with(Kap { delay(15); "default-result" }
                .named("default-task"))
    }
    println("  on/named: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: .await() from suspend context
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureAwait() {
    println("=== Feature: await ===\n")

    val result = Async {
        kap { a: String, b: String -> "$a|$b" }
            .with { Kap { delay(30); "fast" }.timeout(100.milliseconds, "cached").await() }
            .with { delay(20); "normal" }
    }
    println("  await: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: delayed(duration, value)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDelayed() {
    println("=== Feature: delayed ===\n")

    val start = System.currentTimeMillis()
    val result = Async {
        raceN(
            Kap { delay(200); "slow-service" },
            delayed(50.milliseconds, "timeout-fallback"),
        )
    }
    val elapsed = System.currentTimeMillis() - start
    println("  delayed race: $result (${elapsed}ms)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: traced with KapTracer
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraced() {
    println("=== Feature: traced with KapTracer ===\n")

    val events = mutableListOf<String>()
    val tracer = KapTracer { event ->
        when (event) {
            is TraceEvent.Started -> events.add("started:${event.name}")
            is TraceEvent.Succeeded -> events.add("ok:${event.name}(${event.duration.inWholeMilliseconds}ms)")
            is TraceEvent.Failed -> events.add("fail:${event.name}")
        }
    }

    val result = Async {
        kap { a: String, b: String -> "$a|$b" }
            .with(Kap { delay(30); "user-data" }.traced("fetchUser", tracer))
            .with(Kap { delay(20); "cart-data" }.traced("fetchCart", tracer))
    }
    println("  traced result: $result")
    println("  trace events: $events\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: bracketCase with ExitCase handling (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureBracketCase() {
    println("=== Feature: bracketCase with ExitCase ===\n")

    // Success path — commit
    val successResult = Async {
        bracketCase(
            acquire = { delay(10); MockConnection("tx-db") },
            use = { tx -> Kap { tx.query("INSERT INTO orders VALUES (1)") } },
            release = { tx, case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    commit (success: ${case.value})")
                    is ExitCase.Failed -> println("    rollback (error: ${case.error.message})")
                    is ExitCase.Cancelled -> println("    rollback (cancelled)")
                }
                tx.close()
            },
        )
    }
    println("  Success path: $successResult")

    // Failure path — rollback
    val fallbackResult = try {
        Async {
            bracketCase(
                acquire = { delay(10); MockConnection("tx-db-fail") },
                use = { _: MockConnection -> Kap<String> { throw RuntimeException("constraint violation") } },
                release = { tx, case ->
                    when (case) {
                        is ExitCase.Completed<*> -> println("    commit")
                        is ExitCase.Failed -> println("    rollback (error: ${case.error.message})")
                        is ExitCase.Cancelled -> println("    rollback (cancelled)")
                    }
                    tx.close()
                },
            )
        }
    } catch (e: RuntimeException) {
        "caught: ${e.message}"
    }
    println("  Failure path: $fallbackResult\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Resource.zip composable resources (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureResourceComposable() {
    println("=== Feature: Resource.zip composable ===\n")

    data class InfraResult(val dbData: String, val cacheData: String, val httpData: String)

    val dbResource = Resource({ delay(10); MockConnection("db") }, { it.close() })
    val cacheResource = Resource({ delay(10); MockConnection("cache") }, { it.close() })
    val httpResource = Resource({ delay(10); MockConnection("http") }, { it.close() })

    val combined = Resource.zip(dbResource, cacheResource, httpResource) { db, cache, http ->
        Triple(db, cache, http)
    }

    val result = Async {
        combined.useKap { (db, cache, http) ->
            kap(::InfraResult)
                .with { db.query("SELECT * FROM users") }
                .with { cache.get("session:abc") }
                .with { http.get("/api/health") }
        }
    }
    println("  Resource.zip + useKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: guarantee and guaranteeCase (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureGuarantee() {
    println("=== Feature: guarantee & guaranteeCase ===\n")

    // guarantee: finalizer always runs
    val result1 = Async {
        Kap { delay(20); "fetched-data" }
            .guarantee { println("    guarantee: cleanup ran (always)") }
    }
    println("  guarantee result: $result1")

    // guaranteeCase: finalizer receives ExitCase
    val result2 = Async {
        Kap { delay(20); "metrics-data" }
            .guaranteeCase { case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    guaranteeCase: success -> record latency")
                    is ExitCase.Failed -> println("    guaranteeCase: failed -> ${case.error.message}")
                    is ExitCase.Cancelled -> println("    guaranteeCase: cancelled -> no-op")
                }
            }
    }
    println("  guaranteeCase result: $result2")

    // guaranteeCase on failure
    val result3 = try {
        Async {
            Kap<String> { throw RuntimeException("service down") }
                .guaranteeCase { case ->
                    when (case) {
                        is ExitCase.Completed<*> -> println("    guaranteeCase: success")
                        is ExitCase.Failed -> println("    guaranteeCase: failed -> ${case.error.message}")
                        is ExitCase.Cancelled -> println("    guaranteeCase: cancelled")
                    }
                }
        }
    } catch (e: RuntimeException) {
        "caught: ${e.message}"
    }
    println("  guaranteeCase on failure: $result3\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: retryOrElse with fallback (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetryOrElse() {
    println("=== Feature: retryOrElse ===\n")

    var attempts = 0
    suspend fun unreliableService(): String {
        attempts++
        throw RuntimeException("down (attempt $attempts)")
    }

    val policy = Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds)

    val result = Async {
        Kap { unreliableService() }
            .retryOrElse(policy) { err -> "fallback after $attempts attempts: ${err.message}" }
    }
    println("  retryOrElse: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: retryWithResult metadata (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetryWithResult() {
    println("=== Feature: retryWithResult ===\n")

    var attempts = 0
    suspend fun flakyApi(): String {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        return "data from attempt $attempts"
    }

    val policy = Schedule.times<Throwable>(5) and Schedule.exponential(10.milliseconds)

    val retryResult: RetryResult<String> = Async {
        Kap { flakyApi() }.retryWithResult(policy)
    }
    println("  value:      ${retryResult.value}")
    println("  attempts:   ${retryResult.attempts}")
    println("  totalDelay: ${retryResult.totalDelay}\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: kapV + withV builder style validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKapVWithV() {
    println("=== Feature: kapV + withV builder ===\n")

    val result: Either<NonEmptyList<RegError>, User> = Async {
        kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("Bob") }
            .withV { validateEmail("bob@example.com") }
            .withV { validateAge(30) }
            .withV { checkUsername("bobby") }
    }
    when (result) {
        is Either.Right -> println("  Valid user: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }

    // All invalid — errors accumulate
    val invalid: Either<NonEmptyList<RegError>, User> = Async {
        kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("X") }
            .withV { validateEmail("no-at-sign") }
            .withV { validateAge(5) }
            .withV { checkUsername("ab") }
    }
    when (invalid) {
        is Either.Right -> println("  Valid: ${invalid.value}")
        is Either.Left -> println("  ${invalid.value.size} accumulated errors: ${invalid.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: valid(), invalid(), invalidAll() entry points (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureValidEntryPoints() {
    println("=== Feature: valid / invalid / invalidAll entry points ===\n")

    val ok: Either<NonEmptyList<RegError>, ValidName> = Async {
        valid<RegError, ValidName>(ValidName("Alice"))
    }
    println("  valid():      $ok")

    val err: Either<NonEmptyList<RegError>, ValidName> = Async {
        invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
    }
    println("  invalid():    $err")

    val errs: Either<NonEmptyList<RegError>, ValidName> = Async {
        invalidAll<RegError, ValidName>(
            nonEmptyListOf(RegError.NameTooShort("too short"), RegError.InvalidEmail("bad email"))
        )
    }
    println("  invalidAll(): $errs\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: catching for arrow validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureValidCatching() {
    println("=== Feature: catching (exception -> validation error) ===\n")

    val success: Either<NonEmptyList<RegError>, String> = Async {
        Kap { delay(10); "valid-data" }
            .catching { e -> RegError.NameTooShort("caught: ${e.message}") }
    }
    println("  catching success: $success")

    val failure: Either<NonEmptyList<RegError>, String> = Async {
        Kap<String> { throw IllegalArgumentException("bad input") }
            .catching { e -> RegError.InvalidEmail("caught: ${e.message}") }
    }
    println("  catching failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: ensureV and ensureVAll guards (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureEnsureV() {
    println("=== Feature: ensureV & ensureVAll ===\n")

    // ensureV — single error on predicate failure
    val pass: Either<NonEmptyList<RegError>, Int> = Async {
        Kap { delay(10); 25 }
            .ensureV(
                error = { age -> RegError.AgeTooLow("Age $age too low") },
                predicate = { it >= 18 },
            )
    }
    println("  ensureV pass: $pass")

    val fail: Either<NonEmptyList<RegError>, Int> = Async {
        Kap { delay(10); 12 }
            .ensureV(
                error = { age -> RegError.AgeTooLow("Age $age too low") },
                predicate = { it >= 18 },
            )
    }
    println("  ensureV fail: $fail")

    // ensureVAll — multiple errors on predicate failure
    val failAll: Either<NonEmptyList<RegError>, String> = Async {
        Kap { delay(10); "X" }
            .ensureVAll(
                errors = { name ->
                    nonEmptyListOf(
                        RegError.NameTooShort("'$name' too short"),
                        RegError.UsernameTaken("'$name' reserved"),
                    )
                },
                predicate = { it.length >= 3 },
            )
    }
    println("  ensureVAll fail: $failAll\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: mapV transform (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMapV() {
    println("=== Feature: mapV ===\n")

    val result: Either<NonEmptyList<RegError>, String> = Async {
        Kap { delay(10); Either.Right(ValidName("Alice")) as Either<NonEmptyList<RegError>, ValidName> }
            .mapV { name -> "Hello, ${name.value}!" }
    }
    println("  mapV: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: mapError transform (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMapError() {
    println("=== Feature: mapError ===\n")

    val original: Either<NonEmptyList<RegError>, ValidName> = Async {
        invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
    }
    println("  original errors: $original")

    val mapped: Either<NonEmptyList<String>, ValidName> = Async {
        invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
            .mapError { regError -> "mapped: ${regError.message}" }
    }
    println("  mapError:        $mapped\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: recoverV recovery (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRecoverV() {
    println("=== Feature: recoverV ===\n")

    val result: Either<NonEmptyList<RegError>, String> = Async {
        Kap<Either<NonEmptyList<RegError>, String>> {
            throw RuntimeException("network timeout")
        }.recoverV { e ->
            RegError.InvalidEmail("recovered: ${e.message}")
        }
    }
    println("  recoverV: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: orThrow unwrap (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureOrThrow() {
    println("=== Feature: orThrow ===\n")

    // Success — unwraps cleanly
    val name: ValidName = Async {
        valid<RegError, ValidName>(ValidName("Alice")).orThrow()
    }
    println("  orThrow success: $name")

    // Failure — throws ValidationException
    val caught = try {
        Async {
            invalid<RegError, ValidName>(RegError.NameTooShort("too short")).orThrow()
        }
    } catch (e: ValidationException) {
        "caught ValidationException: ${e.errors.size} error(s)"
    }
    println("  orThrow failure: $caught\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: traverseV collection validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverseV() {
    println("=== Feature: traverseV ===\n")

    val names = listOf("Alice", "Bob", "X", "Y")

    val result: Either<NonEmptyList<RegError>, List<ValidName>> = Async {
        names.traverseV { name ->
            Kap { validateName(name) }
        }
    }
    when (result) {
        is Either.Right -> println("  traverseV all valid: ${result.value}")
        is Either.Left -> println("  traverseV errors: ${result.value.map { it.message }}")
    }

    val allValid: Either<NonEmptyList<RegError>, List<ValidName>> = Async {
        listOf("Alice", "Bob", "Charlie").traverseV { name ->
            Kap { validateName(name) }
        }
    }
    when (allValid) {
        is Either.Right -> println("  traverseV all pass: ${allValid.value}")
        is Either.Left -> println("  traverseV errors: ${allValid.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: sequenceV validated computations (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureSequenceV() {
    println("=== Feature: sequenceV ===\n")

    val validations: List<Kap<Either<NonEmptyList<RegError>, ValidName>>> = listOf(
        Kap { validateName("Alice") },
        Kap { validateName("Bob") },
        Kap { validateName("X") },
    )

    val result: Either<NonEmptyList<RegError>, List<ValidName>> = Async {
        validations.sequenceV()
    }
    when (result) {
        is Either.Right -> println("  sequenceV all valid: ${result.value}")
        is Either.Left -> println("  sequenceV errors: ${result.value.map { it.message }}")
    }

    // All valid
    val allOk: Either<NonEmptyList<RegError>, List<ValidName>> = Async {
        listOf(
            Kap { validateName("Alice") },
            Kap { validateName("Bob") },
        ).sequenceV()
    }
    when (allOk) {
        is Either.Right -> println("  sequenceV all pass: ${allOk.value}")
        is Either.Left -> println("  sequenceV errors: ${allOk.value.map { it.message }}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: raceEither with different types (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRaceEither() {
    println("=== Feature: raceEither ===\n")

    suspend fun fetchCachedName(): String { delay(20); return "cached-Alice" }
    suspend fun fetchFreshAge(): Int { delay(100); return 30 }

    val result: Either<String, Int> = Async {
        raceEither(
            fa = Kap { fetchCachedName() },
            fb = Kap { fetchFreshAge() },
        )
    }
    when (result) {
        is Either.Left -> println("  raceEither: Left won (fast) = ${result.value}")
        is Either.Right -> println("  raceEither: Right won (fast) = ${result.value}")
    }
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Main — runs every example
// ═══════════════════════════════════════════════════════════════════════

suspend fun main() {
    println("╔══════════════════════════════════════════════╗")
    println("║  KAP README Examples — All Compilable        ║")
    println("╚══════════════════════════════════════════════╝\n")

    // Hero & Pain
    heroCheckout()
    rawCoroutinesCheckout()
    arrowCheckout()

    // Key concepts
    constructorIsAFunction()
    nothingRunsUntilAsync()
    allValsNoNulls()

    // Three Primitives
    threePrimitiveWith()
    threePrimitiveFollowedBy()
    threePrimitiveFlatMap()

    // Value-Dependent Phases
    phasedFlatMapRaw()
    phasedFlatMapKap()

    // Quick Start
    quickStartBasic()
    quickStartResilience()
    quickStartValidation()

    // Choose Your Style
    chooseYourStyle()

    // Feature Showcase
    featureSettled()
    featureTimeoutRace()
    featureRetrySchedule()
    featureResourceSafety()
    featureRacing()
    featureTraverse()
    featureRaceQuorum()
    featureCircuitBreaker()
    featureMemoize()
    featureParallelValidation()
    featurePhasedValidation()
    featureAttempt()
    featureFallbacks()
    featureKapBuilder()
    executionModel()

    // Reordered execution
    reorderedWithoutBarrier()
    reorderedWithBarrier()

    // BFF example
    bffMobileApp()

    // Additional Feature Showcase
    featureThenValue()
    featureTraverseDiscard()
    featureSequence()
    featureRaceAll()
    featureTimeout()
    featureRecover()
    featureRetrySimple()
    featureEnsure()
    featureCatching()
    featureFlowMapEffect()
    featureFlowMapEffectOrdered()
    featureFlowFirstAsKap()
    featureDeferredToKap()
    featureComputation()
    featureKeepFirst()
    featureDiscardAndPeek()
    featureOnAndNamed()
    featureAwait()
    featureDelayed()
    featureTraced()

    // kap-resilience additional examples
    featureBracketCase()
    featureResourceComposable()
    featureGuarantee()
    featureRetryOrElse()
    featureRetryWithResult()

    // kap-arrow additional examples
    featureKapVWithV()
    featureValidEntryPoints()
    featureValidCatching()
    featureEnsureV()
    featureMapV()
    featureMapError()
    featureRecoverV()
    featureOrThrow()
    featureTraverseV()
    featureSequenceV()
    featureRaceEither()

    println("All README examples passed!")
}
