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

// No wrapper types needed — @KapTypeSafe named builders make
// even all-String / all-Boolean parameters type-safe by position.

@KapTypeSafe
data class CheckoutResult(
    val user: String,
    val cart: String,
    val promos: String,
    val inventory: Boolean,
    val stock: Boolean,
    val shipping: Double,
    val tax: Double,
    val discounts: Double,
    val payment: String,
    val confirmation: String,
    val email: String,
)

suspend fun fetchUser(): String { delay(50); return "Alice (id=42)" }
suspend fun fetchCart(): String { delay(40); return "3 items, $147.50" }
suspend fun fetchPromos(): String { delay(30); return "SUMMER20 (20% off)" }
suspend fun fetchInventory(): Boolean { delay(50); return true }
suspend fun validateStock(): Boolean { delay(20); return true }
suspend fun calcShipping(): Double { delay(30); return 5.99 }
suspend fun calcTax(): Double { delay(20); return 12.38 }
suspend fun calcDiscounts(): Double { delay(15); return 29.50 }
suspend fun reservePayment(): String { delay(40); return "visa-4242-authorized" }
suspend fun generateConfirmation(): String { delay(30); return "order-#90142" }
suspend fun sendEmail(): String { delay(20); return "sent-to-alice@example.com" }

suspend fun fetchName(): String { delay(30); return "Alice" }
suspend fun fetchAge(): Int { delay(20); return 30 }

@KapTypeSafe
data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchDashUser(): String { delay(30); return "Alice" }
suspend fun fetchDashCart(): String { delay(20); return "3 items" }
suspend fun fetchDashPromos(): String { delay(10); return "SAVE20" }

// ═══════════════════════════════════════════════════════════════════════
//  Section: Hero — KAP Checkout (11 services, 5 phases)
// ═══════════════════════════════════════════════════════════════════════

