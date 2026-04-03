import kap.*
import kotlinx.coroutines.delay

/**
 * Dashboard BFF aggregator — 14 service calls, 5 phases.
 *
 * Simulates a Backend-for-Frontend that assembles a dashboard from
 * many microservices with sequential authorization and enrichment gates.
 *
 * 14 fields: 7 are String, 4 are Int, 1 is a List, 1 is a Map, 1 is a Boolean.
 * Named builders (.withUser, .withPrefs, ...) make every slot unique at compile time —
 * no wrapper types needed. Swap any two .with lines and it won't compile.
 */

// ── Assembled dashboard ─────────────────────────────────────────────

@KapTypeSafe
data class DashboardView(
    val user: String,
    val prefs: String,
    val flags: String,
    val auth: String,
    val feed: String,
    val notifications: Int,
    val messages: Int,
    val recommendations: String,
    val analytics: String,
    val trending: String,
    val suggestions: Int,
    val ads: String,
    val social: Int,
    val version: String,
)

// ── Phase 1: Core user data (parallel) ──────────────────────────────

suspend fun fetchUserProfile(): String {
    delay(100); return "Alice (premium)"
}

suspend fun fetchPreferences(): String {
    delay(60); return "dark theme, compact layout"
}

suspend fun fetchFeatureFlags(): String {
    delay(40); return "beta-search=true, new-feed=false"
}

// ── Phase 2: Authorization gate (sequential) ────────────────────────

suspend fun authorize(): String {
    delay(80); return "tok_abc [read:write]"
}

// ── Phase 3: Main content (parallel, requires auth) ─────────────────

suspend fun fetchFeed(): String {
    delay(150); return "10 posts loaded"
}

suspend fun fetchNotifications(): Int {
    delay(90); return 3
}

suspend fun fetchMessages(): Int {
    delay(110); return 5
}

suspend fun fetchRecommendations(): String {
    delay(70); return "Bob, Carol, Dave, Eve"
}

// ── Phase 4: Analytics enrichment (sequential) ──────────────────────

suspend fun enrichWithAnalytics(): String {
    delay(60); return "power-user"
}

// ── Phase 5: Sidebar content (parallel) ─────────────────────────────

suspend fun fetchTrending(): String {
    delay(80); return "#kotlin, #coroutines, #applicatives"
}

suspend fun fetchSuggestions(): Int {
    delay(50); return 3
}

suspend fun fetchAds(): String {
    delay(30); return "sponsored: Kotlin in Action 2nd ed."
}

suspend fun fetchSocialProof(): Int {
    delay(40); return 142
}

suspend fun fetchAppVersion(): String {
    delay(20); return "v2.1.0"
}

suspend fun main() {
    println("=== Dashboard Aggregator (14 calls, 5 phases) ===\n")

    val start = System.currentTimeMillis()

    // Type-safe: each .with slot is verified at compile time against
    // the corresponding DashboardView constructor parameter name.
    // 14 fields, 7 are String — and the compiler still catches every swap.
    val dashboard = kap(::DashboardView)
            // Phase 1: User context (parallel)
            .withUser { fetchUserProfile().also { println("  Phase 1 [${System.currentTimeMillis() - start}ms]: user loaded") } }
            .withPrefs { fetchPreferences() }
            .withFlags { fetchFeatureFlags() }
            // Phase 2: Authorization (must know user first)
            .thenAuth { authorize().also { println("  Phase 2 [${System.currentTimeMillis() - start}ms]: authorized") } }
            // Phase 3: Main content (parallel, requires auth)
            .withFeed { fetchFeed() }
            .withNotifications { fetchNotifications() }
            .withMessages { fetchMessages() }
            .withRecommendations { fetchRecommendations().also { println("  Phase 3 [${System.currentTimeMillis() - start}ms]: content loaded") } }
            // Phase 4: Analytics enrichment (sequential)
            .thenAnalytics { enrichWithAnalytics().also { println("  Phase 4 [${System.currentTimeMillis() - start}ms]: analytics enriched") } }
            // Phase 5: Sidebar (parallel)
            .withTrending { fetchTrending() }
            .withSuggestions { fetchSuggestions() }
            .withAds { fetchAds() }
            .withSocial { fetchSocialProof() }
            .withVersion { fetchAppVersion().also { println("  Phase 5 [${System.currentTimeMillis() - start}ms]: sidebar loaded") } }
            .executeGraph()

    val elapsed = System.currentTimeMillis() - start
    println("\nDashboard assembled in ${elapsed}ms with 14 fields")
    println("Sequential time would be ~${100 + 60 + 40 + 80 + 150 + 90 + 110 + 70 + 60 + 80 + 50 + 30 + 40 + 20}ms")
    println()
    println("  user:            ${dashboard.user}")
    println("  prefs:           ${dashboard.prefs}")
    println("  flags:           ${dashboard.flags}")
    println("  auth:            ${dashboard.auth}")
    println("  feed:            ${dashboard.feed}")
    println("  notifications:   ${dashboard.notifications} unread")
    println("  messages:        ${dashboard.messages} conversations")
    println("  recommendations: ${dashboard.recommendations}")
    println("  analytics:       segment=${dashboard.analytics}")
    println("  trending:        ${dashboard.trending}")
    println("  suggestions:     ${dashboard.suggestions} people")
    println("  ads:             ${dashboard.ads}")
    println("  social:          ${dashboard.social} likes today")
    println("  version:         ${dashboard.version}")
}
