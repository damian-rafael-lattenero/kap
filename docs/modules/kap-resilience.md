# kap-resilience

Retry, resource safety, and protection patterns. All composable in the KAP chain.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-resilience:2.3.0")
```

**Depends on:** `kap-core`.
**Platforms:** JVM, JS (IR), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 164 tests across 16 test classes, including Schedule laws and CircuitBreaker concurrency tests.

---

## Schedule — Composable Retry Policies

Build complex retry strategies from simple building blocks. Combine with `and` (both must agree) or `or` (either continues):

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

| Modifier | Behavior |
|---|---|
| `.jittered()` | Add random jitter to prevent thundering herd |
| `.withMaxDuration(d)` | Stop after total elapsed time |
| `.doWhile { }` | Continue while predicate holds |
| `.doUntil { }` | Continue until predicate holds |

### Composition

```kotlin
// Both must agree to continue (intersection):
val strict = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)

// Either can continue (union):
val lenient = Schedule.times<Throwable>(3) or Schedule.spaced(1.seconds)
```

### Retry Variants

#### `.retry(schedule)` — Retry on failure

```kotlin
val result = Async {
    Kap { flakyService() }.retry(
        Schedule.times<Throwable>(3) and Schedule.exponential(50.milliseconds).jittered()
    )
}
```

#### `.retryOrElse(schedule, fallback)` — Fallback after exhaustion

```kotlin
val result = Async {
    Kap { flakyService() }
        .retryOrElse(
            Schedule.times(2) and Schedule.spaced(100.milliseconds)
        ) { "fallback-after-exhaustion" }
}
```

#### `.retryWithResult(schedule)` — Returns RetryResult

```kotlin
val retryResult = Async {
    Kap { flakyService() }.retryWithResult(
        Schedule.times<Throwable>(5) and Schedule.exponential(10.milliseconds)
    )
}
// retryResult.value, retryResult.attempts, retryResult.totalDelay
```

---

## CircuitBreaker

State machine: **Closed** (normal) → **Open** (rejecting after N failures) → **HalfOpen** (testing one request after timeout) → **Closed**.

```kotlin
val breaker = CircuitBreaker(
    maxFailures = 5,
    resetTimeout = 30.seconds,
    onStateChange = { old, new -> println("CircuitBreaker: $old -> $new") }
)

val result = Async {
    Kap { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
        .recover { "cached-user" }
}
// timeout -> circuit breaker -> retry -> recover. All composable.
```

While the breaker is **Open**, calls fail immediately with `CircuitBreakerOpenException` — no network call, no waiting.

---

## `timeoutRace` — Parallel Fallback

Standard timeout wastes time: wait the full duration, *then* start the fallback. `timeoutRace` starts **both at t=0**:

```kotlin
suspend fun fetchFromPrimary(): String { delay(200); return "primary-data" }
suspend fun fetchFromFallback(): String { delay(30); return "fallback-data" }

val result = Async {
    Kap { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
}
// "fallback-data" at ~30ms
```

```
Standard timeout:
t=0ms    ─── primary starts ───
t=100ms  ─── primary times out ───
t=100ms  ─── fallback starts ───     ← wasted 100ms waiting
t=130ms  ─── fallback completes ───

timeoutRace:
t=0ms    ─── primary starts ───┐
t=0ms    ─── fallback starts ──┘     ← both at t=0
t=30ms   ─── fallback wins ───       ← 3x faster
```

**JMH verified:** 34.0ms vs sequential 87.2ms — **2.6x faster**.

---

## `raceQuorum` — N-of-M Successes

3 database replicas. Need 2-of-3 to agree for consistency:

```kotlin
suspend fun fetchReplicaA(): String { delay(50); return "replica-A" }
suspend fun fetchReplicaB(): String { delay(20); return "replica-B" }
suspend fun fetchReplicaC(): String { delay(80); return "replica-C" }

val quorum: List<String> = Async {
    raceQuorum(
        required = 2,
        Kap { fetchReplicaA() },
        Kap { fetchReplicaB() },
        Kap { fetchReplicaC() },
    )
}
// [replica-B, replica-A] — the 2 fastest. C cancelled.
```

Supports arities 2-22.

---

## Resource Safety

### `bracket` — Guaranteed cleanup

Acquire a resource, use it, **guarantee** cleanup even on failure or cancellation:

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
// All 3 acquired, used in parallel, ALL released even on failure.
// Release runs in NonCancellable context — guaranteed.
```

### `bracketCase` — Release depends on outcome

```kotlin
val result = Async {
    bracketCase(
        acquire = { openDbConnection() },
        use = { tx -> Kap { tx.query("INSERT 1") } },
        release = { tx, case ->
            when (case) {
                is ExitCase.Completed<*> -> tx.commit()
                else -> tx.rollback()  // failure or cancellation
            }
            tx.close()
        },
    )
}
```

### `Resource` — Composable resource

Compose resources first, use later. Cleanup order is guaranteed (reverse of acquisition):

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
// All 3 acquired, used in parallel, ALL released in reverse order.
```

`Resource.zip` supports arities 2-22.

### `guarantee` / `guaranteeCase`

```kotlin
val result = Async {
    guarantee(
        fa = { riskyOperation() },
        finalizer = { cleanup() },  // always runs
    )
}
```

---

## Full Composition

All combinators compose in the chain. Here's a production-grade resilient fetch:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

val result = Async {
    Kap { fetchData() }
        .timeout(2.seconds)                              // hard timeout
        .withCircuitBreaker(breaker)                     // circuit breaker
        .retry(Schedule.times<Throwable>(3)              // retry with backoff
            and Schedule.exponential(50.milliseconds)
            .jittered())
        .recover { cachedData() }                        // fallback on exhaustion
}
```
