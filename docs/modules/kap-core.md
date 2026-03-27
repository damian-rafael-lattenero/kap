# kap-core

The foundation module. Type-safe parallel orchestration with visible phases.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")
```

**Depends on:** `kotlinx-coroutines-core` only.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 438 tests across 33 test classes, including property-based algebraic law verification.

---

## The Three Primitives

### `.with { }` — Parallel Execution

Every `.with` in the same phase runs concurrently. The typed function chain enforces argument order at compile time:

```kotlin
data class Dashboard(val user: String, val cart: String, val promos: String)

val result: Dashboard = Async {
    kap(::Dashboard)
        .with { fetchDashUser() }     // ┐ all three start at t=0
        .with { fetchDashCart() }      // │ total time = max(individual)
        .with { fetchDashPromos() }    // ┘ not sum
}
// Dashboard(user=Alice, cart=3 items, promos=SAVE20)
```

Swap any two `.with` lines? **Compile error.** Each slot expects a specific type.

### `.then { }` — Phase Barrier

Everything above must complete before `.then` executes. Creates a synchronization point:

```kotlin
data class R3(val a: String, val b: String, val c: String)

val result = Async {
    kap(::R3)
        .with { fetchA() }             // ┐ parallel
        .with { fetchB() }             // ┘
        .then { validate() }           // waits for A and B, then runs
}
```

### `.andThen { ctx -> }` — Value-Dependent Sequencing

When the next phase **needs** the previous result:

```kotlin
data class UserContext(val profile: String, val prefs: String, val tier: String)
data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

val dashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐ phase 1: parallel
        .with { fetchPreferences(userId) }   // │
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::PersonalizedDashboard)
                .with { fetchRecommendations(ctx.profile) }   // ┐ phase 2
                .with { fetchPromotions(ctx.tier) }           // │
                .with { fetchTrending(ctx.prefs) }            // ┘
        }
}
```

---

## Construction

### `Kap { }` — Wrap a suspend lambda

```kotlin
val effect: Kap<String> = Kap { fetchUser() }  // nothing runs yet
val result: String = Async { effect }            // runs now
```

### `Kap.of(value)` — Pure value

```kotlin
val pure: Kap<Int> = Kap.of(42)
val result = Async { pure } // 42, no computation
```

### `Kap.failed(error)` — Wrap a failure

```kotlin
val failed: Kap<String> = Kap.failed(RuntimeException("boom"))
// Async { failed } throws RuntimeException
```

### `Kap.defer { }` — Lazy construction

```kotlin
val lazy: Kap<String> = Kap.defer { Kap { expensiveSetup(); fetchData() } }
```

### `kap(f)` — Curry a function for `.with` chains

```kotlin
// ::CheckoutResult has type (User, Cart, ...) -> CheckoutResult
// kap curries it so each .with provides the next argument
val chain = kap(::CheckoutResult)
    .with { fetchUser() }
    .with { fetchCart() }
    // ...
```

Works with constructor refs, function refs, and lambdas:

```kotlin
// Constructor reference
val g1 = Async { kap(::Greeting).with { fetchName() }.with { "hello" } }

// Lambda
val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
val g2 = Async { kap(greet).with { fetchName() }.with { fetchAge() } }

