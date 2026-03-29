# kap-resilience

Retry, resource safety, and protection patterns. All composable in the KAP chain.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-resilience:2.4.0")
```

**Depends on:** `kap-core`.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 164 tests across 16 test classes, including Schedule laws and CircuitBreaker concurrency tests.

---

## Schedule — Composable Retry Policies

=== "Raw Coroutines (~20 lines)"

    ```kotlin
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int,
        initialDelay: Long,
        maxDelay: Long,
        factor: Double,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e !is RuntimeException) throw e  // only retry RuntimeException?
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block() // last attempt, let it throw
    }

    val result = retryWithBackoff(5, 10, 5000, 2.0) { flakyService() }
    // Want jitter? Rewrite. Want max duration? Rewrite. Want to compose two policies? Rewrite.
    ```

=== "Arrow"

    ```kotlin
    val result = Schedule.recurs<Throwable>(5)
        .and(Schedule.exponential(10.milliseconds))
        .retry { flakyService() }
    ```

=== "KAP"

    ```kotlin
    val policy = Schedule.times<Throwable>(5) and
        Schedule.exponential(10.milliseconds) and
        Schedule.doWhile<Throwable> { it is RuntimeException }

    var attempts = 0
    suspend fun flakyService(): String {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        return "success on attempt $attempts"
    }

    val result = Async {
        Kap { flakyService() }.retry(policy)
    }
    // "success on attempt 3"
    ```

### Building Blocks

| Schedule | Behavior | Example |
|---|---|---|
| `times(n)` | Retry up to N times | `Schedule.times<Throwable>(5)` |
| `spaced(d)` | Fixed delay between retries | `Schedule.spaced(1.seconds)` |
| `exponential(base, max)` | Exponential backoff | `Schedule.exponential(10.milliseconds, maxDelay = 5.seconds)` |
| `fibonacci(base)` | Fibonacci-sequence delays | `Schedule.fibonacci(10.milliseconds)` |
| `linear(base)` | Linearly increasing delays | `Schedule.linear(100.milliseconds)` |
| `forever()` | Retry indefinitely | `Schedule.forever()` |

### Modifiers

#### `.jittered()` — Prevent thundering herd

```kotlin
// Without jitter: 100 clients all retry at exactly 1s, 2s, 4s — thundering herd
// With jitter: each client retries at a random time within the window
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(100.milliseconds).jittered()
```

#### `.withMaxDuration(d)` — Total time cap

```kotlin
// Retry for at most 30 seconds total, regardless of attempt count
val policy = Schedule.forever<Throwable>() and
    Schedule.exponential(100.milliseconds) and
    Schedule.withMaxDuration(30.seconds)
```

#### `.doWhile { }` / `.doUntil { }` — Conditional retry

```kotlin
// Only retry on transient errors
val policy = Schedule.times<Throwable>(10) and
    Schedule.doWhile<Throwable> { it is IOException || it is TimeoutException }

// Retry until we get a non-empty response
val untilReady = Schedule.spaced<String>(1.seconds) and
    Schedule.doUntil<String> { it.isNotEmpty() }
```

### Composition

```kotlin
// Both must agree to continue (intersection):
val strict = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)

