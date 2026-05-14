import kap.KapTypeSafe
import kotlinx.coroutines.delay

/**
 * E-commerce checkout flow — 11 service calls, 5 phases.
 *
 * Demonstrates: kap+with+then, parallel fan-out, sequential barriers,
 * and how the code structure mirrors the execution plan.
 *
 * 11 parameters: 8 are String, 3 are Boolean/Double — and the compiler
 * still catches every swap because each .withX is a unique named slot.
 * No wrapper types needed.
 */

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

// Plain data class — NOT @KapTypeSafe. The @KapTypeSafe is on `doSomething` below.
// (If we annotated both, the constructor and the function would generate two
//  `fun kap(f: (String,...) -> CheckoutResult2)` overloads with identical
//  signatures and the call site would be ambiguous.)
data class CheckoutResult2(
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

@KapTypeSafe
fun doSomething(
    user: String,
    cart: String,
    promos: String,
    inventory: Boolean,
    stock: Boolean,
    shipping: Double,
    tax: Double,
    discounts: Double,
    payment: String,
    confirmation: String,
    email: String,
) = CheckoutResult2(user, cart, promos, inventory, stock, shipping, tax, discounts, payment, confirmation, email)

// ── Simulated service calls with realistic latencies ────────────────────────
suspend fun fetchUser(): String { delay(120); return "Alice (id=42)" }
suspend fun fetchCart(): String { delay(80); return "3 items, $147.50" }
suspend fun fetchPromos(): String { delay(60); return "SUMMER20 (20% off)" }
suspend fun fetchInventory(): Boolean { delay(100); return true }
suspend fun validateStock(): Boolean { delay(50); return true }
suspend fun calcShipping(): Double { delay(70); return 5.99 }
suspend fun calcTax(): Double { delay(40); return 12.38 }
suspend fun calcDiscounts(): Double { delay(30); return 29.50 }
suspend fun reservePayment(): String { delay(90); return "card *4242 authorized" }
suspend fun generateConfirmation(): String { delay(60); return "order-#90142" }
suspend fun sendEmail(): String { delay(40); return "receipt sent to alice@example.com" }

suspend fun main() {
    println("=== E-Commerce Checkout (kap+with+then) ===\n")

    val start = System.currentTimeMillis()

    // Type safety: swap any two .with lines and the compiler rejects it.
    // 11 fields, 8 are String — named builders make each slot unique.
    val result = kap(::CheckoutResult)
            // Phase 1: Fetch everything we need (parallel)
            .with { user from fetchUser() }
            .with { cart from fetchCart() }
            .with { promos from fetchPromos() }
            .with { inventory from fetchInventory() }
            // Phase 2: Validate stock (sequential — must wait for phase 1)
            .then { stock from validateStock() }
            // Phase 3: Calculate costs (parallel)
            .with { shipping from calcShipping() }
            .with { tax from calcTax() }
            .with { discounts from calcDiscounts() }
            // Phase 4: Reserve payment (sequential)
            .then { payment from reservePayment() }
            // Phase 5: Confirmation + email (parallel)
            .with { confirmation from generateConfirmation() }
            .with { email from sendEmail() }
            .evalGraph()

    // Same flow, on a @KapTypeSafe *function*. `kap(::doSomething)` returns the
    // scoped builder `DoSomethingKap<curried>` — tag vals (`user`, `cart`, ...) are
    // members of the lambda's implicit receiver, so no `with(...)` or `import` is
    // needed at the call site.
    val result2 = kap(::doSomething)
        // Phase 1: Fetch everything we need (parallel)
        .with { user from fetchUser() }
        .with { cart from fetchCart() }
        .with { promos from fetchPromos() }
        .with { inventory from fetchInventory() }
        // Phase 2: Validate stock (sequential — must wait for phase 1)
        .then { stock from validateStock() }
        // Phase 3: Calculate costs (parallel)
        .with { shipping from calcShipping() }
        .with { tax from calcTax() }
        .with { discounts from calcDiscounts() }
        // Phase 4: Reserve payment (sequential)
        .then { payment from reservePayment() }
        // Phase 5: Confirmation + email (parallel)
        .with { confirmation from generateConfirmation() }
        .with { email from sendEmail() }
        .evalGraph()


    val elapsed = System.currentTimeMillis() - start
    println("Result : $result")
    println("Result2: $result2")
    println("Total time: ${elapsed}ms (phases run in parallel, then enforces ordering)")
}


