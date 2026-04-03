---
hide:
  - toc
---

# Cookbook

Complete, self-contained examples. Every block compiles and runs. Verified on every CI push in [`readme-examples`](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/readme-examples).

```bash
# Run all examples yourself:
git clone https://github.com/damian-rafael-lattenero/kap.git && cd kap
./gradlew :examples:readme-examples:run
```

---

## Parallel Execution — 3 services at once

```kotlin
import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchUser(): String { delay(30); return "Alice" }
suspend fun fetchCart(): String { delay(20); return "3 items" }
suspend fun fetchPromos(): String { delay(10); return "SAVE20" }

suspend fun main() {
    val result: Dashboard = kap(::Dashboard)
        .withUser { fetchUser() }     // ┐ all three start at t=0
        .withCart { fetchCart() }      // │ total time = max(30, 20, 10) = 30ms
        .withPromos { fetchPromos() }    // ┘ not 60ms sequential
        .executeGraph()
    println(result)
    // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

---

## Functions — `@KapTypeSafe` on `fun`

`@KapTypeSafe` works on functions too. KSP generates a marker object from the function name:

```kotlin
import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
fun createUser(name: String, email: String, age: Int): String =
    "User($name, $email, age=$age)"

suspend fun fetchName(): String { delay(30); return "Alice" }
suspend fun fetchEmail(): String { delay(20); return "alice@example.com" }
suspend fun fetchAge(): Int { delay(10); return 30 }

suspend fun main() {
    // CreateUser is a generated marker object — classes use ::ClassName, functions use ObjectName
    val result = kap(CreateUser)
        .withName { fetchName() }
        .withEmail { fetchEmail() }
        .withAge { fetchAge() }
        .executeGraph()
    println(result)
    // User(Alice, alice@example.com, age=30)
}
```

---

## 11-Service Checkout — 5 phases, one flat chain

```kotlin
import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
data class CheckoutResult(
    val user: String, val cart: Double, val promos: String,
    val inventory: Boolean, val stock: Boolean,
    val shipping: Double, val tax: Double, val discounts: Double,
    val payment: Boolean, val confirmation: String, val email: String,
)

suspend fun fetchUser(): String { delay(50); return "Alice" }
suspend fun fetchCart(): Double { delay(40); return 147.50 }
suspend fun fetchPromos(): String { delay(30); return "SUMMER20" }
suspend fun fetchInventory(): Boolean { delay(50); return true }
suspend fun validateStock(): Boolean { delay(20); return true }
suspend fun calcShipping(): Double { delay(30); return 5.99 }
suspend fun calcTax(): Double { delay(20); return 12.38 }
suspend fun calcDiscounts(): Double { delay(15); return 29.50 }
suspend fun reservePayment(): Boolean { delay(40); return true }
suspend fun generateConfirmation(): String { delay(30); return "order-#90142" }
suspend fun sendEmail(): String { delay(20); return "alice@example.com" }

suspend fun main() {
    val checkout: CheckoutResult = kap(::CheckoutResult)
        .withUser { fetchUser() }              // ┐
        .withCart { fetchCart() }               // ├─ phase 1: parallel
        .withPromos { fetchPromos() }             // │
        .withInventory { fetchInventory() }          // ┘
        .thenStock { validateStock() }           // ── phase 2: barrier
        .withShipping { calcShipping() }            // ┐
        .withTax { calcTax() }                 // ├─ phase 3: parallel
        .withDiscounts { calcDiscounts() }           // ┘
        .thenPayment { reservePayment() }          // ── phase 4: barrier
        .withConfirmation { generateConfirmation() }    // ┐ phase 5: parallel
        .withEmail { sendEmail() }              // ┘
        .executeGraph()
    println(checkout)
    // CheckoutResult(user=Alice, cart=147.5, promos=SUMMER20, inventory=true, stock=true, shipping=5.99, tax=12.38, discounts=29.5, payment=true, confirmation=order-#90142, email=alice@example.com)
    // 130ms total — not 460ms sequential
}
```

---

## Value-Dependent Phases — `.andThen`

```kotlin
import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
data class UserContext(val profile: String, val prefs: String, val tier: String)
@KapTypeSafe
data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

suspend fun fetchProfile(id: String): String { delay(50); return "profile-$id" }
suspend fun fetchPreferences(id: String): String { delay(30); return "prefs-dark" }
suspend fun fetchLoyaltyTier(id: String): String { delay(40); return "gold" }
suspend fun fetchRecommendations(profile: String): String { delay(40); return "recs-for-$profile" }
suspend fun fetchPromotions(tier: String): String { delay(30); return "promos-$tier" }
suspend fun fetchTrending(prefs: String): String { delay(20); return "trending-$prefs" }

