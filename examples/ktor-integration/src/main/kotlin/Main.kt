/**
 * Comprehensive Ktor integration — demonstrates the FULL API surface of
 * kap-core, kap-resilience, and kap-arrow in a realistic e-commerce BFF.
 *
 * ── kap-core: Parallel orchestration ──────────────────────────────────────
 *   GET  /dashboard/{userId}         kap/with/then/andThen, named, traced, memoize
 *   GET  /products                   traverse, traverse(concurrency), traverseSettled
 *   GET  /search?q=…                 computation{} DSL with bind, ensure, ensureNotNull
 *   GET  /compare?ids=…              zip, combine, race, raceAgainst, settled
 *
 * ── kap-resilience: Fault tolerance ───────────────────────────────────────
 *   GET  /pricing/{itemId}           raceQuorum, timeoutRace, firstSuccessOf
 *   GET  /users/{userId}             CircuitBreaker, Schedule retry, retryOrElse
 *   GET  /health                     Resource.zip, bracket, guarantee, guaranteeCase
 *
 * ── kap-arrow: Typed error handling ───────────────────────────────────────
 *   POST /register                   validated{} DSL, kapV/withV, andThenV, traverseV, mapV, mapError
 *   POST /validate-batch             traverseV(concurrency), sequenceV, ensureV
 *
 * ── Combined: All three modules ───────────────────────────────────────────
 *   POST /orders                     full pipeline: validate → resilient fetch → bracket-safe write
 */

import kap.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ═══════════════════════════════════════════════════════════════════════════
//  Domain types
// ═══════════════════════════════════════════════════════════════════════════

@Serializable data class UserProfile(val id: String, val name: String, val tier: String)
@Serializable data class Cart(val items: Int, val total: Double)
@Serializable data class RecentOrder(val orderId: String, val amount: Double)
@Serializable data class Recommendation(val productId: String, val reason: String)
@Serializable data class Dashboard(
    val user: UserProfile,
    val cart: Cart,
    val recentOrders: List<RecentOrder>,
    val authToken: String,
    val recommendations: List<Recommendation>,
    val notificationCount: Int,
)

@Serializable data class Product(val id: String, val name: String, val price: Double, val available: Boolean)
@Serializable data class ProductList(val products: List<Product>, val failures: List<String>)
@Serializable data class SearchResult(val query: String, val results: List<Product>, val totalCount: Int)

@Serializable data class PricingData(val price: Double, val currency: String, val source: String)
@Serializable data class PricingResponse(val itemId: String, val pricing: PricingData, val strategy: String)

@Serializable data class UserResponse(val user: UserProfile, val retryAttempts: Int, val totalDelay: String)

@Serializable data class HealthCheck(val service: String, val status: String, val latencyMs: Long)
@Serializable data class HealthResponse(val status: String, val checks: List<HealthCheck>, val exitCase: String)

@Serializable data class RegistrationRequest(val name: String, val email: String, val age: Int, val interests: List<String>)
@Serializable data class RegistrationResult(val id: String, val name: String, val email: String, val age: Int, val interests: List<String>)

@Serializable data class BatchValidationRequest(val emails: List<String>)
@Serializable data class ValidatedEmail(val original: String, val normalized: String)
@Serializable data class BatchValidationResponse(val valid: List<ValidatedEmail>?, val errors: List<String>?)

@Serializable data class CompareResponse(val products: List<Product?>, val winner: Product?, val settled: List<String>)

sealed class RegistrationError(val message: String) {
    class InvalidName(message: String) : RegistrationError(message)
    class InvalidEmail(message: String) : RegistrationError(message)
    class InvalidAge(message: String) : RegistrationError(message)
    class InvalidInterests(message: String) : RegistrationError(message)
    class DuplicateEmail(message: String) : RegistrationError(message)
}

@Serializable data class ErrorResponse(val errors: List<String>)

