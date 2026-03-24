import applicative.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Resilient multi-source data fetcher — kap-core + kap-resilience.
 *
 * Demonstrates every resilience primitive in kap-resilience:
 *   - Schedule (exponential, jittered, withMaxDuration, times, and/or)
 *   - retry(schedule), retryOrElse, retryWithResult
 *   - CircuitBreaker + withCircuitBreaker
 *   - bracket / guarantee / guaranteeCase
 *   - Resource / Resource.zip / useComputation
 *   - timeoutRace (parallel timeout with eager fallback)
 *   - raceQuorum (N-of-M quorum)
 *
 * Scenario: a backend service fetches pricing data from 3 unreliable
 * replicas, user profiles from a flaky API, and config from a DB that
 * requires bracket-style cleanup.
 */

// ── Domain types ────────────────────────────────────────────────────────
data class PricingData(val price: Double, val currency: String, val source: String)
data class UserProfile(val id: Long, val name: String, val tier: String)
data class AppConfig(val maxItems: Int, val featureFlags: Map<String, Boolean>)
data class AuditLog(val entries: List<String>)
data class DualConfig(val primary: AppConfig, val secondary: AppConfig)

data class FetcherResult(
    val pricing: PricingData,
    val user: UserProfile,
    val config: AppConfig,
    val audit: AuditLog,
)

// ── Simulated infrastructure ────────────────────────────────────────────

var replicaAAttempts = 0
var replicaBAttempts = 0
var replicaCAttempts = 0
var userApiAttempts = 0

suspend fun fetchPricingReplicaA(): PricingData {
    replicaAAttempts++
    delay(80)
    return PricingData(99.99, "USD", "replica-A")
}

suspend fun fetchPricingReplicaB(): PricingData {
    replicaBAttempts++
    delay(120)
    return PricingData(99.99, "USD", "replica-B")
}

suspend fun fetchPricingReplicaC(): PricingData {
    replicaCAttempts++
    delay(60)
    return PricingData(99.99, "USD", "replica-C")
}

suspend fun fetchUserFlaky(attempt: Int): UserProfile {
    userApiAttempts++
    if (attempt < 2) {
        delay(30)
        throw RuntimeException("User API timeout (attempt $attempt)")
    }
    delay(50)
    return UserProfile(42, "Alice", "premium")
}

class DbConnection(val name: String) {
    var closed = false
    suspend fun query(): AppConfig {
        delay(40)
        return AppConfig(100, mapOf("dark-mode" to true, "beta-search" to false))
    }
    fun close() { closed = true }
}

suspend fun openDbConnection(): DbConnection {
    delay(20)
    return DbConnection("config-db")
}

suspend fun fetchAuditLogSlow(): AuditLog {
    delay(500)
    return AuditLog(listOf("slow-source"))
}

suspend fun fetchAuditLogCache(): AuditLog {
    delay(30)
    return AuditLog(listOf("login", "view-pricing", "update-cart"))
}