suspend fun main() {
    val dashboard = kap(::UserContext)
        .withProfile { fetchProfile("user-1") }       // ┐ phase 1: parallel
        .withPrefs { fetchPreferences("user-1") }   // │
        .withTier { fetchLoyaltyTier("user-1") }   // ┘
        .andThen { ctx ->                       // ── barrier: ctx available
            kap(::PersonalizedDashboard)
                .withRecs { fetchRecommendations(ctx.profile) }  // ┐ phase 2: parallel
                .withPromos { fetchPromotions(ctx.tier) }           // │ uses ctx from phase 1
                .withTrending { fetchTrending(ctx.prefs) }            // ┘
        }
        .executeGraph()
    println(dashboard)
    // PersonalizedDashboard(recs=recs-for-profile-user-1, promos=promos-gold, trending=trending-prefs-dark)
}
```

---

## Partial Failure — `settled { }`

```kotlin
import kap.*
import kotlinx.coroutines.delay

// The type changes: user is Result<String> instead of String
@KapTypeSafe
data class PartialDashboard(val user: Result<String>, val cart: String, val config: String)

// Services: one is unreliable, the others always succeed
suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

suspend fun main() {
    val dashboard = kap(::PartialDashboard)
        .withUser(settled { fetchUserMayFail() })  // Result<String> — won't cancel siblings
        .withCart { fetchCartAlways() }             // String — runs normally
        .withConfig { fetchConfigAlways() }            // String — runs normally
        .executeGraph()

    println(dashboard)
    // PartialDashboard(user=Result.failure(RuntimeException), cart=cart-ok, config=config-ok)

    // Use the result with a fallback:
    val userName = dashboard.user.getOrDefault("anonymous")
    println("userName = $userName")  // "anonymous"
}
```

---

## Collect ALL Results — `traverseSettled`

```kotlin
import kap.*

suspend fun main() {
    val ids = listOf(1, 2, 3, 4, 5)
    val results: List<Result<String>> = ids.traverseSettled { id ->
        Kap {
            if (id % 2 == 0) throw RuntimeException("fail-$id")
            "user-$id"
        }
    }.executeGraph()
    val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
    val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!!.message }
    println("successes=$successes, failures=$failures")
    // successes=[user-1, user-3, user-5], failures=[fail-2, fail-4]
}
```

---

## Racing — Fastest region wins

```kotlin
import kap.*
import kotlinx.coroutines.delay

suspend fun fetchFromRegionUS(): String { delay(100); return "US-data" }
suspend fun fetchFromRegionEU(): String { delay(30); return "EU-data" }
suspend fun fetchFromRegionAP(): String { delay(60); return "AP-data" }

suspend fun main() {
    val fastest = raceN(
        Kap { fetchFromRegionUS() },   // 100ms
        Kap { fetchFromRegionEU() },   // 30ms — wins
        Kap { fetchFromRegionAP() },   // 60ms
    ).executeGraph()
    println(fastest)
    // EU-data  (at ~30ms, US and AP cancelled automatically)
}
```

---

## Retry with Schedule

```kotlin
import kap.*
import kotlin.time.Duration.Companion.milliseconds

suspend fun main() {
    var attempts = 0

    val policy = Schedule.times<Throwable>(5) and
        Schedule.exponential(10.milliseconds) and
        Schedule.doWhile<Throwable> { it is RuntimeException }

    val result = Kap {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        "success on attempt $attempts"
    }.retry(policy).executeGraph()
    println(result)
    // success on attempt 3
}
```

---

## TimeoutRace — Parallel fallback

```kotlin
import kap.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

suspend fun fetchFromPrimary(): String { delay(200); return "primary-data" }
suspend fun fetchFromFallback(): String { delay(30); return "fallback-data" }

