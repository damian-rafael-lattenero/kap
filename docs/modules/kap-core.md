# kap-core

The foundation module. Type-safe parallel orchestration with visible phases.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
```

**Depends on:** `kotlinx-coroutines-core` only.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 438 tests across 33 test classes, including property-based algebraic law verification.

---

## What kap-core solves

You have multiple async calls. Some parallel, some sequential. kap-core gives you `.with` for independent tasks, `.then` for barriers, and `.andThen` for dependent phases. The code shape becomes the execution plan.

With `@KapTypeSafe` (via the [kap-ksp](kap-ksp.md) module), you get **named builder methods** generated from your data class properties — `.withUser {}`, `.thenStock {}`, etc. — making chains self-documenting while retaining full compile-time type safety. The generic `.with {}` / `.then {}` API shown throughout this page is the underlying core API; named builders are the recommended user-facing pattern built on top of it.

---

## Level 1 — Learn First

### `.with` — Independent tasks in parallel

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

=== "KAP (named builders)"

    ```kotlin
    @KapTypeSafe
    data class Dashboard(val user: String, val cart: String, val promos: String)

    val dashboard: Dashboard = kap(::Dashboard)
        .withUser { fetchUser() }     // ┐ all three start at t=0
        .withCart { fetchCart() }      // │ total time = max(individual)
        .withPromos { fetchPromos() }  // ┘ swap any two? COMPILE ERROR
        .executeGraph()
    ```

=== "KAP (generic API)"

    ```kotlin
    val dashboard: Dashboard = kap(::Dashboard)
        .with { fetchUser() }     // ┐ all three start at t=0
        .with { fetchCart() }      // │ total time = max(individual)
        .with { fetchPromos() }    // ┘ swap any two? COMPILE ERROR
        .executeGraph()
    ```

`@KapTypeSafe` generates `.withUser {}`, `.withCart {}`, `.withPromos {}` from the data class properties. The generic `.with {}` API is equivalent but positional — named builders enforce the correct parameter at each step.

### `.then` — Phase barrier

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
    @KapTypeSafe
    data class Result(val a: A, val b: B, val validated: Validated)

    val result = kap(::Result)
        .withA { fetchA() }             // ┐ parallel
        .withB { fetchB() }             // ┘
        .thenValidated { validate() }   // ── barrier: waits for A and B
        .executeGraph()
    ```

`.then` creates an explicit synchronization point. Everything above must complete before anything below starts.

### `.andThen` — Dependent phase

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

=== "KAP (named builders)"

    ```kotlin
    // With @KapTypeSafe on UserContext, EnrichedContent, and FinalDashboard
    val dashboard: FinalDashboard = kap(::UserContext)
        .withProfile { fetchProfile(userId) }       // ┐
        .withPreferences { fetchPreferences(userId) }   // ├─ phase 1
        .withLoyaltyTier { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::EnrichedContent)
                .withRecommendations { fetchRecommendations(ctx.profile) }  // ┐
                .withPromotions { fetchPromotions(ctx.tier) }               // ├─ phase 2
                .withTrending { fetchTrending(ctx.prefs) }                  // │
                .withHistory { fetchHistory(ctx.profile) }                  // ┘
                .andThen { enriched ->                         // ── barrier
                    kap(::FinalDashboard)
                        .withLayout { renderLayout(ctx, enriched) }     // ┐ phase 3
                        .withAnalytics { trackAnalytics(ctx, enriched) }   // ┘
                }
        }
        .executeGraph()
    ```

=== "KAP (generic API)"

    ```kotlin
    val dashboard: FinalDashboard = kap(::UserContext)
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
        .executeGraph()
    ```

24 lines of nested `coroutineScope`/`async`/`await` vs 14 lines of flat chain. The dependency graph **is** the code shape.

---

## Level 2 — Common Patterns

### Composition styles

=== "kap + @KapTypeSafe (recommended)"

    ```kotlin
    @KapTypeSafe
    data class Dashboard(val user: String, val cart: String, val promos: String)

    val result = kap(::Dashboard)
        .withUser { fetchUser() }
        .withCart { fetchCart() }
        .withPromos { fetchPromos() }
        .executeGraph()
    ```

=== "kap + with (generic API)"

    ```kotlin
    val result = kap(::Dashboard)
        .with { fetchUser() }
        .with { fetchCart() }
        .with { fetchPromos() }
        .executeGraph()
    ```