suspend fun main() {
    val start = System.currentTimeMillis()
    fun elapsed() = "${System.currentTimeMillis() - start}ms"

    // ═══════════════════════════════════════════════════════════════════
    //  1. raceQuorum: get pricing from 2-of-3 replicas
    // ═══════════════════════════════════════════════════════════════════
    println("=== 1. raceQuorum: 2-of-3 pricing replicas ===\n")

    val pricing = Async {
        raceQuorum(
            required = 2,
            Computation { fetchPricingReplicaA() },
            Computation { fetchPricingReplicaB() },
            Computation { fetchPricingReplicaC() },
        )
    }

    println("  Got ${pricing.size} pricing quotes:")
    pricing.forEach { println("    - ${it.source}: ${it.currency} ${it.price}") }
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  2. retry with Schedule: exponential backoff + jitter + max duration
    // ═══════════════════════════════════════════════════════════════════
    println("=== 2. retryWithResult: exponential backoff on flaky user API ===\n")

    var userAttempt = 0
    val retryPolicy = Schedule.times<Throwable>(5) and
        Schedule.exponential<Throwable>(50.milliseconds).jittered()

    val retryResult = Async {
        Computation { fetchUserFlaky(userAttempt++) }
            .retryWithResult(retryPolicy)
    }

    println("  Fetched: ${retryResult.value.name} (tier=${retryResult.value.tier})")
    println("  Took ${retryResult.attempts} retries, ${retryResult.totalDelay} total delay")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  3. bracket: DB connection with guaranteed cleanup
    // ═══════════════════════════════════════════════════════════════════
    println("=== 3. bracket: config from DB with guaranteed cleanup ===\n")

    val dbConn = arrayOfNulls<DbConnection>(1)
    val config = Async {
        bracket(
            acquire = {
                openDbConnection().also {
                    dbConn[0] = it
                    println("  Acquired: ${it.name}")
                }
            },
            use = { conn -> Computation { conn.query() } },
            release = { conn ->
                conn.close()
                println("  Released: ${conn.name} (closed=${conn.closed})")
            },
        )
    }

    println("  Config: maxItems=${config.maxItems}, flags=${config.featureFlags}")
    println("  Connection cleaned up: ${dbConn[0]?.closed}")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  4. Resource.zip + useComputation: composable resources
    // ═══════════════════════════════════════════════════════════════════
    println("=== 4. Resource.zip: composable resource management ===\n")

    val dbResource = Resource(
        acquire = { openDbConnection().also { println("  Acquired: ${it.name}-1") } },
        release = { conn -> conn.close(); println("  Released: ${conn.name}-1") },
    )
    val cacheResource = Resource(
        acquire = { openDbConnection().also { println("  Acquired: ${it.name}-2 (cache)") } },
        release = { conn -> conn.close(); println("  Released: ${conn.name}-2 (cache)") },
    )

    val combined = Resource.zip(dbResource, cacheResource) { db, cache -> db to cache }

    val dualConfig = combined.use { pair ->
        Async {
            kap(::DualConfig)
                .with { pair.first.query() }
                .with { pair.second.query() }
        }
    }
    println("  DB config: ${dualConfig.primary}")
    println("  Cache config: ${dualConfig.secondary}")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  5. timeoutRace: parallel timeout with eager fallback
    // ═══════════════════════════════════════════════════════════════════
    println("=== 5. timeoutRace: slow source vs cache (200ms deadline) ===\n")

    val audit = Async {
        Computation { fetchAuditLogSlow() }
            .timeoutRace(200.milliseconds, Computation { fetchAuditLogCache() })
    }

    println("  Audit log: ${audit.entries}")
    println("  (came from cache because slow source takes 500ms)")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  6. CircuitBreaker: protect downstream with circuit breaker
    // ═══════════════════════════════════════════════════════════════════
    println("=== 6. CircuitBreaker: protect calls to failing service ===\n")

    val breaker = CircuitBreaker(
        maxFailures = 3,
        resetTimeout = 1.seconds,
        onStateChange = { old, new -> println("  Circuit: $old -> $new") },
    )

    var cbAttempt = 0
    repeat(5) { i ->
        val result = runCatching {
            Async {
                Computation {
                    cbAttempt++
                    if (cbAttempt <= 4) throw RuntimeException("Service down (call $cbAttempt)")
                    "recovered!"
                }.withCircuitBreaker(breaker)
            }
        }
        println("  Call ${i + 1}: ${result.fold({ "OK: $it" }, { it.message ?: "error" })}")
    }
    println("  Final circuit state: ${breaker.currentState}")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  7. retryOrElse: graceful degradation on exhaustion
    // ═══════════════════════════════════════════════════════════════════
    println("=== 7. retryOrElse: fallback when retries exhausted ===\n")

    val limitedPolicy = Schedule.times<Throwable>(2) and
        Schedule.spaced<Throwable>(30.milliseconds)

    val graceful = Async {
        Computation<String> { throw RuntimeException("Always fails") }
            .retryOrElse(limitedPolicy) { err ->
                "Fallback value (original error: ${err.message})"
            }
    }

    println("  Result: $graceful")
    println("  (${elapsed()})\n")

    // ═══════════════════════════════════════════════════════════════════
    //  8. Full pipeline: combine everything with kap+with+followedBy
    //     ap(Computation<A>) overload accepts resilience-wrapped computations
    // ═══════════════════════════════════════════════════════════════════
    println("=== 8. Full pipeline: all resilience features composed ===\n")

    val pipelineStart = System.currentTimeMillis()

    val fullResult = Async {
        kap(::FetcherResult)
            // Phase 1: quorum pricing (parallel 2-of-3), take first result
            .with(
                raceQuorum(
                    required = 2,
                    Computation { fetchPricingReplicaA() },
                    Computation { fetchPricingReplicaB() },
                    Computation { fetchPricingReplicaC() },
                ).map { it.first() }
            )
            // Phase 2: resilient user fetch (retry with backoff)
            .with(run {
                var att = 0
                Computation { fetchUserFlaky(att++) }
                    .retry(Schedule.times<Throwable>(3) and Schedule.exponential(20.milliseconds))
            })
            // Phase 3: bracketed config (sequential — needs user for auth)
            .followedBy(
                bracket(
                    acquire = { openDbConnection() },
                    use = { conn -> Computation { conn.query() } },
                    release = { conn -> conn.close() },
                )
            )
            // Phase 4: audit with timeout fallback
            .with(
                Computation { fetchAuditLogSlow() }
                    .timeoutRace(100.milliseconds, Computation { fetchAuditLogCache() })
            )
    }

    val pipelineElapsed = System.currentTimeMillis() - pipelineStart
    println("  Pricing:  ${fullResult.pricing.source} @ ${fullResult.pricing.currency} ${fullResult.pricing.price}")
    println("  User:     ${fullResult.user.name} (${fullResult.user.tier})")
    println("  Config:   maxItems=${fullResult.config.maxItems}")
    println("  Audit:    ${fullResult.audit.entries}")
    println("  Pipeline time: ${pipelineElapsed}ms")
    println("\nTotal example time: ${elapsed()}")
}
