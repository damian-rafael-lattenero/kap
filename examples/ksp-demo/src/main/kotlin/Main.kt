import kap.*
import kotlinx.coroutines.delay

// ── Class: no prefix needed (unique param names) ───────────────

@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

suspend fun fetchFirstName(): String { delay(30); return "Alice" }
suspend fun fetchLastName(): String { delay(20); return "Smith" }
suspend fun fetchAge(): Int { delay(10); return 30 }

// ── Two functions with SAME param names — prefix avoids collision ──

data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)
data class Report(val userName: String, val dateRange: String, val format: String)

// Without prefix these would COLLIDE: both have "userName: String"
// → String.toUserName() would be generated twice = compile error

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

// ── Main ───────────────────────────────────────────────────────

suspend fun main() {
    println("=== KSP Type-Safe Demo ===\n")

    // Class: no prefix, short extensions
    val safeUser = kapSafe(::User)
            .with { fetchFirstName().toFirstName() }
            .with { fetchLastName().toLastName() }
            .with { fetchAge().toAge() }
            .executeGraph()
    println("  User (no prefix): $safeUser")

    // Dashboard: prefix = "Dashboard" → .toDashboardUserName()
    val safeDash = kapSafeBuildDashboard(::buildDashboard)
            .with { fetchUserName().toDashboardUserName() }
            .with { fetchCartSummary().toDashboardCartSummary() }
            .with { fetchPromoCode().toDashboardPromoCode() }
            .executeGraph()
    println("  Dashboard:        $safeDash")

    // Report: prefix = "Report" → .toReportUserName()
    // Same "userName" param — NO collision with Dashboard!
    val safeReport = kapSafeBuildReport(::buildReport)
            .with { fetchUserName().toReportUserName() }
            .with { fetchDateRange().toReportDateRange() }
            .with { fetchFormat().toReportFormat() }
            .executeGraph()
    println("  Report:           $safeReport")

    println("\n  Both Dashboard and Report have 'userName: String'")
    println("  but .toDashboardUserName() and .toReportUserName() don't collide!")
}
