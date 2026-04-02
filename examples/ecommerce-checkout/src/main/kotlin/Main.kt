import kap.*
import kotlinx.coroutines.delay

/**
 * E-commerce checkout flow — 11 service calls, 5 phases.
 *
 * Demonstrates: kap+with+then, parallel fan-out, sequential barriers,
 * and how the code structure mirrors the execution plan.
 *
 * Every service call returns a distinct domain type so the compiler enforces
 * parameter ordering — swap any two .with lines and it won't compile.
 */

// ── Domain types ────────────────────────────────────────────────────────────
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

// ── Simulated service calls with realistic latencies ────────────────────────
suspend fun fetchUser(): UserProfile { delay(120); return UserProfile("Alice", 42) }
suspend fun fetchCart(): ShoppingCart { delay(80); return ShoppingCart(3, 147.50) }
suspend fun fetchPromos(): PromotionBundle { delay(60); return PromotionBundle("SUMMER20", 20) }
suspend fun fetchInventory(): InventorySnapshot { delay(100); return InventorySnapshot(allInStock = true) }
suspend fun validateStock(): StockConfirmation { delay(50); return StockConfirmation(confirmed = true) }
suspend fun calcShipping(): ShippingQuote { delay(70); return ShippingQuote(5.99, "standard") }
suspend fun calcTax(): TaxBreakdown { delay(40); return TaxBreakdown(12.38, 0.08) }
suspend fun calcDiscounts(): DiscountSummary { delay(30); return DiscountSummary(29.50, "SUMMER20") }
suspend fun reservePayment(): PaymentAuth { delay(90); return PaymentAuth("4242", authorized = true) }
suspend fun generateConfirmation(): OrderConfirmation { delay(60); return OrderConfirmation("order-#90142") }
suspend fun sendEmail(): EmailReceipt { delay(40); return EmailReceipt("alice@example.com", "order-#90142") }

suspend fun main() {
    println("=== E-Commerce Checkout (kap+with+then) ===\n")

    val start = System.currentTimeMillis()

    // Type safety: swap any two .with lines and the compiler rejects it.
    // Each slot expects a specific type — UserProfile, ShoppingCart, etc.
    val result = kap(::CheckoutResult)
            // Phase 1: Fetch everything we need (parallel)
            .with { fetchUser() }
            .with { fetchCart() }
            .with { fetchPromos() }
            .with { fetchInventory() }
            // Phase 2: Validate stock (sequential — must wait for phase 1)
            .then { validateStock() }
            // Phase 3: Calculate costs (parallel)
            .with { calcShipping() }
            .with { calcTax() }
            .with { calcDiscounts() }
            // Phase 4: Reserve payment (sequential)
            .then { reservePayment() }
            // Phase 5: Confirmation + email (parallel)
            .with { generateConfirmation() }
            .with { sendEmail() }
            .executeGraph()

    val elapsed = System.currentTimeMillis() - start
    println("Result: $result")
    println("Total time: ${elapsed}ms (phases run in parallel, then enforces ordering)")
}