// Either can continue (union):
val lenient = Schedule.times<Throwable>(3) or Schedule.spaced(1.seconds)
```

### Retry Variants

#### `.retryOrElse(schedule, fallback)` — Fallback after exhaustion

=== "Raw Coroutines"

    ```kotlin
    var result: String? = null
    var lastError: Exception? = null
    for (attempt in 0..2) {
        try {
            result = flakyService()
            break
        } catch (e: Exception) {
            lastError = e
            if (attempt < 2) delay(100)
        }
    }
    val finalResult = result ?: "fallback-after-exhaustion"
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { flakyService() }
            .retryOrElse(
                Schedule.times(2) and Schedule.spaced(100.milliseconds)
            ) { "fallback-after-exhaustion" }
    }
    ```

#### `.retryWithResult(schedule)` — Returns full context

=== "Raw Coroutines"

    ```kotlin
    var attempts = 0
    var totalDelay = 0L
    var currentDelay = 10L
    var value: String? = null
    for (attempt in 0..5) {
        attempts++
        try {
            value = flakyService()
            break
        } catch (e: Exception) {
            if (attempt < 5) {
                delay(currentDelay)
                totalDelay += currentDelay
                currentDelay *= 2
            } else throw e
        }
    }
    println(value)       // "success"
    println(attempts)    // 3
    println(totalDelay)  // 30ms (manual — no standard tracking)
    ```

=== "KAP"

    ```kotlin
    val retryResult = Async {
        Kap { flakyService() }.retryWithResult(
            Schedule.times<Throwable>(5) and Schedule.exponential(10.milliseconds)
        )
    }
    println(retryResult.value)       // "success"
    println(retryResult.attempts)    // 3
    println(retryResult.totalDelay)  // 70ms
    ```

---

## CircuitBreaker

=== "Raw Coroutines"

    ```kotlin
    // Manual state machine — 50+ lines
    class ManualCircuitBreaker(
        private val maxFailures: Int,
        private val resetTimeout: Duration,
    ) {
        private val mutex = Mutex()
        private var failures = 0
        private var state: State = State.Closed
        private var lastFailure: Long = 0

        enum class State { Closed, Open, HalfOpen }

        suspend fun <T> execute(block: suspend () -> T): T {
            mutex.withLock {
                when (state) {
                    State.Open -> {
                        if (System.currentTimeMillis() - lastFailure > resetTimeout.inWholeMilliseconds) {
                            state = State.HalfOpen
                        } else {
                            throw RuntimeException("Circuit breaker is open")
                        }
                    }
                    else -> { }
                }
            }
            return try {
                val result = block()
                mutex.withLock { failures = 0; state = State.Closed }
                result
            } catch (e: Exception) {
                mutex.withLock {
                    failures++
                    lastFailure = System.currentTimeMillis()
                    if (failures >= maxFailures) state = State.Open
                }
                throw e
            }
        }
    }
    ```

=== "KAP"

    ```kotlin
    val breaker = CircuitBreaker(
        maxFailures = 5,
        resetTimeout = 30.seconds,
        onStateChange = { old, new -> println("CircuitBreaker: $old -> $new") }
    )

    val result = Async {
        Kap { fetchUser() }
            .withCircuitBreaker(breaker)
    }
    // While Open: fails immediately with CircuitBreakerOpenException
    // After resetTimeout: tries one request (HalfOpen)
    // If it succeeds: back to Closed
    ```

### Full composition

```kotlin
val result = Async {
    Kap { fetchUser() }
        .timeout(500.milliseconds)                    // hard timeout
        .withCircuitBreaker(breaker)                  // circuit breaker
        .retry(Schedule.times<Throwable>(3)           // retry with backoff
            and Schedule.exponential(10.milliseconds))
        .recover { "cached-user" }                    // fallback on exhaustion
}
// timeout -> circuit breaker -> retry -> recover. All composable.
```

---

## `timeoutRace` — Parallel Fallback

=== "Raw Coroutines"

    ```kotlin
    // Sequential: waste time waiting for timeout before starting fallback
    val result = try {
        withTimeout(100) { fetchFromPrimary() }
    } catch (e: TimeoutCancellationException) {
        fetchFromFallback()  // starts AFTER 100ms timeout
    }
    // Total: 100ms (wasted) + fallback time
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        Kap { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
    }
    // Both start at t=0. Fallback wins at ~30ms. Primary cancelled.
    ```

```
Sequential timeout:
t=0ms    ─── primary starts ───
t=100ms  ─── timeout fires ───
t=100ms  ─── fallback starts ───     ← 100ms wasted
t=130ms  ─── fallback completes ───