suspend fun heroCheckout() {
    println("=== Hero: KAP Checkout (11 services, 5 phases) ===\n")

    val checkout: CheckoutResult = kap(::CheckoutResult)
            .withUser { fetchUser() }              // ┐
            .withCart { fetchCart() }               // ├─ phase 1: parallel
            .withPromos { fetchPromos() }           // │
            .withInventory { fetchInventory() }    // ┘
            .thenStock { validateStock() }         // ── phase 2: barrier
            .withShipping { calcShipping() }       // ┐
            .withTax { calcTax() }                 // ├─ phase 3: parallel
            .withDiscounts { calcDiscounts() }     // ┘
            .thenPayment { reservePayment() }      // ── phase 4: barrier
            .withConfirmation { generateConfirmation() }  // ┐ phase 5: parallel
            .withEmail { sendEmail() }                    // ┘
            .executeGraph()

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

data class Phase1(val user: String, val cart: String, val promos: String, val inventory: Boolean)
data class Phase3(val shipping: Double, val tax: Double, val discounts: Double)

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

@KapTypeSafe
data class Greeting(val text: String, val target: String)

suspend fun constructorIsAFunction() {
    println("=== A constructor is a function ===\n")

    // ::Greeting has type (String, String) -> Greeting
    val g1: Greeting = kap(::Greeting)
            .withText { fetchName() }
            .withTarget { "hello" }
            .executeGraph()
    println("  Constructor ref: $g1")

    // Manual currying for lambda variables:
    val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
    val g2: String = Kap.of { name: String -> { age: Int -> greet(name, age) } }
            .with { fetchName() }
            .with { fetchAge() }
            .executeGraph()
    println("  Lambda function: $g2")

    // Manual currying for local function references:
    fun buildSummary(name: String, items: Int): String = "$name has $items items"

    val g3: String = Kap.of { name: String -> { items: Int -> buildSummary(name, items) } }
            .with { fetchName() }
            .with { 5 }
            .executeGraph()
    println("  Function ref:   $g3\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Nothing Runs Until executeGraph()
// ═══════════════════════════════════════════════════════════════════════

suspend fun nothingRunsUntilAsync() {
    println("=== Nothing runs until executeGraph() ===\n")

    val plan: Kap<Dashboard> = kap(::Dashboard)
        .withUser { fetchDashUser() }
        .withCart { fetchDashCart() }
        .withPromos { fetchDashPromos() }

    println("  Plan built. Nothing has executed yet.")
    println("  plan is: ${plan::class.simpleName}")

    val result: Dashboard = plan.executeGraph()
    println("  After Async: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: All val, no null
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
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
    val kapResult: DashboardView = kap(::DashboardView)
            .withUser { fetchDashUser() }
            .withCart { fetchDashCart() }
            .executeGraph()
    println("  KAP (val, safe):  $kapResult\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Three Primitives
// ═══════════════════════════════════════════════════════════════════════

suspend fun fetchA(): String { delay(30); return "A" }
suspend fun fetchB(): String { delay(20); return "B" }
suspend fun validate(): String { delay(10); return "valid" }

@KapTypeSafe
data class AB(val a: String, val b: String)
@KapTypeSafe
data class R3(val a: String, val b: String, val c: String)

suspend fun threePrimitiveWith() {
    println("=== Primitive: .with (parallel) ===\n")

    val result = kap(::AB)
            .withA { fetchA() }   // ┐ parallel
            .withB { fetchB() }   // ┘
            .executeGraph()
    println("  .with result: $result\n")
}

suspend fun threePrimitiveFollowedBy() {
    println("=== Primitive: .then (barrier) ===\n")

    val result = kap(::R3)
            .withA { fetchA() }             // ┐ parallel
            .withB { fetchB() }             // ┘
            .thenC { validate() }           // waits for A and B
            .executeGraph()
    println("  .then result: $result\n")
}

@KapTypeSafe
data class UserContext(val profile: String, val prefs: String, val tier: String)
@KapTypeSafe
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
    val dashboard = kap(::UserContext)
            .withProfile { fetchProfile(userId) }       // ┐ phase 1: parallel
            .withPrefs { fetchPreferences(userId) }     // │
            .withTier { fetchLoyaltyTier(userId) }      // ┘
            .andThen { ctx ->                            // ── barrier: phase 2 NEEDS ctx
                kap(::PersonalizedDashboard)
                    .withRecs { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
                    .withPromos { fetchPromotions(ctx.tier) }         // │
                    .withTrending { fetchTrending(ctx.prefs) }        // ┘
            }
            .executeGraph()
    println("  .andThen result: $dashboard\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Value-Dependent Phases (raw vs KAP)
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
data class EnrichedContent(val recs: String, val promos: String, val trending: String, val history: String)
@KapTypeSafe
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

    val dashboard: FinalDashboard = kap(::UserContext)
            .withProfile { fetchProfile(userId) }
            .withPrefs { fetchPreferences(userId) }
            .withTier { fetchLoyaltyTier(userId) }
            .andThen { ctx ->
                kap(::EnrichedContent)
                    .withRecs { fetchRecommendations(ctx.profile) }
                    .withPromos { fetchPromotions(ctx.tier) }
                    .withTrending { fetchTrending(ctx.prefs) }
                    .withHistory { fetchHistory(ctx.profile) }
                    .andThen { enriched ->
                        kap(::FinalDashboard)
                            .withLayout { renderLayout(ctx, enriched) }
                            .withAnalytics { trackAnalytics(ctx, enriched) }
                    }
            }
            .executeGraph()

    println("  KAP result: $dashboard\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Section: Quick Start — Basic
// ═══════════════════════════════════════════════════════════════════════

suspend fun quickStartBasic() {
    println("=== Quick Start: Basic ===\n")

    val result = kap(::Dashboard)
            .withUser { fetchDashUser() }    // ┐ all three in parallel
            .withCart { fetchDashCart() }     // │ total time = max(individual)
            .withPromos { fetchDashPromos() } // ┘ not sum
            .executeGraph()
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

    val result = kap(::Dashboard)
            .withUser(Kap { fetchDashUser() }
                .withCircuitBreaker(breaker)
                .retry(retryPolicy))
            .withCart(Kap { fetchFromSlowApi() }
                .timeoutRace(100.milliseconds, Kap { fetchFromCache() }))
            .withPromos { fetchDashPromos() }
            .executeGraph()
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

    val valid: Either<NonEmptyList<RegError>, User> = kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("Alice") }
            .withV { validateEmail("alice@example.com") }
            .withV { validateAge(25) }
            .withV { checkUsername("alice") }
            .executeGraph()
    when (valid) {
        is Either.Right -> println("  Valid: ${valid.value}")
        is Either.Left -> println("  Errors: ${valid.value.map { it.message }}")
    }

    val invalid: Either<NonEmptyList<RegError>, User> = kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("A") }
            .withV { validateEmail("bad") }
            .withV { validateAge(10) }
            .withV { checkUsername("al") }
            .executeGraph()
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
    val s1 = kap(::Dashboard)
            .withUser { fetchDashUser() }
            .withCart { fetchDashCart() }
            .withPromos { fetchDashPromos() }
            .executeGraph()
    println("  kap+with:  $s1")

    // Style 2: combine with suspend lambdas
    val s2 = combine(
            { fetchDashUser() },
            { fetchDashCart() },
            { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
            .executeGraph()
    println("  combine:   $s2")

    // Style 3: combine with pre-built Kaps
    val s3 = combine(
            Kap { fetchDashUser() },
            Kap { fetchDashCart() },
            Kap { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
            .executeGraph()
    println("  zip:       $s3")

    // Bonus: pair
    val (user, cart) = pair({ fetchDashUser() }, { fetchDashCart() }).executeGraph()
    println("  pair:      ($user, $cart)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Partial Failure with .settled() (kap-core)
// ═══════════════════════════════════════════════════════════════════════

// Domain type that tolerates partial failure
@KapTypeSafe
data class PartialDashboard(val user: String, val cart: String, val config: String)

// Services: one is unreliable, the others always succeed
suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

// Builder function: receives Result<String> for the unreliable service, uses fallback
@KapTypeSafe
fun buildPartialDashboard(user: Result<String>, cart: String, config: String): PartialDashboard =
    PartialDashboard(
        user = user.getOrDefault("anonymous"),  // failed? use fallback value
        cart = cart,
        config = config,
    )

suspend fun featureSettled() {
    println("=== Feature: Partial Failure with .settled() ===\n")

    // settled { } wraps the result in Result<T> — failure doesn't cancel siblings
    val dashboard = kap(BuildPartialDashboard)
            .withUser(settled { fetchUserMayFail() })  // Result<String> — won't cancel siblings
            .withCart { fetchCartAlways() }              // normal String — failure here cancels all
            .withConfig { fetchConfigAlways() }          // normal String
            .executeGraph()
    println("  settled: $dashboard")
    // PartialDashboard(user=anonymous, cart=cart-ok, config=config-ok)
    // fetchUserMayFail() threw → settled { } wrapped as Result.failure → buildPartialDashboard used fallback

    // traverseSettled: process ALL items, no cancellation on failure
    val ids = listOf(1, 2, 3, 4, 5)
    val results: List<Result<String>> = ids.traverseSettled { id ->
            Kap {
                if (id % 2 == 0) throw RuntimeException("fail-$id")
                "user-$id"
            }
        }.executeGraph()
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
    val result = Kap { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
            .executeGraph()
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

    val result = Kap { flakyService() }.retry(policy)
            .executeGraph()
    println("  Result: $result")

    // Inline retry with the simple core overload
    attempts = 0
    data class RetryResult(val user: String, val service: String)
    val result2 = Kap.of { user: String -> { service: String -> RetryResult(user, service) } }
            .with { fetchDashUser() }
            .with(Kap { flakyService() }
                .retry(3, delay = 10.milliseconds))
            .executeGraph()
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
    val result = Kap.of { db: String -> { cache: String -> { api: String -> "$db|$cache|$api" } } }
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
            .executeGraph()
    println("  bracket: $result")

    // Resource monad: compose first, use later
    data class DashboardData(val db: String, val cache: String, val http: String)

    val infra = Resource.zip(
        Resource({ openDbConnection() }, { it.close() }),
        Resource({ openCacheConnection() }, { it.close() }),
        Resource({ openHttpClient() }, { it.close() }),
    ) { db, cache, http -> Triple(db, cache, http) }

    val result2 = infra.useKap { (db, cache, http) ->
            Kap.of { d: String -> { c: String -> { h: String -> DashboardData(d, c, h) } } }
                .with { db.query("SELECT 1") }
                .with { cache.get("user:prefs") }
                .with { http.get("/recommendations") }
        }.executeGraph()
    println("  Resource: $result2")

    // bracketCase: release behavior depends on outcome
    val result3 = bracketCase(
            acquire = { openDbConnection() },
            use = { tx -> Kap { tx.query("INSERT 1") } },
            release = { tx, case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    bracketCase: commit")
                    else -> println("    bracketCase: rollback")
                }
                tx.close()
            },
        ).executeGraph()
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

    val fastest = raceN(
            Kap { fetchFromRegionUS() },
            Kap { fetchFromRegionEU() },
            Kap { fetchFromRegionAP() },
        ).executeGraph()
    println("  raceN winner: $fastest")

    // raceEither with different types
    val raceResult: Either<String, Int> = raceEither(
            fa = Kap { delay(30); "fast-string" },
            fb = Kap { delay(100); 42 },
        ).executeGraph()
    println("  raceEither: $raceResult\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Bounded Parallel Collection Processing (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverse() {
    println("=== Feature: Bounded Parallel Traverse ===\n")

    val userIds = (1..10).toList()

    val results = userIds.traverse(concurrency = 3) { id ->
            Kap { delay(20); "user-$id" }
        }.executeGraph()
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

    val quorum: List<String> = raceQuorum(
            required = 2,
            Kap { fetchReplicaA() },
            Kap { fetchReplicaB() },
            Kap { fetchReplicaC() },
        ).executeGraph()
    println("  raceQuorum(2 of 3): $quorum\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Circuit Breaker (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureCircuitBreaker() {
    println("=== Feature: Circuit Breaker ===\n")

    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

    val result = Kap { fetchDashUser() }
            .timeout(500.milliseconds)
            .withCircuitBreaker(breaker)
            .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
            .recover { "cached-user" }
            .executeGraph()
    println("  Composable chain: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Memoization (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMemoize() {
    println("=== Feature: Memoization ===\n")

    var callCount = 0
    val fetchOnce = Kap { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

    val a = fetchOnce.executeGraph()
    val b = fetchOnce.executeGraph()
    println("  memoizeOnSuccess: a=$a, b=$b, callCount=$callCount\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Parallel Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureParallelValidation() {
    println("=== Feature: Parallel Validation ===\n")

    val result: Either<NonEmptyList<RegError>, User> = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
            { checkUsername("alice") },
        ) { name, email, age, username -> User(name, email, age, username) }
            .executeGraph()
    when (result) {
        is Either.Right -> println("  All pass: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }

    val allFail: Either<NonEmptyList<RegError>, User> = zipV(
            { validateName("A") },
            { validateEmail("bad") },
            { validateAge(10) },
            { checkUsername("al") },
        ) { name, email, age, username -> User(name, email, age, username) }
            .executeGraph()
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

    val result: Either<NonEmptyList<RegError>, Registration> = accumulate {
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
        }.executeGraph()

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

    val success: Either<Throwable, String> = Kap { "hello" }.attempt()
            .executeGraph()
    println("  attempt success: $success")

    val failure: Either<Throwable, String> = Kap<String> { throw RuntimeException("boom") }.attempt()
            .executeGraph()
    println("  attempt failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: firstSuccessOf & orElse (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFallbacks() {
    println("=== Feature: firstSuccessOf & orElse ===\n")

    val result = Kap<String> { throw RuntimeException("fail-1") }
            .orElse(Kap { "fallback-ok" })
            .executeGraph()
    println("  orElse: $result")

    val result2 = firstSuccessOf(
            Kap { throw RuntimeException("fail-1") },
            Kap { throw RuntimeException("fail-2") },
            Kap { "third-wins" },
        ).executeGraph()
    println("  firstSuccessOf: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation {} builder (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKapBuilder() {
    println("=== Feature: computation {} builder ===\n")

    val result = computation {
            val user = Kap { fetchDashUser() }.bind()
            val cart = Kap { fetchDashCart() }.bind()
            "$user has $cart"
        }.executeGraph()
    println("  computation {}: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Execution Model — then vs thenValue
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
fun combineThree(a: String, b: String, c: String): String = "$a+$b+$c"

suspend fun executionModel() {
    println("=== Execution Model ===\n")

    val graph = kap(CombineThree)
        .withA { fetchA() }
        .withB { fetchB() }

    println("  graph built, not executed")
    val result = graph.withC { "C" }.executeGraph()
    println("  executeGraph(): $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Reordered execution: execute params out of constructor order
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
data class Page(val a: String, val b: String, val c: String, val d: String)

suspend fun fetchParamA(): String { delay(30); return "A-val" }
suspend fun fetchParamB(): String { delay(20); return "B-val" }
suspend fun fetchParamC(): String { delay(40); return "C-val" }
suspend fun fetchParamD(): String { delay(10); return "D-val" }

suspend fun reorderedWithoutBarrier() {
    println("=== Reordered: No barrier (all parallel, assemble freely) ===\n")

    val result = combine(
            pair({ fetchParamC() }, { fetchParamD() }),
            pair({ fetchParamA() }, { fetchParamB() }),
        ) { (c, d), (a, b) -> Page(a, b, c, d) }
            .executeGraph()
    println("  result: $result\n")
}

suspend fun reorderedWithBarrier() {
    println("=== Reordered: With barrier (phase 1 -> barrier -> phase 2) ===\n")

    val result = computation {
            val (c, d) = pair({ fetchParamC() }, { fetchParamD() }).bind()
            val (a, b) = pair({ fetchParamA() }, { fetchParamB() }).bind()
            Page(a, b, c, d)
        }.executeGraph()
    println("  result: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  BFF Example — mobile app endpoint aggregating multiple backend services
// ═══════════════════════════════════════════════════════════════════════

data class UserSession(val userId: String, val tier: String, val prefs: List<String>)
data class ProductFeed(val items: List<String>, val sponsored: List<String>)
@KapTypeSafe
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

    val homePage: MobileHomePage = Kap { fetchSession("tok-abc") }         // phase 1: authenticate
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
            .executeGraph()

    println("  homePage: $homePage")
    assert(homePage.session.userId == "u-123")
    assert(homePage.feed.items.size == 2)
    assert(homePage.notifications == 7)
    println("  ✓ BFF example passed\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: thenValue — fill a slot without a barrier
// ═══════════════════════════════════════════════════════════════════════

@KapTypeSafe
data class Enriched(val a: String, val enriched: String, val c: String)

suspend fun featureThenValue() {
    println("=== Feature: thenValue (no barrier) ===\n")

    val result = Kap.of { a: String -> { enriched: String -> { c: String -> Enriched(a, enriched, c) } } }
            .with { delay(30); "data-A" }              // launched at t=0
            .thenValue { delay(50); "enriched" }       // sequential value, no barrier
            .with { delay(20); "data-C" }              // launched at t=0 (overlaps!)
            .executeGraph()
    println("  thenValue: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: traverseDiscard — fire-and-forget parallel processing
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverseDiscard() {
    println("=== Feature: traverseDiscard ===\n")

    val ids = listOf(1, 2, 3, 4, 5)
    val processed = mutableListOf<Int>()

    ids.traverseDiscard(concurrency = 3) { id ->
            Kap { delay(10); synchronized(processed) { processed.add(id) } }
        }.executeGraph()
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

    val results: List<String> = computations.sequence(concurrency = 2).executeGraph()
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

    val winner = replicas.raceAll().executeGraph()
    println("  raceAll winner: $winner\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: timeout — .timeout(duration) { fallback }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTimeout() {
    println("=== Feature: timeout ===\n")

    // timeout with default value
    val result1 = Kap { delay(200); "slow-value" }
            .timeout(50.milliseconds, "default-value")
            .executeGraph()
    println("  timeout(default): $result1")

    // timeout with fallback computation
    val result2 = Kap { delay(200); "slow-value" }
            .timeout(50.milliseconds, Kap { delay(10); "fallback-value" })
            .executeGraph()
    println("  timeout(fallback): $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: recover — .recover { } and .recoverWith { }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRecover() {
    println("=== Feature: recover / recoverWith ===\n")

    val result1 = Kap<String> { throw RuntimeException("oops") }
            .recover { err -> "recovered: ${err.message}" }
            .executeGraph()
    println("  recover: $result1")

    val result2 = Kap<String> { throw RuntimeException("oops") }
            .recoverWith { err -> Kap { delay(10); "recovered-with: ${err.message}" } }
            .executeGraph()
    println("  recoverWith: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: retry (simple) — .retry(maxAttempts, delay)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetrySimple() {
    println("=== Feature: retry (simple) ===\n")

    var attempts = 0
    val result = Kap {
            attempts++
            if (attempts < 3) throw RuntimeException("flake #$attempts")
            "success on attempt $attempts"
        }.retry(maxAttempts = 5, delay = 10.milliseconds, backoff = exponential)
            .executeGraph()
    println("  retry: $result (took $attempts attempts)\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: ensure / ensureNotNull
// ═══════════════════════════════════════════════════════════════════════

data class UserRecord(val name: String, val profile: String?)

suspend fun featureEnsure() {
    println("=== Feature: ensure / ensureNotNull ===\n")

    val result1 = Kap { delay(10); 42 }
            .ensure({ IllegalStateException("must be positive") }) { it > 0 }
            .executeGraph()
    println("  ensure (pass): $result1")

    val failResult = try {
        Kap { delay(10); -1 }
                .ensure({ IllegalStateException("must be positive") }) { it > 0 }
                .executeGraph()
    } catch (e: IllegalStateException) {
        "caught: ${e.message}"
    }
    println("  ensure (fail): $failResult")

    val result2 = Kap { delay(10); UserRecord("Alice", "premium") }
            .ensureNotNull({ IllegalStateException("profile is null") }) { it.profile }
            .executeGraph()
    println("  ensureNotNull: $result2\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: catching — Result wrapping
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureCatching() {
    println("=== Feature: catching ===\n")

    val success: Result<String> = catching { delay(10); "hello" }.executeGraph()
    println("  catching success: $success")

    val failure: Result<String> = catching { throw RuntimeException("boom") }.executeGraph()
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

    val result = flowOf("first", "second", "third").firstAsKap()
            .executeGraph()
    println("  firstAsKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: Deferred.toKap
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDeferredToKap() {
    println("=== Feature: Deferred.toKap ===\n")

    val result = coroutineScope {
        val deferred = async { delay(30); "deferred-value" }
        deferred.toKap().executeGraph()
    }
    println("  Deferred.toKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation { bind() } builder
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureComputation() {
    println("=== Feature: computation { bind() } ===\n")

    val result = computation {
            val userId = bind { delay(20); "user-42" }
            val profile = Kap { delay(15); "profile-for-$userId" }.bind()
            val cart = bind { delay(10); "cart-of-$userId" }
            "$profile | $cart"
        }.executeGraph()
    println("  computation: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: keepFirst / keepSecond
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKeepFirst() {
    println("=== Feature: keepFirst / keepSecond ===\n")

    val user = Kap { delay(30); "Alice" }
            .keepFirst(Kap { delay(20); println("    (side-effect: logged access)") })
            .executeGraph()
    println("  keepFirst: $user")

    val cart = Kap { delay(10); println("    (side-effect: tracked event)") }
            .keepSecond(Kap { delay(20); "3 items" })
            .executeGraph()
    println("  keepSecond: $cart\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: discard() and peek { }
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDiscardAndPeek() {
    println("=== Feature: discard / peek ===\n")

    val unit: Unit = Kap { delay(10); "some-value" }.discard()
            .executeGraph()
    println("  discard: $unit (value discarded)")

    val peeked = Kap { delay(10); "peek-value" }
            .peek { v -> println("    (peeked: $v)") }
            .executeGraph()
    println("  peek returned: $peeked\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: .on(Dispatchers.IO) and .named("name")
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureOnAndNamed() {
    println("=== Feature: on / named ===\n")

    val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
            .with(Kap { delay(20); "io-result" }
                .on(Dispatchers.IO)
                .named("io-task"))
            .with(Kap { delay(15); "default-result" }
                .named("default-task"))
            .executeGraph()
    println("  on/named: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: .await() from suspend context
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureAwait() {
    println("=== Feature: await ===\n")

    val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
            .with { Kap { delay(30); "fast" }.timeout(100.milliseconds, "cached").executeGraph() }
            .with { delay(20); "normal" }
            .executeGraph()
    println("  await: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: delayed(duration, value)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureDelayed() {
    println("=== Feature: delayed ===\n")

    val start = System.currentTimeMillis()
    val result = raceN(
            Kap { delay(200); "slow-service" },
            delayed(50.milliseconds, "timeout-fallback"),
        ).executeGraph()
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

    val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
            .with(Kap { delay(30); "user-data" }.traced("fetchUser", tracer))
            .with(Kap { delay(20); "cart-data" }.traced("fetchCart", tracer))
            .executeGraph()
    println("  traced result: $result")
    println("  trace events: $events\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: bracketCase with ExitCase handling (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureBracketCase() {
    println("=== Feature: bracketCase with ExitCase ===\n")

    // Success path — commit
    val successResult = bracketCase(
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
        ).executeGraph()
    println("  Success path: $successResult")

    // Failure path — rollback
    val fallbackResult = try {
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
            ).executeGraph()
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

    val result = combined.useKap { (db, cache, http) ->
            Kap.of { dbData: String -> { cacheData: String -> { httpData: String -> InfraResult(dbData, cacheData, httpData) } } }
                .with { db.query("SELECT * FROM users") }
                .with { cache.get("session:abc") }
                .with { http.get("/api/health") }
        }.executeGraph()
    println("  Resource.zip + useKap: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: guarantee and guaranteeCase (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureGuarantee() {
    println("=== Feature: guarantee & guaranteeCase ===\n")

    // guarantee: finalizer always runs
    val result1 = Kap { delay(20); "fetched-data" }
            .guarantee { println("    guarantee: cleanup ran (always)") }
            .executeGraph()
    println("  guarantee result: $result1")

    // guaranteeCase: finalizer receives ExitCase
    val result2 = Kap { delay(20); "metrics-data" }
            .guaranteeCase { case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    guaranteeCase: success -> record latency")
                    is ExitCase.Failed -> println("    guaranteeCase: failed -> ${case.error.message}")
                    is ExitCase.Cancelled -> println("    guaranteeCase: cancelled -> no-op")
                }
            }
            .executeGraph()
    println("  guaranteeCase result: $result2")

    // guaranteeCase on failure
    val result3 = try {
        Kap<String> { throw RuntimeException("service down") }
                .guaranteeCase { case ->
                    when (case) {
                        is ExitCase.Completed<*> -> println("    guaranteeCase: success")
                        is ExitCase.Failed -> println("    guaranteeCase: failed -> ${case.error.message}")
                        is ExitCase.Cancelled -> println("    guaranteeCase: cancelled")
                    }
                }
                .executeGraph()
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

    val result = Kap { unreliableService() }
            .retryOrElse(policy) { err -> "fallback after $attempts attempts: ${err.message}" }
            .executeGraph()
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

    val retryResult: RetryResult<String> = Kap { flakyApi() }.retryWithResult(policy)
            .executeGraph()
    println("  value:      ${retryResult.value}")
    println("  attempts:   ${retryResult.attempts}")
    println("  totalDelay: ${retryResult.totalDelay}\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: kapV + withV builder style validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureKapVWithV() {
    println("=== Feature: kapV + withV builder ===\n")

    val result: Either<NonEmptyList<RegError>, User> = kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("Bob") }
            .withV { validateEmail("bob@example.com") }
            .withV { validateAge(30) }
            .withV { checkUsername("bobby") }
            .executeGraph()
    when (result) {
        is Either.Right -> println("  Valid user: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }

    // All invalid — errors accumulate
    val invalid: Either<NonEmptyList<RegError>, User> = kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .withV { validateName("X") }
            .withV { validateEmail("no-at-sign") }
            .withV { validateAge(5) }
            .withV { checkUsername("ab") }
            .executeGraph()
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

    val ok: Either<NonEmptyList<RegError>, ValidName> = valid<RegError, ValidName>(ValidName("Alice"))
            .executeGraph()
    println("  valid():      $ok")

    val err: Either<NonEmptyList<RegError>, ValidName> = invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
            .executeGraph()
    println("  invalid():    $err")

    val errs: Either<NonEmptyList<RegError>, ValidName> = invalidAll<RegError, ValidName>(
            nonEmptyListOf(RegError.NameTooShort("too short"), RegError.InvalidEmail("bad email"))
        ).executeGraph()
    println("  invalidAll(): $errs\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: catching for arrow validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureValidCatching() {
    println("=== Feature: catching (exception -> validation error) ===\n")

    val success: Either<NonEmptyList<RegError>, String> = Kap { delay(10); "valid-data" }
            .catching { e -> RegError.NameTooShort("caught: ${e.message}") }
            .executeGraph()
    println("  catching success: $success")

    val failure: Either<NonEmptyList<RegError>, String> = Kap<String> { throw IllegalArgumentException("bad input") }
            .catching { e -> RegError.InvalidEmail("caught: ${e.message}") }
            .executeGraph()
    println("  catching failure: $failure\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: ensureV and ensureVAll guards (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureEnsureV() {
    println("=== Feature: ensureV & ensureVAll ===\n")

    // ensureV — single error on predicate failure
    val pass: Either<NonEmptyList<RegError>, Int> = Kap { delay(10); 25 }
            .ensureV(
                error = { age -> RegError.AgeTooLow("Age $age too low") },
                predicate = { it >= 18 },
            )
            .executeGraph()
    println("  ensureV pass: $pass")

    val fail: Either<NonEmptyList<RegError>, Int> = Kap { delay(10); 12 }
            .ensureV(
                error = { age -> RegError.AgeTooLow("Age $age too low") },
                predicate = { it >= 18 },
            )
            .executeGraph()
    println("  ensureV fail: $fail")

    // ensureVAll — multiple errors on predicate failure
    val failAll: Either<NonEmptyList<RegError>, String> = Kap { delay(10); "X" }
            .ensureVAll(
                errors = { name ->
                    nonEmptyListOf(
                        RegError.NameTooShort("'$name' too short"),
                        RegError.UsernameTaken("'$name' reserved"),
                    )
                },
                predicate = { it.length >= 3 },
            )
            .executeGraph()
    println("  ensureVAll fail: $failAll\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: mapV transform (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMapV() {
    println("=== Feature: mapV ===\n")

    val result: Either<NonEmptyList<RegError>, String> = Kap { delay(10); Either.Right(ValidName("Alice")) as Either<NonEmptyList<RegError>, ValidName> }
            .mapV { name -> "Hello, ${name.value}!" }
            .executeGraph()
    println("  mapV: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: mapError transform (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMapError() {
    println("=== Feature: mapError ===\n")

    val original: Either<NonEmptyList<RegError>, ValidName> = invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
            .executeGraph()
    println("  original errors: $original")

    val mapped: Either<NonEmptyList<String>, ValidName> = invalid<RegError, ValidName>(RegError.NameTooShort("too short"))
            .mapError { regError -> "mapped: ${regError.message}" }
            .executeGraph()
    println("  mapError:        $mapped\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: recoverV recovery (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRecoverV() {
    println("=== Feature: recoverV ===\n")

    val result: Either<NonEmptyList<RegError>, String> = Kap<Either<NonEmptyList<RegError>, String>> {
            throw RuntimeException("network timeout")
        }.recoverV { e ->
            RegError.InvalidEmail("recovered: ${e.message}")
        }.executeGraph()
    println("  recoverV: $result\n")
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: orThrow unwrap (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureOrThrow() {
    println("=== Feature: orThrow ===\n")

    // Success — unwraps cleanly
    val name: ValidName = valid<RegError, ValidName>(ValidName("Alice")).orThrow()
            .executeGraph()
    println("  orThrow success: $name")

    // Failure — throws ValidationException
    val caught = try {
        invalid<RegError, ValidName>(RegError.NameTooShort("too short")).orThrow()
                .executeGraph()
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

    val result: Either<NonEmptyList<RegError>, List<ValidName>> = names.traverseV { name ->
            Kap { validateName(name) }
        }.executeGraph()
    when (result) {
        is Either.Right -> println("  traverseV all valid: ${result.value}")
        is Either.Left -> println("  traverseV errors: ${result.value.map { it.message }}")
    }

    val allValid: Either<NonEmptyList<RegError>, List<ValidName>> = listOf("Alice", "Bob", "Charlie").traverseV { name ->
            Kap { validateName(name) }
        }.executeGraph()
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

    val result: Either<NonEmptyList<RegError>, List<ValidName>> = validations.sequenceV()
            .executeGraph()
    when (result) {
        is Either.Right -> println("  sequenceV all valid: ${result.value}")
        is Either.Left -> println("  sequenceV errors: ${result.value.map { it.message }}")
    }

    // All valid
    val allOk: Either<NonEmptyList<RegError>, List<ValidName>> = listOf(
            Kap { validateName("Alice") },
            Kap { validateName("Bob") },
        ).sequenceV()
            .executeGraph()
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

    val result: Either<String, Int> = raceEither(
            fa = Kap { fetchCachedName() },
            fb = Kap { fetchFreshAge() },
        ).executeGraph()
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
