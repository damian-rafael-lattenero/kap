# Quickstart

Get KAP running in 5 minutes.

[Use the starter template](https://github.com/damian-rafael-lattenero/kap-starter/generate){ .md-button .md-button--primary }

Or add the dependency manually:

## 1. Add the dependency

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'io.github.damian-rafael-lattenero:kap-core:2.3.0'
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.damian-rafael-lattenero</groupId>
        <artifactId>kap-core-jvm</artifactId>
        <version>2.3.0</version>
    </dependency>
    ```

## 2. Write your first parallel call

```kotlin
import kap.*

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun main() {
    val result = Async {
        kap(::Dashboard)
            .with { fetchUser() }     // ┐ all three in parallel
            .with { fetchCart() }      // │ total time = max(individual)
            .with { fetchPromos() }    // ┘ not sum
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}

suspend fun fetchUser(): String { /* ... */ return "Alice" }
suspend fun fetchCart(): String { /* ... */ return "3 items" }
suspend fun fetchPromos(): String { /* ... */ return "SAVE20" }
```

That's it. Three calls run in parallel, results are type-checked into your data class.

## 3. Add phases

Real-world flows have dependencies. Use `.then` for barriers:

```kotlin
val checkout = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }           // ┐ phase 1: parallel
        .with { fetchCart() }            // ┘
        .then { validateStock() }        // ── phase 2: waits for phase 1
        .with { calcShipping() }         // ┐ phase 3: parallel
        .with { calcTax() }              // ┘
}
```

`.with` = parallel. `.then` = barrier. The code shape **is** the execution plan.

## 4. Use value-dependent phases

When phase 2 needs phase 1's result, use `.andThen`:

```kotlin
val dashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }      // ┐ phase 1
        .with { fetchPreferences(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::EnrichedDashboard)
                .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2
                .with { fetchPromotions(ctx.tier) }           // ┘
        }
}
```

## 5. Run an example

```bash
git clone https://github.com/damian-rafael-lattenero/kap.git
cd kap
./gradlew :examples:ecommerce-checkout:run
```

## What's next?

- [Core Concepts](core-concepts.md) — Understand the `with`/`then`/`andThen` model in depth
- [Parallel API Aggregation](parallel-aggregation.md) — Build a real BFF endpoint
- [Modules](../modules/kap-core.md) — Full API reference per module
- [Comparison](../comparison.md) — KAP vs Arrow vs raw coroutines