suspend fun main() {
    val start = System.currentTimeMillis()
    val result = Kap { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
        .executeGraph()
    val elapsed = System.currentTimeMillis() - start
    println("$result (${elapsed}ms — fallback won, both started at t=0)")
    // fallback-data (30ms — 2.6x faster than sequential timeout)
}
```

---

## Resource Safety — `bracket`

```kotlin
import kap.*
import kotlinx.coroutines.delay

class MockConnection(val name: String) {
    var closed = false
    suspend fun query(q: String): String { delay(20); return "$name:result-of-$q" }
    suspend fun get(key: String): String { delay(15); return "$name:$key" }
    fun close() { closed = true; println("  closed $name") }
}

suspend fun openDb(): MockConnection { delay(10); return MockConnection("db") }
suspend fun openCache(): MockConnection { delay(10); return MockConnection("cache") }
suspend fun openHttp(): MockConnection { delay(10); return MockConnection("http") }

suspend fun main() {
    val result = Kap.of { db: String -> { cache: String -> { api: String -> "$db|$cache|$api" } } }
        .with(bracket(
            acquire = { openDb() },
            use = { conn -> Kap { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },  // guaranteed, even on failure
        ))
        .with(bracket(
            acquire = { openCache() },
            use = { conn -> Kap { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openHttp() },
            use = { client -> Kap { client.get("/api") } },
            release = { client -> client.close() },
        ))
        .executeGraph()
    println(result)
    //   closed db
    //   closed cache
    //   closed http
    // db:result-of-SELECT 1|cache:key|http:/api
}
```

---

## Parallel Validation — Collect every error

```kotlin
import kap.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.delay

sealed class RegError(val message: String) {
    class NameTooShort(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class AgeTooLow(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> {
    delay(20)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.NameTooShort("Name must be >= 2 chars")))
}

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> {
    delay(15)
    return if ("@" in email) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Invalid email: $email")))
}

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> {
    delay(10)
    return if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.AgeTooLow("Must be >= 18, got $age")))
}

suspend fun checkUsername(username: String): Either<NonEmptyList<RegError>, ValidUsername> {
    delay(25)
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}

suspend fun main() {
    // All fail — every error collected in one response:
    val result: Either<NonEmptyList<RegError>, User> = zipV(
        { validateName("A") },           // too short
        { validateEmail("bad") },         // no @
        { validateAge(10) },              // under 18
        { checkUsername("al") },           // too short
    ) { name, email, age, username -> User(name, email, age, username) }
        .executeGraph()

    when (result) {
        is Either.Right -> println("Valid: ${result.value}")
        is Either.Left -> println("${result.value.size} errors: ${result.value.map { it.message }}")
    }
    // 4 errors: [Name must be >= 2 chars, Invalid email: bad, Must be >= 18 got 10, Username too short]
    // ALL 4 validators ran in parallel. All errors in one response.
}
```

---

## Memoization — Cache only successes

```kotlin
import kap.*
import kotlinx.coroutines.delay

suspend fun main() {
    var callCount = 0
    val fetchOnce = Kap { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

    val a = fetchOnce.executeGraph()  // runs the actual call, callCount=1
    val b = fetchOnce.executeGraph()  // cached, instant, callCount still 1
    println("a=$a, b=$b, callCount=$callCount")
    // a=expensive-result, b=expensive-result, callCount=1
    // If first call HAD failed? Not cached. Next call would retry.
}
```

---

## Graph as Data — reuse and branch

```kotlin
import kap.*
import kotlinx.coroutines.delay

@KapTypeSafe
data class Order(val user: String, val cart: String, val total: Double)

suspend fun fetchUser(): String { delay(50); return "Alice" }
suspend fun fetchStandardCart(): String { delay(40); return "3 items" }
suspend fun fetchPremiumCart(): String { delay(30); return "3 items + priority" }

suspend fun main() {
    // The graph is data — nothing runs until .executeGraph()
    val base = kap(::Order).withUser { fetchUser() }

    // Complete it differently based on runtime conditions
    fun addCart(partial: OrderStep1, premium: Boolean): OrderStep2 =
        if (premium) partial.withCart { fetchPremiumCart() }
        else partial.withCart { fetchStandardCart() }

    // Build two different graphs from the same base
    val standard = addCart(base, premium = false).withTotal { 99.0 }.executeGraph()
    val premium = addCart(base, premium = true).withTotal { 149.0 }.executeGraph()

    println("Standard: $standard")
    println("Premium: $premium")
    // Standard: Order(user=Alice, cart=3 items, total=99.0)
    // Premium: Order(user=Alice, cart=3 items + priority, total=149.0)
}
```

---

## Real HTTP — GitHub API

From [`examples/real-world-http`](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/real-world-http):

```kotlin
import kap.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

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
)

@Serializable
data class CatFact(val fact: String, val length: Int = 0)

@KapTypeSafe
data class DeveloperProfile(val user: GithubUser, val topRepos: List<GithubRepo>, val funFact: String)

val client = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

suspend fun fetchGithubUser(username: String): GithubUser =
    client.get("https://api.github.com/users/$username").body()

suspend fun fetchGithubRepos(username: String): List<GithubRepo> =
    client.get("https://api.github.com/users/$username/repos?sort=stars&per_page=5").body()

suspend fun fetchCatFact(): CatFact =
    client.get("https://catfact.ninja/fact").body()

suspend fun main() {
    val profile = kap(::DeveloperProfile)
        .withUser { fetchGithubUser("JetBrains") }      // ┐
        .withTopRepos { fetchGithubRepos("JetBrains") }      // ├─ all three in parallel
        .withFunFact { fetchCatFact().fact }                  // ┘
        .executeGraph()

    println("User: ${profile.user.login} (${profile.user.name})")
    println("Repos: ${profile.user.publicRepos} public")
    profile.topRepos.forEach { println("  - ${it.name} (${it.stars} stars)") }
    println("Fun fact: ${profile.funFact}")
    // User: JetBrains (JetBrains)
    // Repos: 805 public
    //   - kotlin (50423 stars)
    //   - ...
    // Fun fact: Cats sleep 70% of their lives.

    client.close()
}
```

---

Ready to try? [Get Started](guide/quickstart.md){ .md-button .md-button--primary } &nbsp; [GitHub](https://github.com/damian-rafael-lattenero/kap){ .md-button }
