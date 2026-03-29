# kap-core

The foundation module. Type-safe parallel orchestration with visible phases.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-core:2.4.0")
```

**Depends on:** `kotlinx-coroutines-core` only.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 438 tests across 33 test classes, including property-based algebraic law verification.

---

## Parallel Execution — `kap` + `.with`

=== "Raw Coroutines"

    ```kotlin
    val dashboard = coroutineScope {
        val dUser = async { fetchUser() }
        val dCart = async { fetchCart() }
        val dPromos = async { fetchPromos() }
        Dashboard(
            dUser.await(),   // ← swap with dCart? Same type, no error
            dCart.await(),
            dPromos.await()
        )
    }
    ```

=== "Arrow"

    ```kotlin
    val dashboard = parZip(
        { fetchUser() },
        { fetchCart() },
        { fetchPromos() },
    ) { user, cart, promos ->    // ← swap user/cart? Same type, no error
        Dashboard(user, cart, promos)
    }
    ```

=== "KAP"

    ```kotlin
    val dashboard: Dashboard = Async {
        kap(::Dashboard)
            .with { fetchUser() }     // ┐ all three start at t=0
            .with { fetchCart() }      // │ total time = max(individual)
            .with { fetchPromos() }    // ┘ swap any two? COMPILE ERROR
    }
    ```

KAP's typed function chain enforces argument order. Each `.with` must provide the next expected type.

---

## Phase Barriers — `.then`

=== "Raw Coroutines"

    ```kotlin
    val result = coroutineScope {
        val dA = async { fetchA() }
        val dB = async { fetchB() }
        val a = dA.await()
        val b = dB.await()
        // Where does the barrier start? You have to read every line.
        val validated = validate(a, b)
        Result(a, b, validated)
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        kap(::Result)
            .with { fetchA() }         // ┐ parallel
            .with { fetchB() }         // ┘
            .then { validate() }       // ── barrier: waits for A and B
    }
    ```

`.then` creates an explicit synchronization point. Everything above must complete before anything below starts.

---

## Value-Dependent Phases — `.andThen`

=== "Raw Coroutines"

    ```kotlin
    // Phase 1
    val ctx = coroutineScope {
        val dProfile = async { fetchProfile(userId) }
        val dPrefs = async { fetchPreferences(userId) }
        val dTier = async { fetchLoyaltyTier(userId) }
        UserContext(dProfile.await(), dPrefs.await(), dTier.await())
    }
    // Phase 2 — needs ctx
    val enriched = coroutineScope {
        val dRecs = async { fetchRecommendations(ctx.profile) }
        val dPromos = async { fetchPromotions(ctx.tier) }
        val dTrending = async { fetchTrending(ctx.prefs) }
        val dHistory = async { fetchHistory(ctx.profile) }
        EnrichedContent(dRecs.await(), dPromos.await(), dTrending.await(), dHistory.await())
    }
    // Phase 3 — needs both
    val dashboard = coroutineScope {
        val dLayout = async { renderLayout(ctx, enriched) }
        val dTrack = async { trackAnalytics(ctx, enriched) }
        FinalDashboard(dLayout.await(), dTrack.await())
    }
    ```

=== "KAP"

    ```kotlin
    val dashboard: FinalDashboard = Async {
        kap(::UserContext)
            .with { fetchProfile(userId) }       // ┐
            .with { fetchPreferences(userId) }   // ├─ phase 1
            .with { fetchLoyaltyTier(userId) }   // ┘
            .andThen { ctx ->                    // ── barrier: ctx available
                kap(::EnrichedContent)
                    .with { fetchRecommendations(ctx.profile) }  // ┐
                    .with { fetchPromotions(ctx.tier) }           // ├─ phase 2
                    .with { fetchTrending(ctx.prefs) }            // │
                    .with { fetchHistory(ctx.profile) }           // ┘
                    .andThen { enriched ->                         // ── barrier
                        kap(::FinalDashboard)
                            .with { renderLayout(ctx, enriched) }     // ┐ phase 3
                            .with { trackAnalytics(ctx, enriched) }   // ┘
                    }
            }
    }
    ```

24 lines of nested `coroutineScope`/`async`/`await` vs 14 lines of flat chain. The dependency graph **is** the code shape.

---

## `.thenValue` — Sequential Value Fill (No Barrier)

Unlike `.then` which creates a real barrier, `.thenValue` fills a slot sequentially without blocking parallel siblings:

=== "Raw Coroutines"

    ```kotlin
    val result = coroutineScope {
        val dContent = async { fetchContent() }
        val dSidebar = async { fetchSidebar() }
        val content = dContent.await()
        val sidebar = dSidebar.await()
        val timestamp = computeTimestamp()  // sequential, but you manually thread it
        Page(content, sidebar, timestamp)
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        kap(::Page)
            .with { fetchContent() }           // parallel
            .with { fetchSidebar() }           // parallel
            .thenValue { computeTimestamp() }  // sequential fill, no barrier
    }
    ```

---

## Construction

### `Kap { }` — Wrap a suspend lambda

```kotlin
val effect: Kap<String> = Kap { fetchUser() }  // nothing runs yet
val result: String = Async { effect }            // NOW it runs
```

### `kap(f)` — Curry a function for `.with` chains

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

### `Kap.of(value)` / `Kap.empty()` / `Kap.failed(error)` / `Kap.defer { }`

```kotlin
val pure: Kap<Int> = Kap.of(42)                              // pure value
val unit: Kap<Unit> = Kap.empty()                             // Unit computation
val failed: Kap<String> = Kap.failed(RuntimeException("boom")) // wrapped failure
val lazy: Kap<String> = Kap.defer { Kap { expensiveSetup() } } // lazy construction
```

---

## Styles — `combine` / `pair` / `triple` / `zip`

=== "kap + with (type-safe order)"

    ```kotlin
    val result = Async {
        kap(::Dashboard)
            .with { fetchUser() }
            .with { fetchCart() }
            .with { fetchPromos() }
    }
    ```

=== "combine (suspend lambdas)"

    ```kotlin
    val result = Async {
        combine(
            { fetchUser() },
            { fetchCart() },
            { fetchPromos() },
        ) { user, cart, promos -> Dashboard(user, cart, promos) }
    }
    ```

=== "zip (pre-built Kaps)"

    ```kotlin
    val result = Async {
        zip(
            Kap { fetchUser() },
            Kap { fetchCart() },
            Kap { fetchPromos() },
        ) { user, cart, promos -> Dashboard(user, cart, promos) }
    }
    ```

=== "pair / triple"

    ```kotlin
    val (user, cart) = Async { pair({ fetchUser() }, { fetchCart() }) }
    val (a, b, c) = Async { triple({ fetchA() }, { fetchB() }, { fetchC() }) }
    ```

`zip` and `combine` support arities 2-22.

---

## Partial Failure — `.settled()`

=== "Raw Coroutines"

    ```kotlin
    // supervisorScope is manual and error-prone
    val result = supervisorScope {
        val dUser = async { fetchUserMayFail() }
        val dCart = async { fetchCartAlways() }
        val dConfig = async { fetchConfigAlways() }
        val user = try { dUser.await() } catch (e: Exception) { "anonymous" }
        val cart = dCart.await()
        val config = dConfig.await()
        PartialDashboard(user, cart, config)
    }
    ```

=== "KAP"

    ```kotlin
    val dashboard = Async {
        kap { user: Result<String>, cart: String, config: String ->
            PartialDashboard(user.getOrDefault("anonymous"), cart, config)
        }
            .with(Kap { fetchUserMayFail() }.settled())   // wrapped in Result
            .with { fetchCartAlways() }
            .with { fetchConfigAlways() }
    }
    // fetchUser fails? Dashboard still builds with "anonymous".
    ```

### `traverseSettled` — Collect ALL results, no cancellation

=== "Raw Coroutines"

    ```kotlin
    // supervisorScope + try/catch per item
    val results = supervisorScope {
        ids.map { id ->
            async {
                try { Result.success(fetchUser(id)) }
                catch (e: Exception) { Result.failure(e) }
            }
        }.awaitAll()
    }
    ```

=== "KAP"

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

=== "Raw Coroutines"

    ```kotlin
    // Manual Mutex + double-checked locking
    private val mutex = Mutex()
    private var cached: String? = null

    suspend fun fetchOnce(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val result = expensiveCall()
            cached = result
            result
        }
    }
    // Caches failures too. Transient error? Cached forever.
    ```

=== "KAP — `.memoize()`"

    ```kotlin
    val fetchOnce = Kap { expensiveCall() }.memoize()
    val a = Async { fetchOnce } // runs the actual call
    val b = Async { fetchOnce } // cached, instant
    ```

=== "KAP — `.memoizeOnSuccess()`"

    ```kotlin
    var callCount = 0
    val fetchOnce = Kap { callCount++; "expensive-result" }.memoizeOnSuccess()

    val a = Async { fetchOnce }  // runs, callCount=1
    val b = Async { fetchOnce }  // cached, callCount still 1
    // If first call FAILS? Not cached. Next call retries.
    ```

---

## Bounded Parallel Traversal — `traverse`

=== "Raw Coroutines"

    ```kotlin
    // Manual Semaphore management
    val semaphore = Semaphore(10)
    val results = coroutineScope {
        userIds.map { id ->
            async {
                semaphore.withPermit {
                    fetchUser(id)
                }
            }
        }.awaitAll()
    }
    ```

=== "Arrow"

    ```kotlin
    val results = userIds.parMap(concurrency = 10) { id ->
        fetchUser(id)
    }
    ```

=== "KAP"

    ```kotlin
    val results = Async {
        userIds.traverse(concurrency = 10) { id ->
            Kap { fetchUser(id) }
        }
    }
    ```

### `traverseDiscard` — Fire-and-forget

```kotlin
Async {
    userIds.traverseDiscard(concurrency = 5) { id ->
        Kap { notifyUser(id) }
    }
}
```

### `sequence` / `sequence(concurrency)`

```kotlin
val kaps: List<Kap<String>> = userIds.map { id -> Kap { fetchUser(id) } }
val results: List<String> = Async { kaps.sequence(concurrency = 10) }
```

---

## Racing

=== "Raw Coroutines"

    ```kotlin
    // Complex select expression
    val result = coroutineScope {
        select {
            async { fetchFromRegionUS() }.onAwait { it }
            async { fetchFromRegionEU() }.onAwait { it }
            async { fetchFromRegionAP() }.onAwait { it }
        }
        // Problem: losing coroutines not cancelled automatically
    }
    ```

=== "Arrow"

    ```kotlin
    val result = raceN(
        { fetchFromRegionUS() },
        { fetchFromRegionEU() },
        { fetchFromRegionAP() },
    )
    ```

=== "KAP"

    ```kotlin
    val fastest = Async {
        raceN(
            Kap { fetchFromRegionUS() },   // 100ms
            Kap { fetchFromRegionEU() },   // 30ms
            Kap { fetchFromRegionAP() },   // 60ms
        )
    }
    // Returns EU at 30ms. US and AP cancelled automatically.
    ```

### `race(fa, fb)` — Two-way race

=== "Raw Coroutines"

    ```kotlin
    val winner = coroutineScope {
        select {
            async { delay(100); "slow" }.onAwait { it }
            async { delay(30); "fast" }.onAwait { it }
        }
        // Loser coroutine still running — must cancel manually
    }
    ```

=== "KAP"

    ```kotlin
    val winner = Async {
        race(
            Kap { delay(100); "slow" },
            Kap { delay(30); "fast" },
        )
    }
    // "fast" at 30ms, loser cancelled automatically
    ```

### `raceAll(list)` — Race a dynamic list

=== "Raw Coroutines"

    ```kotlin
    val fastest = coroutineScope {
        val jobs = regions.map { region -> async { fetchFrom(region) } }
        select {
            jobs.forEach { deferred ->
                deferred.onAwait { it }
            }
        }
        // Must manually cancel remaining jobs
        jobs.forEach { it.cancel() }
    }
    ```

=== "KAP"

    ```kotlin
    val replicas = regions.map { region -> Kap { fetchFrom(region) } }
    val fastest = Async { raceAll(replicas) }
    // Losers cancelled automatically
    ```

---

## Error Handling

### `.timeout(duration, default)`

=== "Raw Coroutines"

    ```kotlin
    val result = withTimeoutOrNull(500) { fetchSlowService() } ?: "fallback-value"
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchSlowService() }
            .timeout(500.milliseconds) { "fallback-value" }
    }
    ```

### `.recover { }` / `.recoverWith { }`

=== "Raw Coroutines"

    ```kotlin
    val result = try {
        fetchUser()
    } catch (e: Exception) {
        "recovered"
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap<String> { throw RuntimeException("fail") }
            .recover { "recovered" }
    }
    ```

### `.orElse(other)` / `firstSuccessOf`

=== "Raw Coroutines"

    ```kotlin
    val result = try {
        source1()
    } catch (e: Exception) {
        try {
            source2()
        } catch (e: Exception) {
            source3()  // nested try/catch hell
        }
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        firstSuccessOf(
            Kap { source1() },  // fails
            Kap { source2() },  // fails
            Kap { source3() },  // wins
        )
    }

    // Or: chained fallback
    val result2 = Async {
        Kap<String> { throw RuntimeException("fail") }
            .orElse(Kap { "fallback-ok" })
    }
    ```

### `.retry(maxAttempts, delay, backoff)`

Simple retry (for composable Schedule-based retry, see [kap-resilience](kap-resilience.md)):

=== "Raw Coroutines"

    ```kotlin
    var result: String? = null
    var lastError: Exception? = null
    repeat(3) { attempt ->
        try {
            result = flakyService()
            return@repeat
        } catch (e: Exception) {
            lastError = e
            delay(10L * (attempt + 1))  // manual backoff
        }
    }
    result ?: throw lastError!!
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { flakyService() }
            .retry(3, delay = 10.milliseconds)
    }
    ```

### `.ensure(error) { predicate }` / `.ensureNotNull(error) { extract }`

=== "Raw Coroutines"

    ```kotlin
    val age = fetchAge()
    if (age < 18) throw IllegalArgumentException("Must be 18+")

    val user = fetchUserOrNull()
        ?: throw NoSuchElementException("User not found")
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchAge() }
            .ensure(IllegalArgumentException("Must be 18+")) { it >= 18 }
    }

    val result2 = Async {
        Kap { fetchUserOrNull() }
            .ensureNotNull(NoSuchElementException("User not found")) { it }
    }
    ```

### `catching { }` — Exception-safe Result

=== "Raw Coroutines"

    ```kotlin
    val result: Result<String> = try {
        Result.success(riskyOperation())
    } catch (e: Exception) {
        Result.failure(e)
    }
    ```

=== "KAP"

    ```kotlin
    val result: Result<String> = catching { riskyOperation() }
    // Result.success("value") or Result.failure(exception)
    ```

---

## Flow Integration

=== "Raw Coroutines"

    ```kotlin
    // Manual concurrency in Flow
    userIdFlow
        .flatMapMerge(concurrency = 5) { id ->
            flow { emit(fetchUser(id)) }
        }
        .collect { user -> process(user) }
    ```

=== "KAP"

    ```kotlin
    // Parallel Flow processing
    userIdFlow
        .mapEffect(concurrency = 5) { id -> Kap { fetchUser(id) } }
        .collect { user -> process(user) }
    ```

### `Flow.mapEffectOrdered` — Preserve upstream order

=== "Raw Coroutines"

    ```kotlin
    // channelFlow + manual index tracking to preserve order
    val results: Flow<String> = channelFlow {
        val buffer = ConcurrentHashMap<Int, String>()
        var nextIndex = 0
        val semaphore = Semaphore(5)
        userIdFlow.collectIndexed { index, id ->
            semaphore.acquire()
            launch {
                val result = fetchUser(id)
                buffer[index] = result
                semaphore.release()
                // Flush in-order results... (complex bookkeeping)
            }
        }
    }
    ```

=== "KAP"

    ```kotlin
    val results: Flow<String> = userIdFlow
        .mapEffectOrdered(concurrency = 5) { id -> Kap { fetchUser(id) } }
    // Results arrive in the same order as the input Flow
    ```

### `Flow.firstAsKap()`

=== "Raw Coroutines"

    ```kotlin
    val result: String = userIdFlow.first()
    // Direct, but not composable with other Kap chains
    ```

=== "KAP"

    ```kotlin
    val first: Kap<String> = userIdFlow.firstAsKap()
    val result = Async { first }
    // Composable — can .map, .recover, .timeout, combine with other Kaps
    ```

---

## Interop

### `Deferred.toKap()` / `Kap.toDeferred(scope)`

Bridge between existing coroutine code and KAP. Useful when you have a `Deferred` from a library or legacy code and want to compose it with other `Kap` combinators (`.map`, `.recover`, `.timeout`, parallel `.with` chains, etc.).

```kotlin
val deferred: Deferred<String> = scope.async { fetchUser() }
val kap: Kap<String> = deferred.toKap()
val result = Async { kap }
```

### `(suspend () -> A).toKap()`

```kotlin
val lambda: suspend () -> String = { fetchUser() }
val kap: Kap<String> = lambda.toKap()
```

### `computation { }` — Imperative builder

=== "Raw Coroutines"

    ```kotlin
    val result = coroutineScope {
        val user = fetchDashUser()     // sequential
        val cart = fetchDashCart()      // sequential, could be parallel
        "$user has $cart"
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        computation {
            val user = Kap { fetchDashUser() }.bind()
            val cart = Kap { fetchDashCart() }.bind()
            "$user has $cart"
        }
    }
    ```

---

## Utilities

### `.keepFirst` / `.keepSecond`

Run both in parallel, keep only one result:

=== "Raw Coroutines"

    ```kotlin
    val user = coroutineScope {
        val dUser = async { fetchUser() }
        val dLog = async { logAccess() }
        dLog.await()    // must explicitly await to ensure it completes
        dUser.await()   // easy to forget awaiting the side-effect
    }
    ```

=== "KAP"

    ```kotlin
    val user = Async {
        Kap { fetchUser() }
            .keepFirst(Kap { logAccess() })  // both run, only user returned
    }
    ```

### `.discard()` / `.peek { }`

```kotlin
val unit = Async {
    Kap { fetchUser() }.discard()  // runs but returns Unit
}

