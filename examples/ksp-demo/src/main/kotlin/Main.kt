import kap.*
import kotlinx.coroutines.delay

// ── Class example ──────────────────────────────────────────────

@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

suspend fun fetchFirstName(): String { delay(30); return "Alice" }
suspend fun fetchLastName(): String { delay(20); return "Smith" }
suspend fun fetchAge(): Int { delay(10); return 30 }

// ── Function example ───────────────────────────────────────────

data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)

@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

suspend fun fetchUserName(): String { delay(30); return "Alice" }
suspend fun fetchCartSummary(): String { delay(20); return "3 items, $147.50" }
suspend fun fetchPromoCode(): String { delay(10); return "SAVE20" }

// ── Main ───────────────────────────────────────────────────────

suspend fun main() {
    println("=== KSP Type-Safe Demo ===\n")

    // ── Class: unsafe vs safe ──────────────────────────

    val unsafeUser = Async {
        kap(::User)
            .with { fetchFirstName() }   // String — swap with lastName? no error
            .with { fetchLastName() }    // String
            .with { fetchAge() }         // Int
    }
    println("  Unsafe class:    $unsafeUser")

    val safeUser = Async {
        kapSafe(::User)
            .with { fetchFirstName().toFirstName() }   // UserFirstName
            .with { fetchLastName().toLastName() }     // UserLastName — swap? COMPILE ERROR
            .with { fetchAge().toAge() }               // UserAge
    }
    println("  Safe class:      $safeUser")

    // ── Function: unsafe vs safe ───────────────────────

    val unsafeDash = Async {
        kap(::buildDashboard)
            .with { fetchUserName() }      // String — swap? no error
            .with { fetchCartSummary() }   // String
            .with { fetchPromoCode() }     // String
    }
    println("  Unsafe function: $unsafeDash")

    val safeDash = Async {
        kapSafeBuildDashboard(::buildDashboard)
            .with { fetchUserName().toUserName() }         // BuildDashboardUserName
            .with { fetchCartSummary().toCartSummary() }   // BuildDashboardCartSummary
            .with { fetchPromoCode().toPromoCode() }       // BuildDashboardPromoCode
    }
    println("  Safe function:   $safeDash")

    println("\n  Swap any same-typed .with in the safe variants — the compiler rejects it!")
}
