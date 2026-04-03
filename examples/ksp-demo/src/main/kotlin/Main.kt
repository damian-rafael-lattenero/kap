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
    println("=== KSP Named Builder Demo ===\n")

    // Class: kap(::User)
    val user = kap(::User)
        .withFirstName { fetchFirstName() }
        .withLastName { fetchLastName() }
        .withAge { fetchAge() }
        .executeGraph()
    println("  User: $user")

    // Function with prefix: kap(BuildDashboard)
    val dash = kap(BuildDashboard)
        .withDashboardUserName { fetchUserName() }
        .withDashboardCartSummary { fetchCartSummary() }
        .withDashboardPromoCode { fetchPromoCode() }
        .executeGraph()
    println("  Dashboard: $dash")

    // Function with prefix: kap(BuildReport)
    val report = kap(BuildReport)
        .withReportUserName { fetchUserName() }
        .withReportDateRange { fetchDateRange() }
        .withReportFormat { fetchFormat() }
        .executeGraph()
    println("  Report: $report")

    // Third-party class via @KapBridge: kap(::ThirdPartyDto)
    val dto = kap(::ThirdPartyDto)
        .withId { 42 }
        .withName { "bridged" }
        .withActive { true }
        .executeGraph()
    println("  ThirdPartyDto: $dto")

    // Phase barriers
    val checkout = kap(::Checkout)
        .withUser { fetchUser() }
        .withCart { fetchCart() }
        .thenValidated { validateOrder() }
        .withTotal { calculateTotal() }
        .executeGraph()
    println("  Checkout: $checkout")

    println("\nAll demos passed!")
}