@Serializable data class OrderRequest(val itemId: String, val quantity: Int, val street: String, val city: String, val zip: String)
@Serializable data class InventoryCheck(val available: Boolean, val warehouse: String)
@Serializable data class PricingInfo(val unitPrice: Double, val discount: Double)
@Serializable data class PaymentReceipt(val txId: String, val amount: Double)
@Serializable data class OrderConfirmation(val orderId: String, val estimatedDelivery: String)
@Serializable data class PlacedOrder(
    val itemId: String, val quantity: Int,
    val inventory: InventoryCheck, val pricing: PricingInfo,
    val payment: PaymentReceipt, val confirmation: OrderConfirmation,
)

sealed class OrderError(val message: String) {
    class InvalidItem(message: String) : OrderError(message)
    class InvalidQuantity(message: String) : OrderError(message)
    class InvalidAddress(message: String) : OrderError(message)
}

data class ValidatedItem(val value: String)
data class ValidatedQuantity(val value: Int)
data class ValidatedAddress(val street: String, val city: String, val zip: String)
data class ValidatedOrder(val item: ValidatedItem, val qty: ValidatedQuantity, val address: ValidatedAddress)

// ═══════════════════════════════════════════════════════════════════════════
//  Simulated services (replace with real HTTP clients in production)
// ═══════════════════════════════════════════════════════════════════════════

object Services {
    suspend fun fetchUserProfile(userId: String): UserProfile {
        delay(50); return UserProfile(userId, "Alice", "Gold")
    }

    suspend fun fetchCart(userId: String): Cart {
        delay(40); return Cart(items = 3, total = 149.99)
    }

    suspend fun fetchRecentOrders(userId: String): List<RecentOrder> {
        delay(60); return listOf(RecentOrder("ORD-001", 59.99), RecentOrder("ORD-002", 89.50))
    }

    suspend fun authorize(userId: String): String {
        delay(30); return "tok_${userId}_${System.currentTimeMillis()}"
    }

    suspend fun fetchRecommendations(tier: String): List<Recommendation> {
        delay(70); return listOf(
            Recommendation("PROD-A", "Based on your $tier tier"),
            Recommendation("PROD-B", "Frequently bought together"),
        )
    }

    suspend fun countNotifications(userId: String): Int {
        delay(25); return 7
    }

    suspend fun fetchProduct(id: String): Product {
        delay(30 + (id.hashCode() % 20).toLong().coerceAtLeast(0))
        if (id == "FAIL") throw RuntimeException("Product $id not found in catalog")
        return Product(id, "Product $id", 29.99 + id.hashCode() % 50, id != "OUT")
    }

    suspend fun searchProducts(query: String): List<Product> {
        delay(80)
        return (1..5).map { Product("SEARCH-$it", "$query result $it", 19.99 + it * 10, true) }
    }

    var pricingReplicaAAttempts = 0
    suspend fun fetchPricingReplicaA(itemId: String): PricingData {
        pricingReplicaAAttempts++
        delay(80); return PricingData(99.99, "USD", "replica-A")
    }
    suspend fun fetchPricingReplicaB(itemId: String): PricingData {
        delay(120); return PricingData(99.99, "USD", "replica-B")
    }
    suspend fun fetchPricingReplicaC(itemId: String): PricingData {
        delay(60); return PricingData(99.99, "USD", "replica-C")
    }
    suspend fun fetchPricingLive(itemId: String): PricingData {
        delay(500); return PricingData(49.99, "USD", "live")
    }
    suspend fun fetchPricingCache(itemId: String): PricingData {
        delay(20); return PricingData(49.99, "USD", "cache")
    }
    suspend fun fetchPricingPrimary(itemId: String): PricingData {
        throw RuntimeException("Primary pricing service down")
    }
    suspend fun fetchPricingSecondary(itemId: String): PricingData {
        throw RuntimeException("Secondary pricing service down")
    }
    suspend fun fetchPricingFallback(itemId: String): PricingData {
        delay(10); return PricingData(39.99, "USD", "static-fallback")
    }

    var userApiAttempts = 0
    suspend fun fetchUserFlaky(userId: String): UserProfile {
        userApiAttempts++
        if (userApiAttempts <= 2) {
            delay(10); throw RuntimeException("User API timeout (attempt $userApiAttempts)")
        }
        delay(40); return UserProfile(userId, "Alice", "premium")
    }

