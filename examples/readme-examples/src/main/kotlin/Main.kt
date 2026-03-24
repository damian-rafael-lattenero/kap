@file:Suppress("unused", "RedundantSuspendModifier", "UNUSED_VARIABLE")

import applicative.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.*
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
            .followedBy { validateStock() }     // ── phase 2: barrier
            .with { calcShipping() }            // ┐
            .with { calcTax() }                 // ├─ phase 3: parallel
            .with { calcDiscounts() }           // ┘
            .followedBy { reservePayment() }    // ── phase 4: barrier
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

    val plan: Computation<Dashboard> = kap(::Dashboard)
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
    println("=== Primitive: .followedBy (barrier) ===\n")

    val result = Async {
        kap(::R3)
            .with { fetchA() }             // ┐ parallel
            .with { fetchB() }             // ┘
            .followedBy { validate() }     // waits for A and B
    }
    println("  .followedBy result: $result\n")
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
    println("=== Primitive: .flatMap (value-dependent phases) ===\n")

    val userId = "user-1"
    val dashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }       // ┐ phase 1: parallel
            .with { fetchPreferences(userId) }   // │
            .with { fetchLoyaltyTier(userId) }   // ┘
            .flatMap { ctx ->                     // ── barrier: phase 2 NEEDS ctx
                kap(::PersonalizedDashboard)
                    .with { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
                    .with { fetchPromotions(ctx.tier) }           // │
                    .with { fetchTrending(ctx.prefs) }            // ┘
            }
    }
    println("  .flatMap result: $dashboard\n")
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
    println("=== Phase Dependencies: KAP flatMap ===\n")
    val userId = "user-42"

    val dashboard: FinalDashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }
            .with { fetchPreferences(userId) }
            .with { fetchLoyaltyTier(userId) }
            .flatMap { ctx ->
                kap(::EnrichedContent)
                    .with { fetchRecommendations(ctx.profile) }
                    .with { fetchPromotions(ctx.tier) }
                    .with { fetchTrending(ctx.prefs) }
                    .with { fetchHistory(ctx.profile) }
                    .flatMap { enriched ->
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
            .with(Computation { fetchDashUser() }
                .withCircuitBreaker(breaker)
                .retry(retryPolicy))
            .with(Computation { fetchFromSlowApi() }
                .timeoutRace(100.milliseconds, Computation { fetchFromCache() }))
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

    // Style 3: combine with pre-built Computations
    val s3 = Async {
        combine(
            Computation { fetchDashUser() },
            Computation { fetchDashCart() },
            Computation { fetchDashPromos() },
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

suspend fun featureSettled() {
    println("=== Feature: Partial Failure with .settled() ===\n")

    suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
    suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
    suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

    data class PartialDashboard(val user: String, val cart: String, val config: String)

    val dashboard = Async {
        kap { user: Result<String>, cart: String, config: String ->
            PartialDashboard(user.getOrDefault("anonymous"), cart, config)
        }
            .with(Computation { fetchUserMayFail() }.settled())
            .with { fetchCartAlways() }
            .with { fetchConfigAlways() }
    }
    println("  settled: $dashboard")

    // traverseSettled: process ALL items, no cancellation on failure
    val ids = listOf(1, 2, 3, 4, 5)
    val results: List<Result<String>> = Async {
        ids.traverseSettled { id ->
            Computation {
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
        Computation { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Computation { fetchFromFallback() })
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
        Computation { flakyService() }.retry(policy)
    }
    println("  Result: $result")

    // Inline retry with the simple core overload
    attempts = 0
    data class RetryResult(val user: String, val service: String)
    val result2 = Async {
        kap { user: String, service: String -> RetryResult(user, service) }
            .with { fetchDashUser() }
            .with(Computation { flakyService() }
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
                use = { conn -> Computation { conn.query("SELECT 1") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openCacheConnection() },
                use = { conn -> Computation { conn.get("key") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openHttpClient() },
                use = { client -> Computation { client.get("/api") } },
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
        infra.useComputation { (db, cache, http) ->
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
            use = { tx -> Computation { tx.query("INSERT 1") } },
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
            Computation { fetchFromRegionUS() },
            Computation { fetchFromRegionEU() },
            Computation { fetchFromRegionAP() },
        )
    }
    println("  raceN winner: $fastest")

    // raceEither with different types
    val raceResult: Either<String, Int> = Async {
        raceEither(
            fa = Computation { delay(30); "fast-string" },
            fb = Computation { delay(100); 42 },
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
            Computation { delay(20); "user-$id" }
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
            Computation { fetchReplicaA() },
            Computation { fetchReplicaB() },
            Computation { fetchReplicaC() },
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
        Computation { fetchDashUser() }
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
    val fetchOnce = Computation { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

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
        Computation { "hello" }.attempt()
    }
    println("  attempt success: $success")

    val failure: Either<Throwable, String> = Async {
        Computation<String> { throw RuntimeException("boom") }.attempt()
    }
    println("  attempt failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: firstSuccessOf & orElse (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFallbacks() {
    println("=== Feature: firstSuccessOf & orElse ===\n")

    val result = Async {
        Computation<String> { throw RuntimeException("fail-1") }
            .orElse(Computation { "fallback-ok" })
    }
    println("  orElse: $result")

    val result2 = Async {
        firstSuccessOf(
            Computation { throw RuntimeException("fail-1") },
            Computation { throw RuntimeException("fail-2") },
            Computation { "third-wins" },
        )
    }
    println("  firstSuccessOf: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation {} builder (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureComputationBuilder() {
    println("=== Feature: computation {} builder ===\n")

    val result = Async {
        computation {
            val user = Computation { fetchDashUser() }.bind()
            val cart = Computation { fetchDashCart() }.bind()
            "$user has $cart"
        }
    }
    println("  computation {}: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Execution Model — followedBy vs thenValue
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
        Computation { fetchSession("tok-abc") }         // phase 1: authenticate
            .flatMap { session ->                        // ── barrier: session ready
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
//  Main — runs every example
// ═══════════════════════════════════════════════════════════════════════

suspend fun main() {
    println("╔══════════════════════════════════════════════╗")
    println("║  KAP README Examples — All Compilable        ║")
    println("╚══════════════════════════════════════════════╝\n")

    // Hero & Pain
    heroCheckout()
    rawCoroutinesCheckout()

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
    featureComputationBuilder()
    executionModel()

    // Reordered execution
    reorderedWithoutBarrier()
    reorderedWithBarrier()

    // BFF example
    bffMobileApp()

    println("All README examples passed!")
}