=== "combine (suspend lambdas)"

    ```kotlin
    val result = combine(
        { fetchUser() },
        { fetchCart() },
        { fetchPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
        .executeGraph()
    ```

=== "zip (pre-built Kaps)"

    ```kotlin
    val result = zip(
        Kap { fetchUser() },
        Kap { fetchCart() },
        Kap { fetchPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
        .executeGraph()
    ```

=== "pair / triple"

    ```kotlin
    val (user, cart) = pair({ fetchUser() }, { fetchCart() }).executeGraph()
    val (a, b, c) = triple({ fetchA() }, { fetchB() }, { fetchC() }).executeGraph()
    ```

`zip` and `combine` support arities 2-22.

### Partial failure

**Use this when:** one service can fail but you still want the rest.

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

    **Without `settled` — one failure cancels everything:**

    ```kotlin
    @KapTypeSafe
    data class Dashboard(val user: String, val cart: String, val config: String)

    val dashboard = kap(::Dashboard)
        .withUser { fetchUser() }     // throws! → cart and config CANCELLED
        .withCart { fetchCart() }      // never runs
        .withConfig { fetchConfig() }  // never runs
        .executeGraph()
    // RuntimeException — entire dashboard lost. Cart and config were fine.
    ```

    **With `settled { }` — failure wrapped, siblings continue:**

    ```kotlin
    // The type changes: user becomes Result<String> instead of String
    @KapTypeSafe
    data class Dashboard(val user: Result<String>, val cart: String, val config: String)

    val dashboard = kap(::Dashboard)
        .withUser(settled { fetchUser() })   // Result<String> — won't cancel siblings
        .withCart { fetchCart() }              // String — runs normally
        .withConfig { fetchConfig() }          // String — runs normally
        .executeGraph()
    // Dashboard(user=Result.failure(RuntimeException), cart=cart-ok, config=config-ok)

    // Use the result with a fallback:
    val userName = dashboard.user.getOrDefault("anonymous")  // "anonymous"
    ```

#### `traverseSettled` — Collect ALL results, no cancellation

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
    val results: List<Result<String>> = ids.traverseSettled { id ->
        Kap {
            if (id % 2 == 0) throw RuntimeException("fail-$id")
            "user-$id"
        }
    }.executeGraph()
    // successes=[user-1, user-3, user-5], failures=[fail-2, fail-4]
    ```

### Collections

**Use this when:** you have a list of items to process in parallel with bounded concurrency.

#### `traverse`

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
    val results = userIds.traverse(concurrency = 10) { id ->
        Kap { fetchUser(id) }
    }.executeGraph()
    ```

#### `traverseDiscard` — Fire-and-forget

=== "Raw Coroutines"

    ```kotlin
    val semaphore = Semaphore(5)
    coroutineScope {
        userIds.map { id ->
            async {
                semaphore.withPermit { notifyUser(id) }
            }
        }.awaitAll()
    }
    // Result discarded — only side-effects matter
    ```

=== "Arrow"

    ```kotlin
    userIds.parMap(concurrency = 5) { id ->
        notifyUser(id)
    }
    // Arrow's parMap returns results; discard them manually
    ```

=== "KAP"

    ```kotlin
    userIds.traverseDiscard(concurrency = 5) { id ->
        Kap { notifyUser(id) }
    }.executeGraph()
    ```

#### `sequence` / `sequence(concurrency)`

=== "Raw Coroutines"

    ```kotlin
    val semaphore = Semaphore(10)
    val results = coroutineScope {
        userIds.map { id ->
            async { semaphore.withPermit { fetchUser(id) } }
        }.awaitAll()
    }
    ```

=== "Arrow"

    ```kotlin
    val results = userIds.parMap(concurrency = 10) { id ->
        fetchUser(id)
    }
    // Arrow has no pre-built effect list to sequence;
    // use parMap over the original collection instead
    ```

=== "KAP"

    ```kotlin
    val kaps: List<Kap<String>> = userIds.map { id -> Kap { fetchUser(id) } }
    val results: List<String> = kaps.sequence(concurrency = 10).executeGraph()
    ```

### Error handling

**Use this when:** you need fallbacks, timeouts, or retries.

#### `.timeout(duration, default)`

=== "Raw Coroutines"

    ```kotlin
    val result = withTimeoutOrNull(500) { fetchSlowService() } ?: "fallback-value"
    ```

