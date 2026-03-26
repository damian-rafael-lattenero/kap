import applicative.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Full-stack order placement — kap-core + kap-resilience + kap-arrow.
 *
 * Combines all three modules in a realistic order-processing pipeline:
 *
 *   kap-core:       kap/with/then for type-safe parallel orchestration
 *   kap-resilience: Schedule retry, CircuitBreaker, bracket, timeoutRace
 *   kap-arrow:      validated/accumulate for input validation, attempt(),
 *                   raceEither for heterogeneous racing, Either bridges
 *
 * Scenario: Place an order by (1) validating input with error accumulation,
 * (2) fetching inventory + pricing with resilience, (3) processing payment
 * with circuit breaker, and (4) confirming with bracket-safe DB write.
 */

// ── Domain types ────────────────────────────────────────────────────────

data class ValidItemId(val value: String)
data class ValidQuantity(val value: Int)
data class ValidAddress(val street: String, val city: String, val zip: String)

sealed class OrderError(val message: String) {
    class InvalidItem(message: String) : OrderError(message)
    class InvalidQuantity(message: String) : OrderError(message)
    class InvalidAddress(message: String) : OrderError(message)
}

data class ValidatedOrder(val item: ValidItemId, val qty: ValidQuantity, val address: ValidAddress)

data class InventoryCheck(val available: Boolean, val warehouse: String)
data class PricingInfo(val unitPrice: Double, val discount: Double)
data class FetchedData(val inventory: InventoryCheck, val pricing: PricingInfo)
data class PaymentReceipt(val txId: String, val amount: Double)
data class OrderConfirmation(val orderId: String, val estimatedDelivery: String)

data class PlacedOrder(
    val order: ValidatedOrder,
    val inventory: InventoryCheck,
    val pricing: PricingInfo,
    val payment: PaymentReceipt,
    val confirmation: OrderConfirmation,
)

// ── Validation functions (kap-arrow: validated/accumulate) ──────────────

suspend fun validateItem(id: String): Either<NonEmptyList<OrderError>, ValidItemId> {
    delay(30)
    return if (id.startsWith("ITEM-") && id.length > 5)
        Either.Right(ValidItemId(id))
    else
        Either.Left(nonEmptyListOf(OrderError.InvalidItem("Item ID must start with 'ITEM-' and be > 5 chars, got: $id")))
}

suspend fun validateQuantity(qty: Int): Either<NonEmptyList<OrderError>, ValidQuantity> {
    delay(20)
    return if (qty in 1..100)
        Either.Right(ValidQuantity(qty))
    else
        Either.Left(nonEmptyListOf(OrderError.InvalidQuantity("Quantity must be 1-100, got: $qty")))
}

suspend fun validateAddress(
    street: String, city: String, zip: String,
): Either<NonEmptyList<OrderError>, ValidAddress> {
    delay(40)
    val errors = mutableListOf<OrderError>()
    if (street.isBlank()) errors.add(OrderError.InvalidAddress("Street cannot be blank"))
    if (city.isBlank()) errors.add(OrderError.InvalidAddress("City cannot be blank"))
    if (!zip.matches(Regex("\\d{5}"))) errors.add(OrderError.InvalidAddress("ZIP must be 5 digits, got: $zip"))
    return if (errors.isEmpty()) Either.Right(ValidAddress(street, city, zip))
    else Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
}

// ── Service calls (simulated with realistic latencies) ──────────────────

var inventoryAttempts = 0

suspend fun checkInventory(itemId: String): InventoryCheck {
    inventoryAttempts++
    if (inventoryAttempts <= 2) {
        delay(30)
        throw RuntimeException("Inventory service flake (attempt $inventoryAttempts)")
    }
    delay(60)
    return InventoryCheck(true, "warehouse-east")
}

suspend fun fetchLivePricing(itemId: String): PricingInfo {
    delay(200)
    return PricingInfo(49.99, 0.10)
}

suspend fun fetchCachedPricing(itemId: String): PricingInfo {
    delay(30)
    return PricingInfo(49.99, 0.05)
}