// Function reference
fun buildSummary(name: String, items: Int): String = "$name has $items items"
val g3 = Async { kap(::buildSummary).with { fetchName() }.with { 5 } }
```

---

## Styles

### `combine` — Lifting with suspend lambdas

```kotlin
val result = Async {
    combine(
        { fetchDashUser() },
        { fetchDashCart() },
        { fetchDashPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
}
```

### `combine` — With pre-built Kaps

```kotlin
val result = Async {
    combine(
        Kap { fetchDashUser() },
        Kap { fetchDashCart() },
        Kap { fetchDashPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
}
```

### `pair` / `triple`

```kotlin
val (user, cart) = Async { pair({ fetchDashUser() }, { fetchDashCart() }) }
```

### `zip` (2-22 args)

```kotlin
val result = Async {
    zip(
        Kap { fetchA() },
        Kap { fetchB() },
        Kap { fetchC() },
    ) { a, b, c -> "$a+$b+$c" }
}
```

---

## Error Handling

### `.timeout(duration, default)`

```kotlin
val result = Async {
    Kap { fetchSlowService() }
        .timeout(500.milliseconds) { "fallback-value" }
}
```

### `.recover { }` / `.recoverWith { }`

```kotlin
val result = Async {
    Kap<String> { throw RuntimeException("fail") }
        .recover { "recovered" }
}
// "recovered"
```

### `.fallback(other)` / `.orElse(other)`

```kotlin
val result = Async {
    Kap<String> { throw RuntimeException("fail-1") }
        .orElse(Kap { "fallback-ok" })
}
// "fallback-ok"
```

### `firstSuccessOf`

Tries computations in order, returns the first success:

```kotlin
val result = Async {
    firstSuccessOf(
        Kap { throw RuntimeException("fail-1") },
        Kap { throw RuntimeException("fail-2") },
        Kap { "third-wins" },
    )
}
// "third-wins"
```

### `.retry(maxAttempts, delay, backoff)`

Simple retry (for composable Schedule-based retry, see kap-resilience):

```kotlin
val result = Async {
    Kap { flakyService() }
        .retry(3, delay = 10.milliseconds)
}
```

### `.ensure(error) { predicate }`

```kotlin
val result = Async {
    Kap { fetchAge() }
        .ensure(IllegalArgumentException("Must be 18+")) { it >= 18 }
}
```

---

## Partial Failure

### `.settled()` — Wrap in Result, no sibling cancellation

Your dashboard has three data sources. If the user service fails, you still want cart and config:

```kotlin
data class PartialDashboard(val user: String, val cart: String, val config: String)

val dashboard = Async {
    kap { user: Result<String>, cart: String, config: String ->
        PartialDashboard(user.getOrDefault("anonymous"), cart, config)
    }
        .with(Kap { fetchUserMayFail() }.settled())   // wrapped in Result
        .with { fetchCartAlways() }
        .with { fetchConfigAlways() }
}
// fetchUser fails? Dashboard still builds with "anonymous".
// fetchCart fails? Everything cancels (it's not settled).
```

### `traverseSettled` — Collect ALL results, no cancellation

```kotlin
val ids = listOf(1, 2, 3, 4, 5)
val results: List<Result<String>> = Async {
    ids.traverseSettled { id ->
        Kap {
            if (id % 2 == 0) throw RuntimeException("fail-$id")
            "user-$id"
        }
    }
}
// successes=[user-1, user-3, user-5], failures=[fail-2, fail-4]
```

---

## Memoization

### `.memoize()` — Cache result (success or failure)

```kotlin
val fetchOnce = Kap { expensiveCall() }.memoize()
val a = Async { fetchOnce } // runs the actual call
val b = Async { fetchOnce } // cached, instant
```

### `.memoizeOnSuccess()` — Cache only successes

```kotlin
var callCount = 0
val fetchOnce = Kap { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

val a = Async { fetchOnce }  // runs, callCount=1
val b = Async { fetchOnce }  // cached, callCount still 1
// If first call FAILS? Not cached. Next call retries.
```

---

## Collections

### `traverse(f)` / `traverse(concurrency, f)`

200 user IDs, downstream handles 10 concurrent requests max:

```kotlin
val results = Async {
    userIds.traverse(concurrency = 10) { id ->
        Kap { fetchUser(id) }
    }
}
```

### `traverseDiscard(f)` — Fire-and-forget parallel

```kotlin
Async {
    userIds.traverseDiscard(concurrency = 5) { id ->
        Kap { notifyUser(id) }
    }
}
```

### `sequence()` / `sequence(concurrency)`

```kotlin
val kaps: List<Kap<String>> = userIds.map { id -> Kap { fetchUser(id) } }
val results: List<String> = Async { kaps.sequence(concurrency = 10) }
```

---

## Racing

### `race(fa, fb)` — First to succeed wins

```kotlin
val fastest = Async {
    race(
        Kap { delay(100); "slow" },
        Kap { delay(30); "fast" },
    )
}
// "fast" at 30ms. Slow cancelled.
```

### `raceN(c1, c2, ..., cN)` — N-way race

```kotlin
val fastest = Async {
    raceN(
        Kap { fetchFromRegionUS() },   // 100ms
        Kap { fetchFromRegionEU() },   // 30ms
        Kap { fetchFromRegionAP() },   // 60ms
    )
}
// Returns EU at 30ms. US and AP cancelled.
```

### `raceAll(list)` — Race a dynamic list

```kotlin
val replicas = regions.map { region -> Kap { fetchFrom(region) } }
val fastest = Async { raceAll(replicas) }
```

---

## Flow Integration

### `Flow.mapEffect(concurrency) { }`

```kotlin
val results: Flow<String> = userIdFlow
    .mapEffect(concurrency = 5) { id -> Kap { fetchUser(id) } }
```

### `Flow.mapEffectOrdered(concurrency) { }`

Same as `mapEffect` but preserves upstream emission order.

### `Flow.firstAsKap()`

```kotlin
val first: Kap<String> = userIdFlow.firstAsKap()
```

---

## Interop

### `Deferred.toKap()` / `Kap.toDeferred(scope)`

```kotlin
val deferred: Deferred<String> = scope.async { fetchUser() }
val kap: Kap<String> = deferred.toKap()
```

### `computation { }` — Imperative builder with `.bind()`

```kotlin
val result = Async {
    computation {
        val user = Kap { fetchDashUser() }.bind()
        val cart = Kap { fetchDashCart() }.bind()
        "$user has $cart"
    }
}
// "Alice has 3 items"
```

---

## Transforms & Utilities

| Combinator | What it does |
|---|---|
| `.map { }` | Transform the result |
| `.discard()` | Run but discard result (returns `Unit`) |
| `.peek { }` | Side-effect without changing result |
| `.on(Dispatchers.IO)` | Switch dispatcher for this computation |
| `.named("fetch-user")` | Set coroutine name for debugging |
| `.keepFirst` / `.keepSecond` | Parallel, keep one result |
| `.await()` | Execute from any suspend context |

---

## Observability

Bring your own logger — no framework coupled:

```kotlin
val tracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

val result = Async {
    kap(::Dashboard)
        .with(Kap { fetchUser() }.traced("fetch-user", tracer))
        .with(Kap { fetchConfig() }.traced("fetch-config", tracer))
}
```

---

## Execution Model

`Kap<A>` is lazy — nothing runs until `Async { }`:

```kotlin
val plan: Kap<Dashboard> = kap(::Dashboard)
    .with { fetchDashUser() }
    .with { fetchDashCart() }
    .with { fetchDashPromos() }

println("Plan built. Nothing has executed yet.")

val result: Dashboard = Async { plan }  // NOW it runs
```

**Key guarantees:**

- **Structured concurrency**: All parallel branches run inside `coroutineScope`. If one fails, siblings cancel.
- **Cancellation safety**: `CancellationException` is never caught. All combinators re-throw it.
- **Context propagation**: `Async(MDCContext()) { ... }` propagates context to all branches.
- **No reflection**: All type safety is compile-time. Zero runtime overhead.
- **Algebraic laws**: `Kap` satisfies Functor, Applicative, and Monad laws — property-tested via Kotest. See [LAWS.md](https://github.com/damian-rafael-lattenero/kap/blob/master/LAWS.md).