val user = Async {
    Kap { fetchUser() }
        .peek { println("Fetched: $it") }  // side-effect, returns original value
}
```

### `.on(context)` / `.named(name)`

```kotlin
val result = Async {
    Kap { readFile() }
        .on(Dispatchers.IO)          // switch dispatcher
        .named("file-read")          // coroutine name for debugging
}
```

### `.await()` — Execute from any suspend context

```kotlin
suspend fun myFunction(): String {
    return Kap { fetchUser() }.await()  // no need for Async { }
}
```

### `delayed(duration, value)` / `withOrNull`

```kotlin
val result = Async { delayed(100.milliseconds, "delayed-value") }

val maybeResult: String? = withOrNull { Kap { riskyOperation() } }
```

---

## Observability — `traced(name, tracer)`

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

`Kap<A>` is **lazy** — nothing runs until `Async { }`:

```kotlin
val plan: Kap<Dashboard> = kap(::Dashboard)
    .with { fetchDashUser() }
    .with { fetchDashCart() }
    .with { fetchDashPromos() }

println("Plan built. Nothing has executed yet.")
println("plan is: ${plan::class.simpleName}")

val result: Dashboard = Async { plan }  // NOW it runs
```

**Key guarantees:**

- **Structured concurrency**: All parallel branches run inside `coroutineScope`. One fails → siblings cancel.
- **Cancellation safety**: `CancellationException` is never caught. All combinators re-throw it.
- **Context propagation**: `Async(MDCContext()) { ... }` propagates context to all branches.
- **No reflection**: All type safety is compile-time. Zero runtime overhead.
- **Algebraic laws**: Functor, Applicative, Monad — property-tested via Kotest. See [LAWS.md](https://github.com/damian-rafael-lattenero/kap/blob/master/LAWS.md).
