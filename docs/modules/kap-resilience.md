# kap-resilience

Retry, resource safety, and protection patterns. All composable in the KAP chain.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-resilience:4.2.0")
```

**Depends on:** `kap-core`.
**Platforms:** JVM, JS (IR), WASM (JS), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 300 tests across 23 test classes (JVM + native), including Schedule semiring laws, Process and Supervisor laws and CircuitBreaker concurrency tests.

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

    val result = Kap { flakyService() }.retry(policy).evalGraph()
    // "success on attempt 3"
    ```

Like every RETRY/CONVERT operator, `retry` runs its operand inside its own `coroutineScope` (Isolation law): a failing child of a nested `traverse`/`with`/`zip` graph is observed as an ordinary failure and retried, instead of cancelling the retry itself — enforced by `IsolationResilienceTest`.

### Building Blocks

| Schedule | Behavior | Example |
|---|---|---|
| `times(n)` | Retry up to N times | `Schedule.times<Throwable>(5)` |
| `spaced(d)` | Fixed delay between retries | `Schedule.spaced(1.seconds)` |
| `exponential(base, max)` | Exponential backoff | `Schedule.exponential(10.milliseconds, max = 5.seconds)` |
| `fibonacci(base)` | Fibonacci-sequence delays | `Schedule.fibonacci(10.milliseconds)` |
| `linear(base)` | Linearly increasing delays | `Schedule.linear(100.milliseconds)` |
| `delays(array/list)` | An explicit sequence of delays, then done (4.3) | `Schedule.delays(arrayOf(100.milliseconds, 2.seconds))` |
| `forever()` | Retry indefinitely (no delay) | `Schedule.forever()` |
| `immediate()` | Same value as `forever()`, named as the semiring identity (5.0) | `Schedule.immediate()` |
| `never()` | Never retry — the additive identity (5.0) | `Schedule.never()` |

### Modifiers

#### `.delayBy { }` — Delay as a function of the input (4.3)

```kotlin
// Longer waits for rate limits than for transient timeouts; stop condition untouched
val policy = Schedule.forever<Throwable>()
    .delayBy { error -> if (error is RateLimited) 30.seconds else 500.milliseconds }
```
`delayBy` is only called when the schedule says Continue; a negative delay fails fast with `IllegalArgumentException`.

#### `.jittered()` — Prevent thundering herd

```kotlin
// Without jitter: 100 clients all retry at exactly 1s, 2s, 4s — thundering herd
// With jitter: each client retries at a random time within the window
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(100.milliseconds).jittered()
```

#### `.withMaxDuration(d)` — Total time cap

`withMaxDuration` is a schedule **member** (it wraps the schedule it is called on, tracking elapsed time internally):

```kotlin
// Retry for at most 30 seconds total, regardless of attempt count
val policy = Schedule.forever<Throwable>() and
    Schedule.exponential(100.milliseconds).withMaxDuration(30.seconds)
```

**Statefulness warning:** a `withMaxDuration` schedule carries its start time. Reuse the same instance across retries and the timer leaks — prefer the `retry(scheduleFactory)` overload for a fresh schedule per invocation.

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

**The schedule semiring (5.0).** `and` is multiplication (both must continue, delays merge with `max`), `or` is addition (either may continue, delays merge with `min`), and `Done` behaves as the delay `+∞`. The laws — associativity and commutativity of both, `never()` as the additive identity, `immediate()` as the multiplicative identity, `never()` as the multiplicative annihilator, distributivity and idempotence — are property-tested in `ScheduleLawsTest` and documented in `LAWS.md`. `Schedule.decideAt(attempt, value)` exposes the decision a driver would take (a schedule is a value, so inspecting it is lawful).

### The quiet window — `Schedule.debounce`

`Schedule.debounce(n, isQuiet)` is the stop condition of a drain: keep going while the work keeps coming, and stop once `n` consecutive inputs have reported quiet. `Schedule.debounce(n)` is the count shorthand — a poll that hands over `0` is quiet.

```kotlin
// Poll every 50 ms until three consecutive polls come back empty
Kap { poll() }
    .repeat(Schedule.spaced<Int>(50.milliseconds) and Schedule.debounce(3))
```

Every `Continue` carries `ZERO`, so the window never touches the cadence: `and` keeps the cadence's delays exactly (`max(d, 0) == d`) and stops when either operand does. The window is a **stop condition, not a clock**.

