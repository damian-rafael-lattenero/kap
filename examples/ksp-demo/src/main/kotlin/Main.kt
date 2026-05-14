@file:KapBridge(ThirdPartyDto::class)

import kap.*
import kotlinx.coroutines.delay

// ── Class: kap(::User) — no companion object needed ───────────

@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

suspend fun fetchFirstName(): String { delay(30); return "Alice" }
suspend fun fetchLastName(): String { delay(20); return "Smith" }
suspend fun fetchAge(): Int { delay(10); return 30 }

// ── Two functions with SAME param names — prefix avoids collision ──

data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)
data class Report(val userName: String, val dateRange: String, val format: String)

@KapTypeSafe(prefix = "Dashboard")
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

@KapTypeSafe(prefix = "Report")
fun buildReport(userName: String, dateRange: String, format: String): Report =
    Report(userName, dateRange, format)

suspend fun fetchUserName(): String { delay(30); return "Alice" }
suspend fun fetchCartSummary(): String { delay(20); return "3 items, $147.50" }
suspend fun fetchPromoCode(): String { delay(10); return "SAVE20" }
suspend fun fetchDateRange(): String { delay(15); return "2026-01-01..2026-03-27" }
suspend fun fetchFormat(): String { delay(5); return "PDF" }

// ── Third-party class via @KapBridge ───────────────────────────

data class ThirdPartyDto(val id: Int, val name: String, val active: Boolean)

// ── Phase barrier demo ─────────────────────────────────────────

@KapTypeSafe
data class Checkout(
    val user: String,
    val cart: String,
    val validated: Boolean,
    val total: Double,
)

suspend fun fetchUser(): String { delay(30); return "Alice" }
suspend fun fetchCart(): String { delay(20); return "3 items" }
suspend fun validateOrder(): Boolean { delay(10); return true }
suspend fun calculateTotal(): Double { delay(15); return 147.50 }

// ── Main ───────────────────────────────────────────────────────

suspend fun main() {
    println("=== KSP Scoped Builder Demo ===\n")

    // Class: kap(::User)
    val userResult = kap(::User)
        .with { firstName from fetchFirstName() }
        .with { lastName from fetchLastName() }
        .with { age from fetchAge() }
        .evalGraph()
    println("  User: $userResult")

    // Function: kap(::buildDashboard). The `prefix=Dashboard` affects the
    // generated file/tag class names internally, but call-site tag names are
    // always the original param names (`userName`, `cartSummary`, ...).
    val dash = kap(::buildDashboard)
        .with { userName from fetchUserName() }
        .with { cartSummary from fetchCartSummary() }
        .with { promoCode from fetchPromoCode() }
        .evalGraph()
    println("  Dashboard: $dash")

    // Function: kap(::buildReport). Same `userName` param name as buildDashboard
    // but no collision — each wrapper has its own slot interface.
    val report = kap(::buildReport)
        .with { userName from fetchUserName() }
        .with { dateRange from fetchDateRange() }
        .with { format from fetchFormat() }
        .evalGraph()
    println("  Report: $report")

    // Third-party class via @KapBridge: kap(::ThirdPartyDto)
    val dto = kap(::ThirdPartyDto)
        .with { id from 42 }
        .with { name from "bridged" }
        .with { active from true }
        .evalGraph()
    println("  ThirdPartyDto: $dto")

    // Phase barriers — `.then` waits for everything above
    val checkout = kap(::Checkout)
        .with { user from fetchUser() }
        .with { cart from fetchCart() }
        .then { validated from validateOrder() }
        .with { total from calculateTotal() }
        .evalGraph()
    println("  Checkout: $checkout")

    println("\nAll demos passed!")
}