=== "Arrow"

    ```kotlin
    // Arrow has no built-in timeout combinator; use kotlinx.coroutines directly:
    val result = withTimeoutOrNull(500) { fetchSlowService() } ?: "fallback-value"
    ```

=== "KAP"

    ```kotlin
    val result = Kap { fetchSlowService() }
        .timeout(500.milliseconds) { "fallback-value" }
        .executeGraph()
    ```

#### `.recover { }` / `.recoverWith { }`

=== "Raw Coroutines"

    ```kotlin
    val result = try {
        fetchUser()
    } catch (e: Exception) {
        "recovered"
    }
    ```

=== "Arrow"

    ```kotlin
    val result = Either.catch { fetchUser() }
        .getOrElse { "recovered" }
    ```

=== "KAP"

    ```kotlin
    val result = Kap<String> { throw RuntimeException("fail") }
        .recover { "recovered" }
        .executeGraph()
    ```

#### `.retry(maxAttempts, delay, backoff)`

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

=== "Arrow"

    ```kotlin
    // Arrow's Schedule can express retry policies, but requires arrow-resilience:
    val result = Schedule.recurs<Throwable>(3)
        .and(Schedule.exponential<Throwable>(10.milliseconds))
        .retry { flakyService() }
    ```

=== "KAP"

    ```kotlin
    val result = Kap { flakyService() }
        .retry(3, delay = 10.milliseconds)
        .executeGraph()
    ```

#### `.ensure(error) { predicate }` / `.ensureNotNull(error) { extract }`

=== "Raw Coroutines"

    ```kotlin
    val age = fetchAge()
    if (age < 18) throw IllegalArgumentException("Must be 18+")

    val user = fetchUserOrNull()
        ?: throw NoSuchElementException("User not found")
    ```

=== "Arrow"

    ```kotlin
    // Arrow's either { } block provides ensure via Raise:
    val result: Either<String, Int> = either {
        val age = fetchAge()
        ensure(age >= 18) { "Must be 18+" }
        age
    }

    val result2: Either<String, User> = either {
        val user = fetchUserOrNull()
        ensureNotNull(user) { "User not found" }
        user
    }
    ```

=== "KAP"

    ```kotlin
    val result = Kap { fetchAge() }
        .ensure(IllegalArgumentException("Must be 18+")) { it >= 18 }
        .executeGraph()

    val result2 = Kap { fetchUserOrNull() }
        .ensureNotNull(NoSuchElementException("User not found")) { it }
        .executeGraph()
    ```

#### `catching { }` — Exception-safe Result

=== "Raw Coroutines"

    ```kotlin
    val result: Result<String> = try {
        Result.success(riskyOperation())
    } catch (e: Exception) {
        Result.failure(e)
    }
    ```

=== "Arrow"

    ```kotlin
    val result: Either<Throwable, String> = Either.catch { riskyOperation() }
    // Either.Right("value") or Either.Left(exception)
    ```

=== "KAP"

    ```kotlin
    val result: Result<String> = catching { riskyOperation() }
    // Result.success("value") or Result.failure(exception)
    ```

#### `.orElse(other)` / `firstSuccessOf`

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
    val result = firstSuccessOf(
        Kap { source1() },  // fails
        Kap { source2() },  // fails
        Kap { source3() },  // wins
    ).executeGraph()

    // Or: chained fallback
    val result2 = Kap<String> { throw RuntimeException("fail") }
        .orElse(Kap { "fallback-ok" })
        .executeGraph()
    ```

### Racing

**Use this when:** you want the fastest result from multiple sources.

#### `raceN(c1, c2, ..., cN)` — First to succeed wins, rest cancelled

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
    val fastest = raceN(
        Kap { fetchFromRegionUS() },   // 100ms
        Kap { fetchFromRegionEU() },   // 30ms
        Kap { fetchFromRegionAP() },   // 60ms
    ).executeGraph()
    // Returns EU at 30ms. US and AP cancelled automatically.
    ```

#### `race(fa, fb)` — Two-way race

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

=== "Arrow"

    ```kotlin
    val winner = raceN(
        { delay(100); "slow" },
        { delay(30); "fast" },
    )
    // "fast" at 30ms, loser cancelled automatically
    ```

=== "KAP"

    ```kotlin
    val winner = race(
        Kap { delay(100); "slow" },
        Kap { delay(30); "fast" },
    ).executeGraph()
    // "fast" at 30ms, loser cancelled automatically
    ```

