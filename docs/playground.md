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

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchUser(): String { delay(30); return "Alice" }
suspend fun fetchCart(): String { delay(20); return "3 items" }
suspend fun fetchPromos(): String { delay(10); return "SAVE20" }

suspend fun main() {
    val result: Dashboard = Async {
        kap(::Dashboard)
            .with { fetchUser() }     // ┐ all three start at t=0
            .with { fetchCart() }      // │ total time = max(30, 20, 10) = 30ms
            .with { fetchPromos() }    // ┘ not 60ms sequential
    }
    println(result)
    // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

---

## 11-Service Checkout — 5 phases, one flat chain

```kotlin
import kap.*
import kotlinx.coroutines.delay

data class UserProfile(val name: String, val id: Long)
data class ShoppingCart(val items: Int, val total: Double)
data class PromotionBundle(val code: String, val discountPct: Int)
data class InventorySnapshot(val allInStock: Boolean)
data class StockConfirmation(val confirmed: Boolean)
data class ShippingQuote(val amount: Double, val method: String)
data class TaxBreakdown(val amount: Double, val rate: Double)
data class DiscountSummary(val amount: Double, val promoApplied: String)
data class PaymentAuth(val cardLast4: String, val authorized: Boolean)
data class OrderConfirmation(val orderId: String)
data class EmailReceipt(val sentTo: String, val orderId: String)

data class CheckoutResult(
    val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
    val inventory: InventorySnapshot, val stock: StockConfirmation,
    val shipping: ShippingQuote, val tax: TaxBreakdown, val discounts: DiscountSummary,
    val payment: PaymentAuth, val confirmation: OrderConfirmation, val email: EmailReceipt,
)

suspend fun fetchUser(): UserProfile { delay(50); return UserProfile("Alice", 42) }
suspend fun fetchCart(): ShoppingCart { delay(40); return ShoppingCart(3, 147.50) }
suspend fun fetchPromos(): PromotionBundle { delay(30); return PromotionBundle("SUMMER20", 20) }
suspend fun fetchInventory(): InventorySnapshot { delay(50); return InventorySnapshot(true) }
suspend fun validateStock(): StockConfirmation { delay(20); return StockConfirmation(true) }
suspend fun calcShipping(): ShippingQuote { delay(30); return ShippingQuote(5.99, "standard") }
suspend fun calcTax(): TaxBreakdown { delay(20); return TaxBreakdown(12.38, 0.08) }
suspend fun calcDiscounts(): DiscountSummary { delay(15); return DiscountSummary(29.50, "SUMMER20") }
suspend fun reservePayment(): PaymentAuth { delay(40); return PaymentAuth("4242", true) }
suspend fun generateConfirmation(): OrderConfirmation { delay(30); return OrderConfirmation("order-#90142") }
suspend fun sendEmail(): EmailReceipt { delay(20); return EmailReceipt("alice@example.com", "order-#90142") }

suspend fun main() {
    val checkout: CheckoutResult = Async {
        kap(::CheckoutResult)
            .with { fetchUser() }              // ┐
            .with { fetchCart() }               // ├─ phase 1: parallel
            .with { fetchPromos() }             // │
            .with { fetchInventory() }          // ┘
            .then { validateStock() }           // ── phase 2: barrier
            .with { calcShipping() }            // ┐
            .with { calcTax() }                 // ├─ phase 3: parallel
            .with { calcDiscounts() }           // ┘
            .then { reservePayment() }          // ── phase 4: barrier
            .with { generateConfirmation() }    // ┐ phase 5: parallel
            .with { sendEmail() }              // ┘
    }
    println(checkout)
    // CheckoutResult(user=UserProfile(name=Alice, id=42), cart=ShoppingCart(items=3, total=147.5), ...)
    // 130ms total — not 460ms sequential
}
```

---

## Value-Dependent Phases — `.andThen`

```kotlin
import kap.*
import kotlinx.coroutines.delay

data class UserContext(val profile: String, val prefs: String, val tier: String)
data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

suspend fun fetchProfile(id: String): String { delay(50); return "profile-$id" }
suspend fun fetchPreferences(id: String): String { delay(30); return "prefs-dark" }
suspend fun fetchLoyaltyTier(id: String): String { delay(40); return "gold" }
suspend fun fetchRecommendations(profile: String): String { delay(40); return "recs-for-$profile" }
suspend fun fetchPromotions(tier: String): String { delay(30); return "promos-$tier" }
suspend fun fetchTrending(prefs: String): String { delay(20); return "trending-$prefs" }

suspend fun main() {
    val dashboard = Async {
        kap(::UserContext)
            .with { fetchProfile("user-1") }       // ┐ phase 1: parallel
            .with { fetchPreferences("user-1") }   // │
            .with { fetchLoyaltyTier("user-1") }   // ┘
            .andThen { ctx ->                       // ── barrier: ctx available
                kap(::PersonalizedDashboard)
                    .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2: parallel
                    .with { fetchPromotions(ctx.tier) }           // │ uses ctx from phase 1
                    .with { fetchTrending(ctx.prefs) }            // ┘
            }
    }
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
data class PartialDashboard(val user: Result<String>, val cart: String, val config: String)

// Services: one is unreliable, the others always succeed
suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

suspend fun main() {
    val dashboard = Async {
        kap(::PartialDashboard)
            .with(settled { fetchUserMayFail() })  // Result<String> — won't cancel siblings
            .with { fetchCartAlways() }             // String — runs normally
            .with { fetchConfigAlways() }            // String — runs normally
    }

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
    val results: List<Result<String>> = Async {
        ids.traverseSettled { id ->
            Kap {
                if (id % 2 == 0) throw RuntimeException("fail-$id")
                "user-$id"
            }
        }
    }
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
    val fastest = Async {
        raceN(
            Kap { fetchFromRegionUS() },   // 100ms
            Kap { fetchFromRegionEU() },   // 30ms — wins
            Kap { fetchFromRegionAP() },   // 60ms
        )
    }
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

    val result = Async {
        Kap {
            attempts++
            if (attempts <= 2) throw RuntimeException("flake #$attempts")
            "success on attempt $attempts"
        }.retry(policy)
    }
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
    val result = Async {
        Kap { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
    }
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
    val result = Async {
        kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
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
    }
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
    val result: Either<NonEmptyList<RegError>, User> = Async {
        zipV(
            { validateName("A") },           // too short
            { validateEmail("bad") },         // no @
            { validateAge(10) },              // under 18
            { checkUsername("al") },           // too short
        ) { name, email, age, username -> User(name, email, age, username) }
    }

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

    val a = Async { fetchOnce }  // runs the actual call, callCount=1
    val b = Async { fetchOnce }  // cached, instant, callCount still 1
    println("a=$a, b=$b, callCount=$callCount")
    // a=expensive-result, b=expensive-result, callCount=1
    // If first call HAD failed? Not cached. Next call would retry.
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
    val profile = Async {
        kap(::DeveloperProfile)
            .with { fetchGithubUser("JetBrains") }      // ┐
            .with { fetchGithubRepos("JetBrains") }      // ├─ all three in parallel
            .with { fetchCatFact().fact }                  // ┘
    }

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