| Law | Property |
|---|---|
| Stop at the filled window | `Done` exactly when the trailing run of quiet inputs is `>= n` |
| Reset | a non-quiet input resets the run to zero |
| Delay neutrality | every `Continue` carries `ZERO` |
| Empty window | `debounce(0, isQuiet)` ≡ `Schedule.never()` |
| Singleton window | `debounce(1, isQuiet)` ≡ `Schedule.doUntil(isQuiet)` |

Unlike the pointwise factories above, `debounce` is **stateful** (like `withMaxDuration`): the run belongs to the instance, so build a fresh one per run and never share one between drivers. Laws property-tested in `ScheduleDebounceLawsTest`.

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

=== "Arrow"

    ```kotlin
    // Arrow has no retryOrElse equivalent.
    // You can approximate it by catching the error after Schedule exhaustion:
    val result = try {
        Schedule.recurs<Throwable>(2)
            .and(Schedule.spaced(100.milliseconds))
            .retry { flakyService() }
    } catch (e: Throwable) {
        "fallback-after-exhaustion"
    }
    ```

=== "KAP"

    ```kotlin
    val result = Kap { flakyService() }
        .retryOrElse(
            Schedule.times(2) and Schedule.spaced(100.milliseconds)
        ) { "fallback-after-exhaustion" }
        .evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has no retryWithResult equivalent.
    // Schedule.retry does not return attempt count or total delay metadata.
    // You would need to manually track this state around the retry call.
    ```

=== "KAP"

    ```kotlin
    val retryResult = Kap { flakyService() }.retryWithResult(
        Schedule.times<Throwable>(5) and Schedule.exponential(10.milliseconds)
    ).evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has no built-in CircuitBreaker.
    // You would need to implement a state machine manually (similar to Raw Coroutines)
    // or use a third-party library like resilience4j.
    ```

=== "KAP"

    ```kotlin
    val breaker = CircuitBreaker(
        maxFailures = 5,
        retryAfter = 30.seconds,          // open-state cooldown (4.3 name; resetTimeout is the deprecated alias)
        successThreshold = 2,             // two consecutive HalfOpen probes to close (4.3)
        onStateChange = { old, new -> println("CircuitBreaker: $old -> $new") }
    )

    val result = Kap { fetchUser() }
        .withCircuitBreaker(breaker)
        .evalGraph()
    // While Open: fails immediately with CircuitBreakerOpenException
    // After retryAfter: tries one probe (HalfOpen); a failed probe reopens the circuit
    // After successThreshold consecutive probe successes: back to Closed
    ```

### Guarding code that is not a `Kap`

The breaker also exposes a small public suspend API (4.1.0) for protecting plain suspend calls — `tryAcquire()`, `onSuccess()`, `onFailure()`, and the `protect { }` wrapper that does all three:

```kotlin
val user = breaker.protect { fetchUser() }   // throws CircuitBreakerOpenException when open,
                                             // records success/failure, never counts cancellation

// Manual form (what protect does internally):
if (breaker.tryAcquire()) {
    try {
        val u = fetchUser()
        breaker.onSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        breaker.onFailure()
        throw e
    }
}
```

The state-transition bookkeeping (`shouldAttempt`/`recordSuccess`/`recordFailure`) is internal — `withCircuitBreaker`, `protect`, and the tryAcquire/onSuccess/onFailure triad are the supported surface. `breaker.currentState` snapshots the state for observation.

### Full composition

```kotlin
val result = Kap { fetchUser() }
    .timeoutAsFailure(500.milliseconds)           // hard timeout, as an ORDINARY failure
    .withCircuitBreaker(breaker)                  // circuit breaker
    .retry(Schedule.times<Throwable>(3)           // retry with backoff
        and Schedule.exponential(10.milliseconds))
    .recover { "cached-user" }                    // fallback on exhaustion
    .evalGraph()
// timeout -> circuit breaker -> retry -> recover. All composable.
```

!!! warning "timeout(d) is not retryable"
    `timeout(d)` fails with `TimeoutCancellationException` — a `CancellationException`, which every `retry` rethrows by law. Chain `timeout(d)` into `retry(...)` and slow calls are never retried. Use `timeoutAsFailure(d)` (kap-core) whenever a timeout feeds a RETRY operator — it raises an ordinary `KapTimeoutException` instead.

---

## `process` — Supervised Loops (4.2.0)

A `Process` is a long-lived supervised computation (SPEC-5.0, milestone 4.2): the body runs; **normal completion finishes the process**, a failure restarts it according to a `RestartPolicy`, and exhausting the policy fails `await()` with `ProcessExhaustedException`. While alive, a heartbeat beats at its own cadence — independent of the body's progress, so the beat answers "is the process there", never "did the cycle finish".

