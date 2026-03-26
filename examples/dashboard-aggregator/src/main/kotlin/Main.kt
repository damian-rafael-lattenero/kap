import applicative.*
import kotlinx.coroutines.delay

/**
 * Dashboard BFF aggregator — 14 service calls, 5 phases.
 *
 * Simulates a Backend-for-Frontend that assembles a dashboard from
 * many microservices with sequential authorization and enrichment gates.
 *
 * Every service call returns a distinct domain type, and the final
 * DashboardView is assembled via kap(::DashboardView) — the compiler
 * enforces that each slot receives the correct type.
 */

// ── Domain types ────────────────────────────────────────────────────

data class UserProfile(val name: String, val tier: String)
data class Preferences(val theme: String, val layout: String)
data class FeatureFlags(val flags: Map<String, Boolean>)
data class AuthToken(val token: String, val scope: String)
data class FeedContent(val posts: List<String>)
data class NotificationList(val unread: Int)
data class MessageSummary(val conversations: Int)
data class Recommendations(val suggested: List<String>)
data class AnalyticsEnrichment(val segment: String)
data class TrendingTopics(val tags: List<String>)
data class PeopleSuggestions(val count: Int)
data class AdSlots(val content: String)
data class SocialProof(val likesToday: Int)
data class AppVersion(val version: String)

// ── Assembled dashboard ─────────────────────────────────────────────

data class DashboardView(
    val user: UserProfile,
    val prefs: Preferences,
    val flags: FeatureFlags,
    val auth: AuthToken,
    val feed: FeedContent,
    val notifications: NotificationList,
    val messages: MessageSummary,
    val recommendations: Recommendations,
    val analytics: AnalyticsEnrichment,
    val trending: TrendingTopics,
    val suggestions: PeopleSuggestions,
    val ads: AdSlots,
    val social: SocialProof,
    val version: AppVersion,
)

// ── Phase 1: Core user data (parallel) ──────────────────────────────

suspend fun fetchUserProfile(): UserProfile {
    delay(100); return UserProfile("Alice", "premium")
}

suspend fun fetchPreferences(): Preferences {
    delay(60); return Preferences("dark", "compact")
}

suspend fun fetchFeatureFlags(): FeatureFlags {
    delay(40); return FeatureFlags(mapOf("beta-search" to true, "new-feed" to false))
}

// ── Phase 2: Authorization gate (sequential) ────────────────────────

suspend fun authorize(): AuthToken {
    delay(80); return AuthToken("tok_abc", "read:write")
}

// ── Phase 3: Main content (parallel, requires auth) ─────────────────

suspend fun fetchFeed(): FeedContent {
    delay(150); return FeedContent(listOf("post-1", "post-2", "post-3", "post-4", "post-5",
        "post-6", "post-7", "post-8", "post-9", "post-10"))
}

suspend fun fetchNotifications(): NotificationList {
    delay(90); return NotificationList(unread = 3)
}

suspend fun fetchMessages(): MessageSummary {
    delay(110); return MessageSummary(conversations = 5)
}

suspend fun fetchRecommendations(): Recommendations {
    delay(70); return Recommendations(listOf("Bob", "Carol", "Dave", "Eve"))
}

// ── Phase 4: Analytics enrichment (sequential) ──────────────────────

suspend fun enrichWithAnalytics(): AnalyticsEnrichment {
    delay(60); return AnalyticsEnrichment("power-user")
}

// ── Phase 5: Sidebar content (parallel) ─────────────────────────────

suspend fun fetchTrending(): TrendingTopics {
    delay(80); return TrendingTopics(listOf("#kotlin", "#coroutines", "#applicatives"))
}

suspend fun fetchSuggestions(): PeopleSuggestions {
    delay(50); return PeopleSuggestions(count = 3)
}

suspend fun fetchAds(): AdSlots {
    delay(30); return AdSlots("sponsored: Kotlin in Action 2nd ed.")
}

suspend fun fetchSocialProof(): SocialProof {
    delay(40); return SocialProof(likesToday = 142)
}

suspend fun fetchAppVersion(): AppVersion {
    delay(20); return AppVersion("v2.1.0")
}

suspend fun main() {
    println("=== Dashboard Aggregator (14 calls, 5 phases) ===\n")

    val start = System.currentTimeMillis()

    // Type-safe: each .with slot is verified at compile time against
    // the corresponding DashboardView constructor parameter type.
    // Swapping two calls (e.g. fetchFeed and fetchNotifications) is a compile error.
    val dashboard = Async {
        kap(::DashboardView)
            // Phase 1: User context (parallel)
            .with { fetchUserProfile().also { println("  Phase 1 [${System.currentTimeMillis() - start}ms]: user loaded") } }
            .with { fetchPreferences() }
            .with { fetchFeatureFlags() }
            // Phase 2: Authorization (must know user first)
            .then { authorize().also { println("  Phase 2 [${System.currentTimeMillis() - start}ms]: authorized") } }
            // Phase 3: Main content (parallel, requires auth)
            .with { fetchFeed() }
            .with { fetchNotifications() }
            .with { fetchMessages() }
            .with { fetchRecommendations().also { println("  Phase 3 [${System.currentTimeMillis() - start}ms]: content loaded") } }
            // Phase 4: Analytics enrichment (sequential)
            .then { enrichWithAnalytics().also { println("  Phase 4 [${System.currentTimeMillis() - start}ms]: analytics enriched") } }
            // Phase 5: Sidebar (parallel)
            .with { fetchTrending() }
            .with { fetchSuggestions() }
            .with { fetchAds() }
            .with { fetchSocialProof() }
            .with { fetchAppVersion().also { println("  Phase 5 [${System.currentTimeMillis() - start}ms]: sidebar loaded") } }
    }

    val elapsed = System.currentTimeMillis() - start
    println("\nDashboard assembled in ${elapsed}ms with 14 fields")
    println("Sequential time would be ~${100 + 60 + 40 + 80 + 150 + 90 + 110 + 70 + 60 + 80 + 50 + 30 + 40 + 20}ms")
    println()
    println("  user:            ${dashboard.user.name} (${dashboard.user.tier})")
    println("  prefs:           ${dashboard.prefs.theme} theme, ${dashboard.prefs.layout} layout")
    println("  flags:           ${dashboard.flags.flags}")
    println("  auth:            ${dashboard.auth.token} [${dashboard.auth.scope}]")
    println("  feed:            ${dashboard.feed.posts.size} posts")
    println("  notifications:   ${dashboard.notifications.unread} unread")
    println("  messages:        ${dashboard.messages.conversations} conversations")
    println("  recommendations: ${dashboard.recommendations.suggested}")
    println("  analytics:       segment=${dashboard.analytics.segment}")
    println("  trending:        ${dashboard.trending.tags}")
    println("  suggestions:     ${dashboard.suggestions.count} people")
    println("  ads:             ${dashboard.ads.content}")
    println("  social:          ${dashboard.social.likesToday} likes today")
    println("  version:         ${dashboard.version.version}")
}
