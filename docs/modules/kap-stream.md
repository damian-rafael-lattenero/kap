# kap-stream

Backpressured streams whose stages are `Kap`s. The backpressure policy is a **named value at every stage**; the default is exact (nothing is ever dropped).

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-stream:5.0.0")
```

**Depends on:** `kap-core`, `kap-resilience` (for `Schedule`).
**Platforms:** JVM, JS (IR), WASM (JS), Linux X64, macOS (x64/ARM64), iOS (x64/ARM64/Simulator).
**Tests:** 28 tests across 2 test classes, all virtual time (no sleeps).
**Laws:** strategy drops, ordering and cancellation are property-tested in `KapStreamLawsTest`.

---

## Why a stream layer over Flow?

Kotlin flows already give you suspension, cancellation and backpressure. `KapStream` adds the two things flows leave implicit:

1. **Backpressure is a value, not a hidden detail.** A slow consumer can buffer, conflate, drop the oldest, drop the incoming or keep the last `n` — and the choice is written at the stage where it applies.
2. **Stages are `Kap`s**, so they compose with the error semantics, isolation law and retry policies of the rest of KAP.

It wraps the runtime (`channelFlow`, `Channel`, structured concurrency); it never reimplements it.

## Sources

```kotlin
// lift a Flow
val users = KapStream.from(userQueries.channelFlow())

// poll a Kap on a schedule's cadence
val readings = KapStream.emitted(Kap { sensor.read() })
    .every(Schedule.spaced<Reading>(1.seconds) and Schedule.doUntil { it == Reading.CLOSED })
```

`emitted(kap).every(schedule)` runs the `Kap` immediately, emits the value, then asks the schedule (with that value as input) whether to continue and how long to wait. `Schedule.times(n)` gives `n + 1` emissions; `Schedule.spaced(d)` polls forever; `doUntil { }` stops on the data.

## Stages

```kotlin
users
    .filterKap { user -> Kap { !user.isDeleted } }          // sequential, order preserved
    .mapKap(concurrency = 8) { user -> Kap { enrich(user) } } // completion order
    .mapKapOrdered(concurrency = 8) { user -> Kap { charge(user) } } // upstream order
    .flatMapKap(concurrency = 4) { id -> loadRelated(id) }   // merges inner streams
    .collectAsKap()
    .evalGraph()
```

| Stage | Order | Notes |
|---|---|---|
| `filterKap { Kap<Boolean> }` | upstream | sequential predicate |
| `mapKap(concurrency)` | completion | `concurrency = 1` is sequential and exact |
| `mapKapOrdered(concurrency)` | upstream | buffers completed results until earlier ones finish |
| `flatMapKap(concurrency) { KapStream }` | completion | merges inner streams, bounded in-flight |

## Backpressure strategies

Every stage takes a `backpressure` parameter; it defaults to `Backpressure.Suspend` (exact). Strategies can also be applied as stages:

```kotlin
source
    .buffer(64)                 // exact, decoupled: the producer waits when full
    .mapKap(concurrency = 4) { value -> Kap { transform(value) } }
    .conflate()                 // keep only the latest while the consumer is busy
    .drainTo(viewerChannel)
```

| Strategy | Drops | Never drops |
|---|---|---|
| `Backpressure.Suspend` (default) | nothing | every element, exact order |
| `Backpressure.Buffer(n)` | nothing — the producer suspends | every element, exact order |
| `Backpressure.Conflate` = `latest(1)` | every superseded element | the element buffered at completion |
| `Backpressure.DropOldest(n)` | the oldest buffered element | the most recent element |
| `Backpressure.DropLatest(n)` | the incoming element | the buffered (older) elements |
| `Backpressure.Latest(n)` | everything older than the last `n` | the last `n` elements |

All strategies preserve the upstream order of the elements they keep, and never duplicate one.

```kotlin
// per-stage naming: the map stage's input uses a 32-slot exact buffer
KapStream.from(events)
    .mapKap(concurrency = 4, backpressure = Backpressure.Buffer(32)) { e -> Kap { handle(e) } }
    .dropLatest(capacity = 8)   // explicitly lossy downstream: freshness of the oldest wins
```

## Sinks

```kotlin
// finite streams: collect everything (exact by construction)
val all: List<Event> = stream.collectAsKap().evalGraph()

// long-lived streams: a backpressured channel sink
with(stream.drainTo(outbox)) { execute() }
```

`drainTo` suspends on the channel's own capacity and owns no lifecycle: the caller closes the channel. `asFlow()` is the escape hatch to plain flows.

## Cancellation

Streams are cold and structured. `collectAsKap()`/`drainTo()` live in the caller's scope, and cancelling that scope cancels the collection through every channel boundary back to the source — proven in `KapStreamTest` under virtual time.

```kotlin
withTimeout(5.seconds) {
    with(KapStream.emitted(Kap { poll() }).every(Schedule.spaced(500.milliseconds)).drainTo(sink)) { execute() }
}
// the timeout cancels the poll loop; the source's finally blocks run
```

## Performance

JMH, n = 1,000,000 elements, Apple M1 / Corretto 25 (see
`benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` for the exact
commands and the annotated table). Average time in ms:

| Pipeline | Raw Flow | KapStream (suspend) | RxJava 3 |
|---|---|---|---|
| map | 4.7 | 14.3 | 2.1 |
| filter | 4.6 | 11.2 | 1.3 |
| flatMap (concurrency 16) | 236 | 241 | 57 |

The suspend default is **1.02x** raw Flow for flatMap. For trivial `map`/`filter`
bodies the per-element Kap allocation dominates a ~5 ns baseline: the honest
comparator is a suspend call per element (10.3 ms for the same map), against
which the stage is **1.39x**. Lossy strategies trade exactness for throughput;
the exact `buffer` boundary costs a channel handoff per element (~67 ms), so
use it only when producer/consumer decoupling is the point.