var paymentAttempts = 0

suspend fun processPayment(amount: Double): PaymentReceipt {
    paymentAttempts++
    if (paymentAttempts <= 1) {
        delay(20)
        throw RuntimeException("Payment gateway timeout")
    }
    delay(70)
    return PaymentReceipt("TX-${System.currentTimeMillis()}", amount)
}

class OrderDb(val name: String) {
    var closed = false
    suspend fun insert(orderId: String): OrderConfirmation {
        delay(50)
        return OrderConfirmation(orderId, "3-5 business days")
    }
    fun close() { closed = true }
}

suspend fun openOrderDb(): OrderDb {
    delay(20)
    return OrderDb("orders-primary")
}

// ═══════════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════════

suspend fun main() {
    val start = System.currentTimeMillis()
    fun elapsed() = "${System.currentTimeMillis() - start}ms"

    // ─── Phase 1: Validate input (kap-arrow: accumulate) ────────────
    println("=== Phase 1: Input validation with error accumulation ===\n")

    // Scenario A: all valid
    println("  Scenario A: valid input")
    val validResult = Async {
        kapV<OrderError, ValidItemId, ValidQuantity, ValidAddress, ValidatedOrder>(::ValidatedOrder)
            .withV { validateItem("ITEM-12345") }
            .withV { validateQuantity(3) }
            .withV { validateAddress("123 Main St", "Springfield", "62701") }
    }

    when (validResult) {
        is Either.Right -> println("    OK: ${validResult.value}")
        is Either.Left -> println("    Errors: ${validResult.value.joinToString { it.message }}")
    }

    // Scenario B: multiple failures accumulated in parallel
    println("  Scenario B: invalid input (errors accumulated)")
    val invalidResult = Async {
        kapV<OrderError, ValidItemId, ValidQuantity, ValidAddress, ValidatedOrder>(::ValidatedOrder)
            .withV { validateItem("bad") }
            .withV { validateQuantity(0) }
            .withV { validateAddress("", "", "abc") }
    }

    when (invalidResult) {
        is Either.Right -> println("    OK: ${invalidResult.value}")
        is Either.Left -> {
            println("    ${invalidResult.value.size} errors found:")
            invalidResult.value.forEach { println("      - ${it.message}") }
        }
    }
    println("  (${elapsed()})\n")

    // ─── Phase 2: Resilient data fetching (kap-resilience + kap-core) ─
    println("=== Phase 2: Resilient data fetching ===\n")

    val order = (validResult as Either.Right).value

    val retryPolicy = Schedule.times<Throwable>(4) and
        Schedule.exponential<Throwable>(30.milliseconds).jittered()

    val fetched = Async {
        kap(::FetchedData)
            // Retry flaky inventory API with exponential backoff
            .with(
                Effect { checkInventory(order.item.value) }
                    .retry(retryPolicy) { attempt, err, nextDelay ->
                        println("  Inventory retry #$attempt: ${err.message} (waiting $nextDelay)")
                    }
            )
            // timeoutRace: try live pricing, fall back to cache if slow
            .with(
                Effect { fetchLivePricing(order.item.value) }
                    .timeoutRace(100.milliseconds, Effect { fetchCachedPricing(order.item.value) })
            )
    }

    println("  Inventory: ${fetched.inventory.warehouse} (available=${fetched.inventory.available})")
    println("  Pricing: $${fetched.pricing.unitPrice} (discount=${(fetched.pricing.discount * 100).toInt()}%)")
    println("  (${elapsed()})\n")

    // ─── Phase 3: Payment with circuit breaker (kap-resilience) ─────
    println("=== Phase 3: Payment processing with CircuitBreaker ===\n")

    val breaker = CircuitBreaker(
        maxFailures = 3,
        resetTimeout = 5.seconds,
        onStateChange = { old, new -> println("  Circuit: $old -> $new") },
    )

    val totalAmount = order.qty.value * fetched.pricing.unitPrice * (1 - fetched.pricing.discount)

    val payment = Async {
        Effect { processPayment(totalAmount) }
            .withCircuitBreaker(breaker)
            .retry(Schedule.times<Throwable>(2) and Schedule.spaced(50.milliseconds))
    }

    println("  Payment: txId=${payment.txId}, amount=$${String.format("%.2f", payment.amount)}")
    println("  (${elapsed()})\n")

    // ─── Phase 4: Confirmation with bracket (kap-resilience) ────────
    println("=== Phase 4: Order confirmation with bracket-safe DB ===\n")

    val confirmation = Async {
        bracket(
            acquire = {
                openOrderDb().also { println("  Acquired DB: ${it.name}") }
            },
            use = { db ->
                Effect { db.insert("ORD-${System.currentTimeMillis()}") }
            },
            release = { db ->
                db.close()
                println("  Released DB: ${db.name} (closed=${db.closed})")
            },
        )
    }

    println("  Confirmation: ${confirmation.orderId}")
    println("  Delivery: ${confirmation.estimatedDelivery}")
    println("  (${elapsed()})\n")

    // ─── Phase 5: attempt() + raceEither (kap-arrow) ────────────────
    println("=== Phase 5: attempt() and raceEither from kap-arrow ===\n")

    val attemptResult: Either<Throwable, String> = Async {
        Effect { "Order ${confirmation.orderId} processed successfully" }.attempt()
    }
    println("  attempt() result: $attemptResult")

    val raceResult: Either<String, Int> = Async {
        raceEither(
            fa = Effect { delay(50); "fast-notification-sent" },
            fb = Effect { delay(200); 42 },
        )
    }
    println("  raceEither winner: $raceResult")
    println("  (${elapsed()})\n")

    // ─── Full composed pipeline: all three modules in one graph ─────
    println("=== Full pipeline: validate -> fetch -> pay -> confirm ===\n")

    inventoryAttempts = 0
    paymentAttempts = 0
    val pipeStart = System.currentTimeMillis()

    // Step 1: validate input (kap-arrow)
    val validated = Async {
        kapV<OrderError, ValidItemId, ValidQuantity, ValidAddress, ValidatedOrder>(::ValidatedOrder)
            .withV { validateItem("ITEM-99999") }
            .withV { validateQuantity(2) }
            .withV { validateAddress("456 Oak Ave", "Shelbyville", "62702") }
            .orThrow()
    }

    // Step 2: orchestrate with kap+with+then (all three modules)
    val fullOrder = Async {
        kap(::PlacedOrder)
            .with { validated }
            // Phase: inventory + pricing in parallel (kap-resilience)
            .with(
                Effect { checkInventory(validated.item.value) }
                    .retry(Schedule.times<Throwable>(4) and Schedule.exponential(20.milliseconds))
            )
            .with(
                Effect { fetchLivePricing(validated.item.value) }
                    .timeoutRace(80.milliseconds, Effect { fetchCachedPricing(validated.item.value) })
            )
            // Phase: payment (sequential, depends on pricing)
            .then(
                Effect { processPayment(validated.qty.value * 49.99 * 0.95) }
                    .withCircuitBreaker(breaker)
                    .retry(Schedule.times<Throwable>(2) and Schedule.spaced(30.milliseconds))
            )
            // Phase: confirmation (sequential, depends on payment)
            .then(
                bracket(
                    acquire = { openOrderDb() },
                    use = { db -> Effect { db.insert("ORD-FULL-${System.currentTimeMillis()}") } },
                    release = { db -> db.close() },
                )
            )
    }

    val pipeElapsed = System.currentTimeMillis() - pipeStart
    println("  Order:        ${fullOrder.order}")
    println("  Inventory:    ${fullOrder.inventory.warehouse}")
    println("  Pricing:      $${fullOrder.pricing.unitPrice}")
    println("  Payment:      ${fullOrder.payment.txId}")
    println("  Confirmation: ${fullOrder.confirmation.orderId}")
    println("  Pipeline time: ${pipeElapsed}ms")
    println("\nTotal example time: ${elapsed()}")
}