#### `raceAll(list)` — Race a dynamic list

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

=== "Arrow"

    ```kotlin
    // Arrow's raceN supports a fixed number of args (max 9), not a dynamic list.
    // For a dynamic list, you'd need to implement it manually:
    val fastest = coroutineScope {
        select {
            regions.map { region ->
                async { fetchFrom(region) }
            }.forEach { deferred ->
                deferred.onAwait { it }
            }
        }
    }
    ```

=== "KAP"

    ```kotlin
    val replicas = regions.map { region -> Kap { fetchFrom(region) } }
    val fastest = raceAll(replicas).executeGraph()
    // Losers cancelled automatically
    ```

---

## Level 3 — Advanced

### Construction utilities

#### `Kap { }` — Wrap a suspend lambda

```kotlin
val effect: Kap<String> = Kap { fetchUser() }  // nothing runs yet
val result: String = effect.executeGraph()       // NOW it runs
```

#### `kap(f)` — Curry a function for `.with` chains

Works with constructor refs, function refs, and lambdas:

```kotlin
// Constructor reference
@KapTypeSafe
data class Greeting(val name: String, val message: String)

val g1 = kap(::Greeting).withName { fetchName() }.withMessage { "hello" }.executeGraph()

// Lambda — use Kap.of with manual currying
val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
val g2 = Kap.of { name: String -> { age: Int -> greet(name, age) } }
    .with { fetchName() }.with { fetchAge() }.executeGraph()

// Function — annotate with @KapTypeSafe for named builders
@KapTypeSafe
fun buildSummary(name: String, items: Int): String = "$name has $items items"
val g3 = kap(BuildSummary).withName { fetchName() }.withItems { 5 }.executeGraph()
```

#### `Kap.of(value)` / `Kap.empty()` / `Kap.failed(error)` / `Kap.defer { }`

```kotlin
val pure: Kap<Int> = Kap.of(42)                              // pure value
val unit: Kap<Unit> = Kap.empty()                             // Unit computation
val failed: Kap<String> = Kap.failed(RuntimeException("boom")) // wrapped failure
val lazy: Kap<String> = Kap.defer { Kap { expensiveSetup() } } // lazy construction
```

### `.thenValue`

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
    @KapTypeSafe
    data class Page(val content: Content, val sidebar: Sidebar, val timestamp: Timestamp)

    val result = kap(::Page)
        .withContent { fetchContent() }           // parallel
        .withSidebar { fetchSidebar() }           // parallel
        .thenValue { computeTimestamp() }  // sequential fill, no barrier
        .executeGraph()
    ```

### Flow integration

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

#### `Flow.mapEffectOrdered` — Preserve upstream order

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

#### `Flow.firstAsKap()`

=== "Raw Coroutines"

    ```kotlin
    val result: String = userIdFlow.first()
    // Direct, but not composable with other Kap chains
    ```

=== "KAP"

    ```kotlin
    val first: Kap<String> = userIdFlow.firstAsKap()
    val result = first.executeGraph()
    // Composable — can .map, .recover, .timeout, combine with other Kaps
    ```

### Memoization

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
    val a = fetchOnce.executeGraph() // runs the actual call
    val b = fetchOnce.executeGraph() // cached, instant
    ```

=== "KAP — `.memoizeOnSuccess()`"

    ```kotlin
    var callCount = 0
    val fetchOnce = Kap { callCount++; "expensive-result" }.memoizeOnSuccess()

    val a = fetchOnce.executeGraph()  // runs, callCount=1
    val b = fetchOnce.executeGraph()  // cached, callCount still 1
    // If first call FAILS? Not cached. Next call retries.
    ```

### Interop

#### `Deferred.toKap()` / `Kap.toDeferred(scope)`

Bridge between existing coroutine code and KAP. Useful when you have a `Deferred` from a library or legacy code and want to compose it with other `Kap` combinators (`.map`, `.recover`, `.timeout`, parallel `.with` chains, etc.).

```kotlin
val deferred: Deferred<String> = scope.async { fetchUser() }
val kap: Kap<String> = deferred.toKap()
val result = kap.executeGraph()
```

#### `(suspend () -> A).toKap()`

```kotlin
val lambda: suspend () -> String = { fetchUser() }
val kap: Kap<String> = lambda.toKap()
```

#### `computation { }` — Imperative builder