    fun resetCounters() {
        pricingReplicaAAttempts = 0
        userApiAttempts = 0
        inventoryAttempts = 0
        paymentAttempts = 0
    }

    suspend fun checkDbConnection(): HealthCheck {
        delay(30); return HealthCheck("database", "ok", 30)
    }
    suspend fun checkCacheConnection(): HealthCheck {
        delay(15); return HealthCheck("cache", "ok", 15)
    }
    suspend fun checkExternalApi(): HealthCheck {
        delay(50); return HealthCheck("external-api", "ok", 50)
    }

    fun validateName(name: String): Either<NonEmptyList<RegistrationError>, String> =
        if (name.length >= 2) Either.Right(name)
        else Either.Left(nonEmptyListOf(RegistrationError.InvalidName("Name must be at least 2 characters, got '${name}'")))

    fun validateEmail(email: String): Either<NonEmptyList<RegistrationError>, String> {
        val errors = mutableListOf<RegistrationError>()
        if ("@" !in email) errors.add(RegistrationError.InvalidEmail("Email must contain '@'"))
        if (!email.endsWith(".com") && !email.endsWith(".io")) errors.add(RegistrationError.InvalidEmail("Email must end with .com or .io"))
        return if (errors.isEmpty()) Either.Right(email.lowercase())
        else Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
    }

    fun validateAge(age: Int): Either<NonEmptyList<RegistrationError>, Int> =
        if (age in 18..120) Either.Right(age)
        else Either.Left(nonEmptyListOf(RegistrationError.InvalidAge("Age must be 18-120, got $age")))

    suspend fun checkEmailExists(email: String): Boolean {
        delay(30); return email == "taken@example.com"
    }

    fun validateSingleInterest(interest: String): Either<NonEmptyList<RegistrationError>, String> =
        if (interest.length >= 2) Either.Right(interest.lowercase())
        else Either.Left(nonEmptyListOf(RegistrationError.InvalidInterests("Interest '$interest' must be at least 2 chars")))

    fun validateEmailFormat(email: String): Either<NonEmptyList<String>, ValidatedEmail> =
        if ("@" in email && (email.endsWith(".com") || email.endsWith(".io")))
            Either.Right(ValidatedEmail(email, email.lowercase().trim()))
        else Either.Left(nonEmptyListOf("Invalid email format: $email"))

    var inventoryAttempts = 0
    suspend fun checkInventory(itemId: String): InventoryCheck {
        inventoryAttempts++
        if (inventoryAttempts <= 2) { delay(10); throw RuntimeException("Inventory flake #$inventoryAttempts") }
        delay(50); return InventoryCheck(true, "warehouse-east")
    }

    suspend fun fetchLivePricing(itemId: String): PricingInfo {
        delay(300); return PricingInfo(49.99, 0.10)
    }
    suspend fun fetchCachedPricing(itemId: String): PricingInfo {
        delay(20); return PricingInfo(49.99, 0.05)
    }

    var paymentAttempts = 0
    suspend fun processPayment(amount: Double): PaymentReceipt {
        paymentAttempts++
        if (paymentAttempts <= 1) { delay(10); throw RuntimeException("Payment gateway timeout") }
        delay(50); return PaymentReceipt("TX-${System.currentTimeMillis()}", amount)
    }

    class OrderDb(val name: String) {
        var closed = false
        suspend fun insert(orderId: String): OrderConfirmation {
            delay(40); return OrderConfirmation(orderId, "3-5 business days")
        }
        fun close() { closed = true }
    }

    suspend fun openOrderDb(): OrderDb {
        delay(15); return OrderDb("orders-primary")
    }

    fun validateItem(id: String): Either<NonEmptyList<OrderError>, ValidatedItem> =
        if (id.startsWith("ITEM-") && id.length > 5) Either.Right(ValidatedItem(id))
        else Either.Left(nonEmptyListOf(OrderError.InvalidItem("Item ID must start with 'ITEM-' and be > 5 chars, got: $id")))

    fun validateQuantity(qty: Int): Either<NonEmptyList<OrderError>, ValidatedQuantity> =
        if (qty in 1..100) Either.Right(ValidatedQuantity(qty))
        else Either.Left(nonEmptyListOf(OrderError.InvalidQuantity("Quantity must be 1-100, got: $qty")))