```kotlin
val poller = Kap { pollOnce() }
    .repeat(Schedule.spaced(500.milliseconds))
    .process(
        name = "tg-poller",
        restart = RestartPolicy.exponential(base = 1.seconds, maxRestarts = 5),
        heartbeatEvery = 5.seconds,
        onHeartbeat = { touchHbFile() },          // external supervisors read the file
        onRestart = { n, err, delay -> log("restart #$n after $err in $delay") },
    )

coroutineScope {
    val handle = poller.startIn(this)   // a child of THIS scope — no daemon escape
    awaitShutdownSignal()
    handle.stop()                        // idempotent; not a failure, never restarts
}
```

The laws (`LAWS.md` — Process laws):

| Law | Guarantee |
|---|---|
| **Convergence** | Every restart waits a strictly positive delay and the count is capped — an instantly-failing body can never hot-loop. The private `RestartPolicy` constructor makes an unlawful policy inexpressible. |
| **Terminal success** | A body that returns finishes the process; no restart, heartbeat stops. |
| **Stop/cancel ≠ failure** | `stop()` and scope death never trigger a restart or exhaustion; `await()` reports `ProcessStoppedException` / the cancellation. |
| **Heartbeat independence** | Beats arrive at the period while the body is mid-cycle or mid-backoff, and stop with the process. |
| **Transparent observation** | `onRestart`/`onHeartbeat` throwing cannot change the lifecycle. |

`handle.restarts` is a `StateFlow<Int>` (collect it for live supervision surfaces), `handle.isAlive` covers both running and waiting-out-a-restart, and `handle.heartbeatAge()` gives the staleness of the alive signal (`Duration.INFINITE` when the heartbeat is disabled with `heartbeatEvery = Duration.ZERO`).

---

## `Supervisor` — Named Supervision (5.0.0)

A `Supervisor` groups named `Kap` bodies and supervises them together. A failure is handled by the **strategy**: `OneForOne` restarts only the failed child (siblings keep running untouched), `AllForOne` restarts every child still in the group. Backoff and the restart cap come from the same `RestartPolicy` as `process`, so the convergence law holds by construction.

```kotlin
val group = Supervisor(
    children = listOf(
        Supervisor.child("bus-tailer") { bus.tail() },
        Supervisor.child("memory-worker") { memory.work() },
        Supervisor.child("bridge") { bridge.relay() },
    ),
    strategy = SupervisionStrategy.OneForOne,
    restartPolicy = RestartPolicy.exponential(base = 1.seconds, maxRestarts = 5),
)

coroutineScope {
    val handle = group.startIn(this)       // children of THIS scope
    handle.restarts.getValue("bridge").collect { println("bridge restarts: $it") }
    awaitShutdownSignal()
    handle.stop()                          // idempotent; never a failure
}
```

The laws (`LAWS.md` — Supervisor laws):

| Law | Guarantee |
|---|---|
| **OneForOne isolation** | Siblings are never cancelled, never restarted, never re-run; their counters stay at zero. |
| **AllForOne totality** | Every non-completed child restarts together; simultaneous failures coalesce into one group restart. |
| **Terminal success** | A child completing normally is finished for good (never resurrected); the group completes when every child has. |
| **Stop/cancel ≠ failure** | Neither restarts anything nor fires exhaustion, whatever the strategy or budget. |
| **Honest exhaustion** | Exactly `maxRestarts` restarts, then `SupervisorExhaustedException` with the child name, the count and the last cause. |
| **Honest liveness** | `isAlive` is true exactly while the group exists (including backoff); counters advance when a restart runs. |

`handle.restarts` is a `Map<String, StateFlow<Int>>` keyed by child name — the honest, live record of what the supervisor did.

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

=== "Arrow"

    ```kotlin
    // Arrow has no timeoutRace equivalent.
    // The closest approximation is the same sequential withTimeout + catch pattern
    // shown in Raw Coroutines. There is no built-in parallel fallback combinator.
    ```

=== "KAP"

    ```kotlin
    val result = Kap { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
        .evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has no raceQuorum equivalent.
    // Arrow provides raceN (2-3 arity) which returns the single fastest result,
    // but has no N-of-M quorum combinator. You would need the manual approach above.
    ```

=== "KAP"

    ```kotlin
    val quorum: List<String> = raceQuorum(
        required = 2,
        Kap { fetchReplicaA() },  // 50ms
        Kap { fetchReplicaB() },  // 20ms
        Kap { fetchReplicaC() },  // 80ms
    ).evalGraph()
    // [replica-B, replica-A] — the 2 fastest. C cancelled.
    ```

