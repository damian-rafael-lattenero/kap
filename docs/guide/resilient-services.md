# Resilient Services

Production services fail. KAP's resilience module gives you composable retry, circuit breaking, timeouts, and resource safety.

!!! note "Requires `kap-resilience`"
    ```kotlin
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.4.0")
    ```

## Composable retry with Schedule

Build retry policies from composable building blocks:

```kotlin
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(10.milliseconds) and
    Schedule.doWhile<Throwable> { it is RuntimeException }

val result = Async {
    Kap { flakyService() }.retry(policy)
}
```

### Building blocks

| Schedule | Behavior |
|---|---|
| `times(n)` | Retry up to N times |
| `spaced(d)` | Fixed delay between retries |
| `exponential(base, max)` | Exponential backoff with optional cap |
| `fibonacci(base)` | Fibonacci-sequence delays |
| `linear(base)` | Linearly increasing delays |
| `forever()` | Retry indefinitely |

### Modifiers

| Modifier | Behavior |
|---|---|
| `.jittered()` | Add random jitter to prevent thundering herd |
| `.withMaxDuration(d)` | Stop after total elapsed time |
| `.doWhile { }` | Continue only while predicate holds |
| `.doUntil { }` | Continue until predicate holds |

### Composition

```kotlin
// Both must agree to continue:
val strict = Schedule.times<Throwable>(3) and Schedule.exponential(100.milliseconds)

// Either can continue:
val lenient = Schedule.times<Throwable>(3) or Schedule.spaced(1.seconds)
```

## Circuit Breaker

Stop calling a degraded service. Auto-recover when it's healthy:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

val result = Async {
    Kap { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
        .recover { "cached-user" }
}
```

State machine: **Closed** (normal) -> **Open** (rejecting, after N failures) -> **HalfOpen** (testing one request after timeout) -> Closed.

## `timeoutRace` — Parallel Fallback

Standard timeout wastes time: wait the full duration, *then* start the fallback. `timeoutRace` starts both immediately:

```kotlin
val result = Async {
    Kap { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
}
```

```
Standard timeout:
t=0ms    ─── primary starts ───
t=100ms  ─── primary times out ───
t=100ms  ─── fallback starts ───     ← wasted 100ms
t=180ms  ─── fallback completes ───

timeoutRace:
t=0ms    ─── primary starts ───┐
t=0ms    ─── fallback starts ──┘     ← both at t=0
t=80ms   ─── fallback wins ───       ← 2.6x faster
```

**JMH verified:** 34.0ms vs sequential 87.2ms.

## `raceQuorum` — N-of-M

3 database replicas. Need 2-of-3 to agree:

```kotlin
val quorum: List<String> = Async {
    raceQuorum(
        required = 2,
        Kap { fetchReplicaA() },
        Kap { fetchReplicaB() },
        Kap { fetchReplicaC() },
    )
}
// Returns the 2 fastest. Third cancelled.
```

## Resource safety with `bracket`

Guarantee cleanup even on failure or cancellation:

```kotlin
val result = Async {
    kap { db: String, cache: String -> "$db|$cache" }
        .with(bracket(
            acquire = { openDb() },
            use = { conn -> Kap { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openCache() },
            use = { c -> Kap { c.get("key") } },
            release = { c -> c.close() },
        ))
}
// Both resources acquired, used in parallel, BOTH released even on failure.
```

## Composable `Resource`

```kotlin
val db = Resource(acquire = { openDb() }, release = { it.close() })
val cache = Resource(acquire = { openCache() }, release = { it.close() })

val result = Resource.zip(db, cache) { d, c -> Pair(d, c) }
    .use { (d, c) ->
        // both open, guaranteed cleanup
        d.query("SELECT 1") + c.get("key")
    }
```

## Full composition

All combinators compose in the chain:

```kotlin
val result = Async {
    Kap { fetchData() }
        .timeout(2.seconds)                    // hard timeout
        .withCircuitBreaker(breaker)           // circuit breaker
        .retry(Schedule.times<Throwable>(3)    // retry with backoff
            and Schedule.exponential(50.milliseconds))
        .recover { cachedData() }              // fallback on exhaustion
}
```

## Try it

```bash
./gradlew :examples:resilient-fetcher:run
./gradlew :examples:full-stack-order:run
```