    fun validateAddress(street: String, city: String, zip: String): Either<NonEmptyList<OrderError>, ValidatedAddress> {
        val errors = mutableListOf<OrderError>()
        if (street.isBlank()) errors.add(OrderError.InvalidAddress("Street cannot be blank"))
        if (city.isBlank()) errors.add(OrderError.InvalidAddress("City cannot be blank"))
        if (!zip.matches(Regex("\\d{5}"))) errors.add(OrderError.InvalidAddress("ZIP must be 5 digits, got: $zip"))
        return if (errors.isEmpty()) Either.Right(ValidatedAddress(street, city, zip))
        else Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Shared resilience infrastructure
// ═══════════════════════════════════════════════════════════════════════════

val userCircuitBreaker = CircuitBreaker(
    maxFailures = 5,
    resetTimeout = 10.seconds,
    onStateChange = { old, new -> println("  [CircuitBreaker] User API: $old -> $new") },
)

val tracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started -> println("  [Trace] ${event.name} started")
        is TraceEvent.Succeeded -> println("  [Trace] ${event.name} succeeded in ${event.duration}")
        is TraceEvent.Failed -> println("  [Trace] ${event.name} failed in ${event.duration}: ${event.error.message}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Application module (extracted for testability)
// ═══════════════════════════════════════════════════════════════════════════

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.errors.toList().map { it.toString() })
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(listOf(cause.message ?: "Bad request")))
        }
    }

    routing {
        coreRoutes()
        resilienceRoutes()
        arrowRoutes()
        combinedRoutes()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  kap-core routes: Parallel orchestration
// ═══════════════════════════════════════════════════════════════════════════

fun Routing.coreRoutes() {

    // ── 1. Multi-phase parallel aggregation ─────────────────────────────
    //  Phase 1 (parallel): user + cart + recentOrders
    //  Phase 2 (barrier):  authorize (needs user context)
    //  Phase 3 (parallel): recommendations (needs user.tier) + notifications
    //
    //  APIs: kap, with, then, andThen, named, traced, on
    get("/dashboard/{userId}") {
        val userId = call.parameters["userId"]!!

        val dashboard = kap(::Dashboard)
                .with(
                    Kap { Services.fetchUserProfile(userId) }
                        .named("fetchProfile")
                        .traced("profile", tracer)
                )
                .with(
                    Kap { Services.fetchCart(userId) }
                        .named("fetchCart")
                        .traced("cart", tracer)
                )
                .with(
                    Kap { Services.fetchRecentOrders(userId) }
                        .on(Dispatchers.IO)
                        .traced("recentOrders", tracer)
                )
                .then(
                    Kap { Services.authorize(userId) }
                        .traced("authorize", tracer)
                )
                .with { Services.fetchRecommendations("Gold") }
                .with { Services.countNotifications(userId) }
                .executeGraph()

        call.respond(dashboard)
    }

    // ── 2. Collection traversal with concurrency ────────────────────────
    //
    //  APIs: traverse, traverse(concurrency), traverseSettled
    get("/products") {
        val ids = call.request.queryParameters["ids"]?.split(",") ?: listOf("P1", "P2", "P3", "P4", "P5")
        val concurrency = call.request.queryParameters["concurrency"]?.toIntOrNull()

        val products: List<Product>
        val failures: List<String>

        if (call.request.queryParameters["settled"] == "true") {
            val results = ids.traverseSettled { id -> Kap { Services.fetchProduct(id) } }
                .executeGraph()
            products = results.filter { it.isSuccess }.map { it.getOrThrow() }
            failures = results.filter { it.isFailure }.map { "${it.exceptionOrNull()?.message}" }
        } else if (concurrency != null) {
            products = ids.traverse(concurrency) { id -> Kap { Services.fetchProduct(id) } }
                .executeGraph()
            failures = emptyList()
        } else {
            products = ids.traverse { id -> Kap { Services.fetchProduct(id) } }
                .executeGraph()
            failures = emptyList()
        }

        call.respond(ProductList(products, failures))
    }

    // ── 3. computation{} DSL with bind, ensure, ensureNotNull ───────────
    get("/search") {
        val query = call.request.queryParameters["q"]
        val minPrice = call.request.queryParameters["minPrice"]?.toDoubleOrNull()

        val result = computation {
                val q = bind {
                    query ?: throw IllegalArgumentException("Query parameter 'q' is required")
                }

                Kap { q }
                    .ensure({ IllegalArgumentException("Query must be at least 2 characters") }) { it.length >= 2 }
                    .bind()

                val rawProducts = Kap { Services.searchProducts(q) }.bind()

                val filtered = if (minPrice != null) {
                    rawProducts.filter { it.price >= minPrice }
                } else rawProducts

                SearchResult(q, filtered, filtered.size)
            }.executeGraph()

        call.respond(result)
    }

    // ── 4. zip, race, raceAgainst, settled, memoize ────────────────────
    //
    //  APIs: computation{} DSL with bind(), traverseSettled, race, memoizeOnSuccess, kap/with
    get("/compare") {
        val ids = call.request.queryParameters["ids"]?.split(",")
            ?: throw IllegalArgumentException("'ids' parameter required (comma-separated)")

        val memoizedExpensive = Kap { Services.fetchProduct("EXPENSIVE") }.memoizeOnSuccess()

        val result = computation {
                val settledResults = ids.traverseSettled { id ->
                    Kap { Services.fetchProduct(id) }
                }.bind()

                val products = settledResults.map { r -> r.getOrNull() }
                val statusList = settledResults.map { r ->
                    r.fold(onSuccess = { "ok: ${it.id}" }, onFailure = { "error: ${it.message}" })
                }

                val cheapest = if (ids.size >= 2) {
                    race(
                        Kap { Services.fetchProduct(ids[0]) },
                        Kap { Services.fetchProduct(ids[1]) },
                    ).bind()
                } else null

                kap { a: Product, b: Product ->
                    if (a.price < b.price) a else b
                }.with(memoizedExpensive).with(memoizedExpensive).bind()

                CompareResponse(products, cheapest, statusList)
            }.executeGraph()

        call.respond(result)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  kap-resilience routes: Fault tolerance
// ═══════════════════════════════════════════════════════════════════════════

fun Routing.resilienceRoutes() {

    // ── 5. raceQuorum + timeoutRace + firstSuccessOf ───────────────────
    //
    //  APIs: raceQuorum, timeoutRace, firstSuccessOf, orElse, fallback
    get("/pricing/{itemId}") {
        val itemId = call.parameters["itemId"]!!
        val strategy = call.request.queryParameters["strategy"] ?: "quorum"

        val pricing: PricingData
        val strategyUsed: String

        when (strategy) {
            "quorum" -> {
                val quotes = raceQuorum(
                        required = 2,
                        Kap { Services.fetchPricingReplicaA(itemId) },
                        Kap { Services.fetchPricingReplicaB(itemId) },
                        Kap { Services.fetchPricingReplicaC(itemId) },
                    ).executeGraph()
                pricing = quotes.first()
                strategyUsed = "quorum-2-of-3 (sources: ${quotes.map { it.source }})"
            }
            "timeout-race" -> {
                pricing = Kap { Services.fetchPricingLive(itemId) }
                        .timeoutRace(100.milliseconds, Kap { Services.fetchPricingCache(itemId) })
                        .executeGraph()
                strategyUsed = "timeout-race (100ms deadline, source: ${pricing.source})"
            }
            "fallback-chain" -> {
                pricing = firstSuccessOf(
                        Kap { Services.fetchPricingPrimary(itemId) },
                        Kap { Services.fetchPricingSecondary(itemId) },
                        Kap { Services.fetchPricingFallback(itemId) },
                    ).executeGraph()
                strategyUsed = "fallback-chain (source: ${pricing.source})"
            }
            "orElse" -> {
                pricing = Kap { Services.fetchPricingPrimary(itemId) }
                        .orElse(Kap { Services.fetchPricingSecondary(itemId) })
                        .orElse(Kap { Services.fetchPricingFallback(itemId) })
                        .executeGraph()
                strategyUsed = "orElse-chain (source: ${pricing.source})"
            }
            else -> throw IllegalArgumentException("Unknown strategy: $strategy")
        }

        call.respond(PricingResponse(itemId, pricing, strategyUsed))
    }

    // ── 6. CircuitBreaker + Schedule retry + retryOrElse ────────────────
    //
    //  APIs: CircuitBreaker, withCircuitBreaker, Schedule.times, Schedule.exponential,
    //        jittered, and, retry(schedule), retryOrElse, retryWithResult, attempt
    get("/users/{userId}") {
        val userId = call.parameters["userId"]!!
        val mode = call.request.queryParameters["mode"] ?: "retry"
        Services.userApiAttempts = 0

        when (mode) {
            "retry" -> {
                val policy = Schedule.times<Throwable>(5) and
                    Schedule.exponential<Throwable>(30.milliseconds).jittered()

                val retryResult = Kap { Services.fetchUserFlaky(userId) }
                        .withCircuitBreaker(userCircuitBreaker)
                        .retryWithResult(policy)
                        .executeGraph()

                call.respond(UserResponse(retryResult.value, retryResult.attempts, retryResult.totalDelay.toString()))
            }
            "fallback" -> {
                val policy = Schedule.times<Throwable>(1) and
                    Schedule.spaced<Throwable>(10.milliseconds)

                val user = Kap { Services.fetchUserFlaky(userId) }
                        .withCircuitBreaker(userCircuitBreaker)
                        .retryOrElse(policy) {
                            UserProfile(userId, "Cached User", "Basic")
                        }
                        .executeGraph()

                call.respond(UserResponse(user, Services.userApiAttempts, "N/A (fallback)"))
            }
            "attempt" -> {
                val result: Either<Throwable, UserProfile> = Kap { Services.fetchUserFlaky(userId) }
                        .withCircuitBreaker(userCircuitBreaker)
                        .attempt()
                        .executeGraph()

                when (result) {
                    is Either.Right -> call.respond(UserResponse(result.value, Services.userApiAttempts, "0ms"))
                    is Either.Left -> call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(listOf("User service unavailable: ${result.value.message}"))
                    )
                }
            }
            else -> throw IllegalArgumentException("Unknown mode: $mode")
        }
    }

    // ── 7. Resource.zip + bracket + guarantee + guaranteeCase ───────────
    get("/health") {
        val dbResource = Resource(
            acquire = { Services.checkDbConnection() },
            release = { println("  [Resource] Released DB health check") },
        )
        val cacheResource = Resource(
            acquire = { Services.checkCacheConnection() },
            release = { println("  [Resource] Released Cache health check") },
        )

        var exitCaseStr = "unknown"

        val checks = mutableListOf<HealthCheck>()

        val resourceChecks = Resource.zip(dbResource, cacheResource) { db, cache -> listOf(db, cache) }
            .use { it }

        checks.addAll(resourceChecks)

        val apiCheck = bracket(
                acquire = { println("  [Bracket] Acquiring API probe"); "api-probe" },
                use = { _ ->
                    Kap { Services.checkExternalApi() }
                        .guaranteeCase { case ->
                            exitCaseStr = when (case) {
                                is ExitCase.Completed<*> -> "completed"
                                is ExitCase.Failed -> "failed: ${case.error.message}"
                                is ExitCase.Cancelled -> "cancelled"
                            }
                        }
                },
                release = { probe -> println("  [Bracket] Released $probe") },
            ).executeGraph()
        checks.add(apiCheck)

        val overallStatus = if (checks.all { it.status == "ok" }) "healthy" else "degraded"
        call.respond(HealthResponse(overallStatus, checks, exitCaseStr))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  kap-arrow routes: Typed error handling
// ═══════════════════════════════════════════════════════════════════════════

fun Routing.arrowRoutes() {

    // ── 8. Comprehensive validation ─────────────────────────────────────
    //  APIs: validated{} DSL, kapV/withV, andThenV, traverseV, mapV, mapError, orThrow
    post("/register") {
        val req = call.receive<RegistrationRequest>()

        val result = kapV<RegistrationError, String, String, Int, List<String>, RegistrationResult> { name, email, age, interests ->
                RegistrationResult("USR-${System.currentTimeMillis()}", name, email, age, interests)
            }
                .withV { Services.validateName(req.name) }
                .withV { Services.validateEmail(req.email) }
                .withV { Services.validateAge(req.age) }
                .withV(
                    req.interests.traverseV { interest ->
                        Kap<Either<NonEmptyList<RegistrationError>, String>> {
                            Services.validateSingleInterest(interest)
                        }
                    }
                )
                .andThenV { result ->
                    Kap {
                        val exists = Services.checkEmailExists(result.email)
                        if (exists) Either.Left(nonEmptyListOf(RegistrationError.DuplicateEmail("Email ${result.email} is already registered")))
                        else Either.Right(result)
                    }
                }
                .mapV { it }
                .mapError { err -> "${err::class.simpleName}: ${err.message}" }
                .orThrow()
                .executeGraph()

        call.respond(HttpStatusCode.Created, result)
    }

    // ── 9. Batch validation: traverseV, sequenceV, ensureV ──────────────
    post("/validate-batch") {
        val req = call.receive<BatchValidationRequest>()

        val result: Either<NonEmptyList<String>, List<ValidatedEmail>> = req.emails.traverseV(3) { email ->
                Kap { Services.validateEmailFormat(email) }
            }.executeGraph()

        when (result) {
            is Either.Right -> call.respond(BatchValidationResponse(result.value, null))
            is Either.Left -> call.respond(
                HttpStatusCode.BadRequest,
                BatchValidationResponse(null, result.value.toList())
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Combined routes: All three modules
// ═══════════════════════════════════════════════════════════════════════════

fun Routing.combinedRoutes() {

    // ── 10. Full order pipeline ─────────────────────────────────────────
    //  kap-arrow:       validate input (kapV/withV/orThrow)
    //  kap-resilience:  resilient fetch (Schedule, retry, timeoutRace, CircuitBreaker, bracket)
    //  kap-core:        parallel orchestration (kap, with, then)
    post("/orders") {
        val req = call.receive<OrderRequest>()
        Services.resetCounters()

        val retryPolicy = Schedule.times<Throwable>(4) and
            Schedule.exponential<Throwable>(20.milliseconds).jittered()

        val paymentBreaker = CircuitBreaker(
            maxFailures = 3,
            resetTimeout = 5.seconds,
        )

        val order = kapV<OrderError, ValidatedItem, ValidatedQuantity, ValidatedAddress, ValidatedOrder>(::ValidatedOrder)
                .withV { Services.validateItem(req.itemId) }
                .withV { Services.validateQuantity(req.quantity) }
                .withV { Services.validateAddress(req.street, req.city, req.zip) }
                .mapError { "${it::class.simpleName}: ${it.message}" }
                .orThrow()
                .andThen { validated ->
                    kap(::PlacedOrder)
                        .with { validated.item.value }
                        .with { validated.qty.value }
                        .with(
                            Kap { Services.checkInventory(validated.item.value) }
                                .retry(retryPolicy) { attempt, err, nextDelay ->
                                    println("  Inventory retry #$attempt: ${err.message} (waiting $nextDelay)")
                                }
                                .traced("inventory", tracer)
                        )
                        .with(
                            Kap { Services.fetchLivePricing(validated.item.value) }
                                .timeoutRace(100.milliseconds, Kap { Services.fetchCachedPricing(validated.item.value) })
                                .traced("pricing", tracer)
                        )
                        .then(
                            Kap { Services.processPayment(validated.qty.value * 49.99 * 0.95) }
                                .withCircuitBreaker(paymentBreaker)
                                .retry(Schedule.times<Throwable>(2) and Schedule.spaced(30.milliseconds))
                                .traced("payment", tracer)
                        )
                        .then(
                            bracket(
                                acquire = { Services.openOrderDb() },
                                use = { db -> Kap { db.insert("ORD-${System.currentTimeMillis()}") } },
                                release = { db -> db.close() },
                            ).traced("confirmation", tracer)
                        )
                }
                .executeGraph()

        call.respond(HttpStatusCode.Created, order)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Main entry point
// ═══════════════════════════════════════════════════════════════════════════

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}