=== "Raw Coroutines"

    ```kotlin
    val result = coroutineScope {
        val user = fetchDashUser()     // sequential
        val cart = fetchDashCart()      // sequential, could be parallel
        "$user has $cart"
    }
    ```

=== "Arrow"

    ```kotlin
    // Arrow's either { } block provides similar monadic composition:
    val result: Either<Throwable, String> = either {
        val user = Either.catch { fetchDashUser() }.bind()
        val cart = Either.catch { fetchDashCart() }.bind()
        "$user has $cart"
    }
    ```

=== "KAP"

    ```kotlin
    val result = computation {
        val user = Kap { fetchDashUser() }.bind()
        val cart = Kap { fetchDashCart() }.bind()
        "$user has $cart"
    }.executeGraph()
    ```

### Observability

```kotlin
@KapTypeSafe
data class Dashboard(val user: String, val config: String)

val tracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

val result = kap(::Dashboard)
    .withUser(Kap { fetchUser() }.traced("fetch-user", tracer))
    .withConfig(Kap { fetchConfig() }.traced("fetch-config", tracer))
    .executeGraph()
```

### Utilities

#### `.keepFirst` / `.keepSecond`

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

=== "Arrow"

    ```kotlin
    val user = parZip(
        { fetchUser() },
        { logAccess() },
    ) { user, _ -> user }   // both run, manually discard second
    ```

=== "KAP"

    ```kotlin
    val user = Kap { fetchUser() }
        .keepFirst(Kap { logAccess() })  // both run, only user returned
        .executeGraph()
    ```

#### `.discard()` / `.peek { }`

```kotlin
val unit = Kap { fetchUser() }.discard().executeGraph()  // runs but returns Unit

val user = Kap { fetchUser() }
    .peek { println("Fetched: $it") }  // side-effect, returns original value
    .executeGraph()
```

#### `.on(context)` / `.named(name)`

```kotlin
val result = Kap { readFile() }
    .on(Dispatchers.IO)          // switch dispatcher
    .named("file-read")          // coroutine name for debugging
    .executeGraph()
```

#### `.executeGraph()` — Execute from any suspend context

```kotlin
suspend fun myFunction(): String {
    return Kap { fetchUser() }.executeGraph()
}
```

#### `delayed(duration, value)` / `withOrNull`

```kotlin
val result = delayed(100.milliseconds, "delayed-value").executeGraph()

val maybeResult: String? = withOrNull { Kap { riskyOperation() } }
```

### Execution model

`Kap<A>` is **lazy** — nothing runs until `.executeGraph()`:

```kotlin
@KapTypeSafe
data class Dashboard(val user: String, val cart: String, val promos: String)

val plan: Kap<Dashboard> = kap(::Dashboard)
    .withUser { fetchDashUser() }
    .withCart { fetchDashCart() }
    .withPromos { fetchDashPromos() }

println("Plan built. Nothing has executed yet.")
println("plan is: ${plan::class.simpleName}")

val result: Dashboard = plan.executeGraph()  // NOW it runs
```

**Key guarantees:**

- **Structured concurrency**: All parallel branches run inside `coroutineScope`. One fails → siblings cancel.
- **Cancellation safety**: `CancellationException` is never caught. All combinators re-throw it.
- **Context propagation**: `.executeGraph(MDCContext())` propagates context to all branches.
- **No reflection**: All type safety is compile-time. Zero runtime overhead.
- **Algebraic laws**: Functor, Applicative, Monad — property-tested via Kotest. See [LAWS.md](https://github.com/damian-rafael-lattenero/kap/blob/master/LAWS.md).

---

## API Reference Map

| I want to... | Use this |
|---|---|
| Run tasks in parallel (named) | `.withParamName { }` via `@KapTypeSafe` |
| Run tasks in parallel (generic) | `.with { }` |
| Wait for all before continuing | `.then { }` / `.thenParamName { }` |
| Use previous result in next phase | `.andThen { ctx -> }` |
| Handle one failure without cancelling rest | `settled { }` |
| Process a list with bounded concurrency | `traverse(concurrency) { }` |
| Retry on failure | `.retry(schedule)` |
| Timeout with fallback | `.timeout(duration) { default }` |
| Recover from errors | `.recover { }` |
| Race multiple sources | `raceN(c1, c2, c3)` |
| Cache computation result | `.memoizeOnSuccess()` |
| Guaranteed resource cleanup | `bracket(acquire, use, release)` |