timeoutRace:
t=0ms    ─── primary starts ───┐
t=0ms    ─── fallback starts ──┘     ← both at t=0
t=30ms   ─── fallback wins ───       ← 3x faster
```

**JMH verified:** 34.0ms vs sequential 87.2ms — **2.6x faster**.

---

## `raceQuorum` — N-of-M Successes

=== "Raw Coroutines"

    ```kotlin
    // Manual select + counting — fragile, hard to get right
    val results = mutableListOf<String>()
    val required = 2
    coroutineScope {
        val jobs = listOf(
            async { fetchReplicaA() },
            async { fetchReplicaB() },
            async { fetchReplicaC() },
        )
        val channel = Channel<String>(3)
        jobs.forEach { job ->
            launch { try { channel.send(job.await()) } catch (_: Exception) { } }
        }
        repeat(required) { results.add(channel.receive()) }
        jobs.forEach { it.cancel() }  // cancel the rest
    }
    ```

=== "KAP"

    ```kotlin
    val quorum: List<String> = Async {
        raceQuorum(
            required = 2,
            Kap { fetchReplicaA() },  // 50ms
            Kap { fetchReplicaB() },  // 20ms
            Kap { fetchReplicaC() },  // 80ms
        )
    }
    // [replica-B, replica-A] — the 2 fastest. C cancelled.
    ```

Supports arities 2-22.

---

## Resource Safety

### `bracket` — Guaranteed cleanup

=== "Raw Coroutines"

    ```kotlin
    // Nested try/finally — gets ugly fast with multiple resources
    val db = openDbConnection()
    try {
        val cache = openCacheConnection()
        try {
            val http = openHttpClient()
            try {
                // use all three... but sequentially acquired
                val dbResult = db.query("SELECT 1")
                val cacheResult = cache.get("key")
                val httpResult = http.get("/api")
                "$dbResult|$cacheResult|$httpResult"
            } finally {
                http.close()
            }
        } finally {
            cache.close()
        }
    } finally {
        db.close()
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
            .with(bracket(
                acquire = { openDbConnection() },
                use = { conn -> Kap { conn.query("SELECT 1") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openCacheConnection() },
                use = { conn -> Kap { conn.get("key") } },
                release = { conn -> conn.close() },
            ))
            .with(bracket(
                acquire = { openHttpClient() },
                use = { client -> Kap { client.get("/api") } },
                release = { client -> client.close() },
            ))
    }
    // All 3 acquired, used in PARALLEL, ALL released even on failure.
    // Release runs in NonCancellable context — guaranteed.
    ```

### `bracketCase` — Release depends on outcome

=== "Raw Coroutines"

    ```kotlin
    val conn = openDbConnection()
    var succeeded = false
    try {
        val result = conn.query("INSERT 1")
        succeeded = true
        result
    } catch (e: Exception) {
        conn.rollback()
        throw e
    } finally {
        if (succeeded) conn.commit()
        conn.close()
    }
    ```

=== "KAP"

    ```kotlin
    val result = Async {
        bracketCase(
            acquire = { openDbConnection() },
            use = { tx -> Kap { tx.query("INSERT 1") } },
            release = { tx, case ->
                when (case) {
                    is ExitCase.Completed<*> -> { println("commit"); tx.commit() }
                    is ExitCase.Failed -> { println("rollback"); tx.rollback() }
                    is ExitCase.Cancelled -> { println("rollback (cancelled)"); tx.rollback() }
                }
                tx.close()
            },
        )
    }
    ```
```

### `Resource` — Composable resource

=== "Raw Coroutines"

    ```kotlin
    // Manual acquisition + cleanup ordering
    val db = openDbConnection()
    val cache = openCacheConnection()
    val http = openHttpClient()
    try {
        // use them...
    } finally {
        http.close()   // reverse order? you have to remember
        cache.close()
        db.close()
    }
    ```

=== "KAP"

    ```kotlin
    val db = Resource({ openDbConnection() }, { it.close() })
    val cache = Resource({ openCacheConnection() }, { it.close() })
    val http = Resource({ openHttpClient() }, { it.close() })

    val infra = Resource.zip(db, cache, http) { d, c, h -> Triple(d, c, h) }

    val result = Async {
        infra.useKap { (db, cache, http) ->
            kap(::DashboardData)
                .with { db.query("SELECT 1") }
                .with { cache.get("user:prefs") }
                .with { http.get("/recommendations") }
        }
    }
    // All acquired, used in parallel, released in reverse order. Guaranteed.
    ```

`Resource.zip` supports arities 2-22.

### `guarantee` / `guaranteeCase`

=== "Raw Coroutines"

    ```kotlin
    // guarantee: try/finally is all you get
    val result = try {
        riskyOperation()
    } finally {
        cleanup()
    }

    // guaranteeCase: manual outcome tracking
    var outcome: String = "cancelled"
    val result2 = try {
        val r = riskyOperation()
        outcome = "success"
        r
    } catch (e: Exception) {
        outcome = "failure: ${e.message}"
        throw e
    } finally {
        when {
            outcome.startsWith("success") -> println("success cleanup")
            outcome.startsWith("failure") -> println("failure cleanup: $outcome")
            else -> println("cancellation cleanup")
        }
    }
    ```

=== "KAP"

    ```kotlin
    // guarantee: finalizer always runs, regardless of success or failure
    val result = Async {
        guarantee(
            fa = { riskyOperation() },
            finalizer = { cleanup() },
        )
    }

    // guaranteeCase: finalizer receives the exit case
    val result2 = Async {
        guaranteeCase(
            fa = { riskyOperation() },
            finalizer = { case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("success cleanup")
                    is ExitCase.Failed -> println("failure cleanup: ${case.error}")
                    is ExitCase.Cancelled -> println("cancellation cleanup")
                }
            },
        )
    }
    ```

---

## Full Production Pipeline

All features composed in one chain:

=== "Raw Coroutines"

    ```kotlin
    val maxFailures = 5
    var failures = 0
    var cbState = "closed"
    var cbLastFailure = 0L

    suspend fun fetchWithResilience(): String {
        // Circuit breaker check
        if (cbState == "open") {
            if (System.currentTimeMillis() - cbLastFailure > 30_000) {
                cbState = "half-open"
            } else {
                throw RuntimeException("Circuit breaker is open")
            }
        }

        // Retry loop with exponential backoff + jitter
        var lastException: Exception? = null
        var currentDelay = 50L
        val startTime = System.currentTimeMillis()

        for (attempt in 0..3) {
            if (System.currentTimeMillis() - startTime > 10_000) break // max duration

            try {
                // Timeout
                val result = withTimeout(2000) { fetchData() }

                // Circuit breaker success
                failures = 0
                cbState = "closed"
                return result
            } catch (e: Exception) {
                lastException = e

                // Circuit breaker failure tracking
                failures++
                cbLastFailure = System.currentTimeMillis()
                if (failures >= maxFailures) cbState = "open"

                if (attempt < 3) {
                    // Jittered delay
                    val jitter = (currentDelay * Math.random()).toLong()
                    delay(currentDelay + jitter)
                    currentDelay *= 2
                }
            }
        }

        // Fallback after exhaustion
        return cachedData()
    }
    ```

=== "KAP"

    ```kotlin
    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

    val result = Async {
        Kap { fetchData() }
            .timeout(2.seconds)                              // hard timeout
            .withCircuitBreaker(breaker)                     // circuit breaker
            .retry(Schedule.times<Throwable>(3)              // retry with backoff + jitter
                and Schedule.exponential(50.milliseconds)
                .jittered()
                .withMaxDuration(10.seconds))
            .recover { cachedData() }                        // fallback on exhaustion
    }
    ```
