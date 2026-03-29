# Quickstart

Get KAP running in 5 minutes.

[Use the starter template](https://github.com/damian-rafael-lattenero/kap-starter/generate){ .md-button .md-button--primary }

Or add the dependency manually:

## 1. Add the dependency

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'io.github.damian-rafael-lattenero:kap-core:2.5.0'
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.damian-rafael-lattenero</groupId>
        <artifactId>kap-core-jvm</artifactId>
        <version>2.5.0</version>
    </dependency>
    ```

## 2. Write your first parallel call

Copy, paste, run:

```kotlin
import kap.*
import kotlinx.coroutines.delay

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchUser(): String { delay(50); return "Alice" }
suspend fun fetchCart(): String { delay(40); return "3 items" }
suspend fun fetchPromos(): String { delay(30); return "SAVE20" }

suspend fun main() {
    val result: Dashboard = Async {
        kap(::Dashboard)
            .with { fetchUser() }     // ┐ all three start at t=0
            .with { fetchCart() }      // │ total time = max(50, 40, 30) = 50ms
            .with { fetchPromos() }    // ┘ not 120ms sequential
    }
    println(result)
    // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

That's it. Three calls run in parallel, results are type-checked into your data class.

## 3. Add phases

Real-world flows have dependencies. Use `.then` for barriers:

```kotlin
import kap.*
import kotlinx.coroutines.delay

data class User(val name: String)
data class Cart(val items: Int)
data class StockCheck(val confirmed: Boolean)
data class ShippingQuote(val amount: Double)
data class TaxBreakdown(val rate: Double)
data class CheckoutResult(
    val user: User, val cart: Cart, val stock: StockCheck,
    val shipping: ShippingQuote, val tax: TaxBreakdown,
)

suspend fun fetchUser(): User { delay(50); return User("Alice") }
suspend fun fetchCart(): Cart { delay(40); return Cart(3) }
suspend fun validateStock(): StockCheck { delay(20); return StockCheck(true) }
suspend fun calcShipping(): ShippingQuote { delay(30); return ShippingQuote(5.99) }
suspend fun calcTax(): TaxBreakdown { delay(20); return TaxBreakdown(0.08) }

suspend fun main() {
    val checkout: CheckoutResult = Async {
        kap(::CheckoutResult)
            .with { fetchUser() }           // ┐ phase 1: parallel
            .with { fetchCart() }            // ┘
            .then { validateStock() }        // ── phase 2: waits for phase 1
            .with { calcShipping() }         // ┐ phase 3: parallel
            .with { calcTax() }              // ┘
    }
    println(checkout)
    // CheckoutResult(user=User(name=Alice), cart=Cart(items=3), stock=StockCheck(confirmed=true),
    //   shipping=ShippingQuote(amount=5.99), tax=TaxBreakdown(rate=0.08))
}
```

`.with` = parallel. `.then` = barrier. The code shape **is** the execution plan.

## 4. Use value-dependent phases

When phase 2 needs phase 1's result, use `.andThen`:

```kotlin
import kap.*
import kotlinx.coroutines.delay

data class UserContext(val profile: String, val prefs: String)
data class EnrichedDashboard(val recs: String, val promos: String)

suspend fun fetchProfile(userId: String): String { delay(50); return "profile-$userId" }
suspend fun fetchPreferences(userId: String): String { delay(30); return "dark-mode" }
suspend fun fetchRecommendations(profile: String): String { delay(40); return "recs-for-$profile" }
suspend fun fetchPromotions(prefs: String): String { delay(30); return "promos-for-$prefs" }

suspend fun main() {
    val userId = "user-42"

    val dashboard: EnrichedDashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }      // ┐ phase 1: parallel
            .with { fetchPreferences(userId) }   // ┘
            .andThen { ctx ->                    // ── barrier: ctx available
                kap(::EnrichedDashboard)
                    .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2: parallel
                    .with { fetchPromotions(ctx.prefs) }          // ┘ uses ctx from phase 1
            }
    }
    println(dashboard)
    // EnrichedDashboard(recs=recs-for-profile-user-42, promos=promos-for-dark-mode)
}
```

## 5. Run an example

```bash
git clone https://github.com/damian-rafael-lattenero/kap.git
cd kap
./gradlew :examples:ecommerce-checkout:run
```

## What's next?

- [kap-core KDocs](../modules/kap-core.md) — Full API with Raw/Arrow/KAP comparisons
- [kap-resilience KDocs](../modules/kap-resilience.md) — Schedule, CircuitBreaker, Resource
- [kap-arrow KDocs](../modules/kap-arrow.md) — Parallel validation with error accumulation
- [Cookbook](../playground.md) — 12 complete runnable examples
- [Comparison](../comparison.md) — KAP vs Arrow vs raw coroutines
