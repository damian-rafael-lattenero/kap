import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kap.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

// ── Domain types ───────────────────────────────────────────────

@Serializable
data class GithubUser(
    val login: String,
    val name: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
)

@Serializable
data class GithubRepo(
    val name: String,
    @SerialName("stargazers_count") val stars: Int = 0,
    val language: String? = null,
    val description: String? = null,
)

@Serializable
data class CatFact(val fact: String, val length: Int = 0)

@KapTypeSafe
data class DeveloperProfile(
    val user: GithubUser,
    val topRepos: List<GithubRepo>,
    val funFact: String,
)

// ── HTTP client ────────────────────────────────────────────────

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// ── Real API calls ─────────────────────────────────────────────

suspend fun fetchGithubUser(username: String): GithubUser =
    client.get("https://api.github.com/users/$username").body()

suspend fun fetchGithubRepos(username: String): List<GithubRepo> =
    client.get("https://api.github.com/users/$username/repos?sort=stars&per_page=5").body()

suspend fun fetchCatFact(): CatFact =
    client.get("https://catfact.ninja/fact").body()

// ── Main: parallel orchestration with real HTTP ────────────────

suspend fun main() {
    println("╔══════════════════════════════════════════════╗")
    println("║  KAP Real-World HTTP Example                 ║")
    println("╚══════════════════════════════════════════════╝\n")

    val username = "JetBrains"

    // ── Example 1: Simple parallel — 3 APIs at once ────────────

    println("1. Parallel fetch: GitHub user + repos + cat fact\n")

    val (profile, duration) = measureTimedValue {
        kap(::DeveloperProfile)
                .withUser { fetchGithubUser(username) }       // ┐
                .withTopRepos { fetchGithubRepos(username) }   // ├─ all three in parallel
                .withFunFact { fetchCatFact().fact }           // ┘
                .evalGraph()
    }

    println("   User: ${profile.user.login} (${profile.user.name})")
    println("   Repos: ${profile.user.publicRepos} public, top ${profile.topRepos.size}:")
    profile.topRepos.forEach { println("     - ${it.name} (${it.stars} stars, ${it.language})") }
    println("   Fun fact: ${profile.funFact}")
    println("   Time: ${duration.inWholeMilliseconds}ms (parallel, not sequential)\n")

    // ── Example 2: Phase-dependent — user first, then repos ────

    println("2. Phased: fetch user, then fetch their repos\n")

    data class PhasedResult(val user: GithubUser, val repos: List<GithubRepo>)

    val (phased, phasedDuration) = measureTimedValue {
        Kap { fetchGithubUser(username) }
                .andThen { user ->
                    println("   Phase 1 done: ${user.login} has ${user.publicRepos} repos")
                    Kap { fetchGithubRepos(user.login) }
                        .map { repos -> PhasedResult(user, repos) }
                }
                .evalGraph()
    }

    println("   Phase 2 done: top repo is ${phased.repos.firstOrNull()?.name}")
    println("   Time: ${phasedDuration.inWholeMilliseconds}ms (sequential phases)\n")

    // ── Example 3: Retry with Schedule ─────────────────────────

    println("3. Retry: fetch with exponential backoff\n")

    val retryPolicy = Schedule.times<Throwable>(3) and
        Schedule.exponential(100.milliseconds)

    val (retryResult, retryDuration) = measureTimedValue {
        Kap { fetchCatFact() }
                .retry(retryPolicy)
                .map { it.fact }
                .evalGraph()
    }

    println("   Fact: $retryResult")
    println("   Time: ${retryDuration.inWholeMilliseconds}ms\n")

    // ── Example 4: Race — fastest API wins ─────────────────────

    println("4. Race: two cat fact sources, fastest wins\n")

    val (raceResult, raceDuration) = measureTimedValue {
        race(
                Kap { fetchCatFact().fact },
                Kap { delay(50); "Cats sleep 70% of their lives (cached fallback)" },
            ).evalGraph()
    }

    println("   Winner: $raceResult")
    println("   Time: ${raceDuration.inWholeMilliseconds}ms\n")

    // ── Example 5: Traverse — parallel bounded fetch ───────────

    println("5. Traverse: fetch 5 users with concurrency=3\n")

    val users = listOf("torvalds", "gaearon", "sindresorhus", "tj", "yyx990803")
    val (profiles, traverseDuration) = measureTimedValue {
        users.traverse(concurrency = 3) { user ->
                Kap { fetchGithubUser(user) }
            }.evalGraph()
    }

    profiles.forEach { println("   ${it.login}: ${it.publicRepos} repos, ${it.followers} followers") }
    println("   Time: ${traverseDuration.inWholeMilliseconds}ms (5 users, max 3 concurrent)\n")

    // ── Example 6: Settled — partial failure tolerance ──────────

    println("6. Settled: one fails, rest still complete\n")

    data class MultiResult(val real: Result<GithubUser>, val fake: Result<GithubUser>, val fact: String)

    val (settled, settledDuration) = measureTimedValue {
        Kap.of { real: Result<GithubUser> -> { fake: Result<GithubUser> -> { fact: String -> MultiResult(real, fake, fact) } } }
            .with(Kap { fetchGithubUser("torvalds") }.settled())
            .with(Kap { fetchGithubUser("this-user-definitely-does-not-exist-xyz") }.settled())
            .with { fetchCatFact().fact }
            .evalGraph()
    }

    println("   Real user: ${settled.real.getOrNull()?.login ?: "failed"}")
    println("   Fake user: ${settled.fake.getOrNull()?.login ?: "failed (expected)"}")
    println("   Fact: ${settled.fact}")
    println("   Time: ${settledDuration.inWholeMilliseconds}ms\n")

    client.close()
    println("Done!")
}