Takes any number of computations via vararg, or a collection with `list.raceQuorum(required = 2)`.

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

=== "Arrow"

    ```kotlin
    // Arrow has Resource but not bracket with acquire/use/release in this form.
    // Arrow's Resource monad works differently:
    val db = Resource({ openDbConnection() }, { conn, _ -> conn.close() })
    val cache = Resource({ openCacheConnection() }, { conn, _ -> conn.close() })
    val http = Resource({ openHttpClient() }, { client, _ -> client.close() })

    // Arrow Resource does not natively compose into parallel use like KAP's bracket.
    // Resources are acquired and released sequentially.
    ```

=== "KAP"

    ```kotlin
    val result = Kap.of { db: String -> { cache: String -> { api: String -> "$db|$cache|$api" } } }
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
        .evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has no bracketCase equivalent.
    // Arrow's Resource receives an ExitCase in its release function, but there is no
    // standalone bracketCase combinator. You would need manual try/catch/finally
    // with outcome tracking as shown in Raw Coroutines.
    ```

=== "KAP"

    ```kotlin
    val result = bracketCase(
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
    ).evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has a Resource monad:
    val db = Resource({ openDbConnection() }, { conn, _ -> conn.close() })
    val cache = Resource({ openCacheConnection() }, { conn, _ -> conn.close() })
    val http = Resource({ openHttpClient() }, { client, _ -> client.close() })

    val infra = arrow.fx.coroutines.Resource.zip(db, cache, http)

    infra.use { (db, cache, http) ->
        // use resources — but no built-in parallel composition like KAP
        val dbResult = db.query("SELECT 1")
        val cacheResult = cache.get("user:prefs")
        val httpResult = http.get("/recommendations")
        DashboardData(dbResult, cacheResult, httpResult)
    }
    // Resources released in reverse order on completion or failure.
    ```

=== "KAP"

    ```kotlin
    @KapTypeSafe
    data class DashboardData(val dbResult: String, val cacheResult: String, val httpResult: String)

    val db = Resource({ openDbConnection() }, { it.close() })
    val cache = Resource({ openCacheConnection() }, { it.close() })
    val http = Resource({ openHttpClient() }, { it.close() })

    val infra = Resource.zip(db, cache, http) { d, c, h -> Triple(d, c, h) }

    val result = infra.useKap { (db, cache, http) ->
        kap(::DashboardData)
            .with { dbResult from db.query("SELECT 1") }
            .with { cacheResult from cache.get("user:prefs") }
            .with { httpResult from http.get("/recommendations") }
    }.evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has no guarantee or guaranteeCase equivalent.
    // The closest approach is using Resource or manual try/finally as in Raw Coroutines.
    // Arrow does not provide a standalone finalizer combinator for arbitrary suspend blocks.
    ```

=== "KAP"

    ```kotlin
    // guarantee: finalizer always runs, regardless of success or failure
    val result = Kap { riskyOperation() }
        .guarantee { cleanup() }
        .evalGraph()

    // guaranteeCase: finalizer receives the exit case
    val result2 = Kap { riskyOperation() }
        .guaranteeCase { case ->
            when (case) {
                is ExitCase.Completed<*> -> println("success cleanup")
                is ExitCase.Failed -> println("failure cleanup: ${case.error}")
                is ExitCase.Cancelled -> println("cancellation cleanup")
            }
        }
        .evalGraph()
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

=== "Arrow"

    ```kotlin
    // Arrow has Schedule for retries but lacks CircuitBreaker, timeoutRace, and
    // composable resilience chains. A partial approximation:
    val result = try {
        Schedule.recurs<Throwable>(3)
            .and(Schedule.exponential(50.milliseconds))
            .retry {
                withTimeout(2000) { fetchData() }
            }
    } catch (e: Throwable) {
        cachedData()
    }
    // Missing: CircuitBreaker (needs manual implementation or resilience4j),
    // jitter (not built-in to Arrow Schedule), max duration cap,
    // and composable chaining of timeout + CB + retry + recover.
    ```

=== "KAP"

    ```kotlin
    val breaker = CircuitBreaker(maxFailures = 5, retryAfter = 30.seconds)

    val result = Kap { fetchData() }
        .timeoutAsFailure(2.seconds)                     // hard timeout (retryable failure)
        .withCircuitBreaker(breaker)                     // circuit breaker
        .retry(Schedule.times<Throwable>(3)              // retry with backoff + jitter
            and Schedule.exponential(50.milliseconds)
                .jittered()
                .withMaxDuration(10.seconds))
        .recover { cachedData() }                        // fallback on exhaustion
        .evalGraph()
    ```
