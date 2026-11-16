# kap-micrometer

The [`Meter`](kap-core.md#metrics-the-meter-spi) SPI over a Micrometer
`MeterRegistry` — the thin JVM adapter between KAP and any Micrometer backend
(Prometheus, OTLP, CloudWatch, ...).

!!! info "Published"
    Part of the KAP release train; see the CHANGELOG for the first version that
    includes it.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-micrometer:<version>")
```

**Depends on:** `kap-core`, `micrometer-core` (both `api` — the constructor
exposes `MeterRegistry`).
**Platforms:** JVM.
**Tests:** real `SimpleMeterRegistry` and `PrometheusMeterRegistry` (counts,
durations, scrape-time gauges, Prometheus rendering).

---

## Wiring

The adapter lives in `kap.micrometer` (`import kap.micrometer.MicrometerMeter`);
the SPI stays in `kap` (`import kap.Meter`, `kap.Tags`). Decide once at boot,
exactly like any other dependency of the `Meter` SPI:

```kotlin
val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val meter: Meter =
    if (metricsEnabled) MicrometerMeter(registry)
    else Meter.Noop
```

The rest of the application only sees `Meter`, so turning metrics off is a
wiring change, not a code change: `Meter.Noop` runs every timed block bare
with no clock read and zero allocation.

Expose the registry's scrape through your HTTP layer (Ktor, Spring, a lambda):

```kotlin
get("/metrics") { call.respondText(registry.scrape()) }
```

## Naming

Names pass through **verbatim** in Micrometer's dot form. A Prometheus
registry renders them Prometheus-style:

| KAP call | Prometheus |
|---|---|
| `increment("kap.turns.started")` | `kap_turns_started_total` |
| `increment("kap.turns.sealed", Tags.of("outcome" to "failed"))` | `kap_turns_sealed_total{outcome="failed"}` |
| `record("kap.brain.request", duration)` | `kap_brain_request_seconds_count` / `_sum` / `_max` |
| `gauge("kap.outbox.depth", Tags.of("lane" to "main")) { ... }` | `kap_outbox_depth{lane="main"}` |

Tags are **dimensions of the same name**, never part of it: two `outcome`
values are one timer name with two tag sets, not two names. Build `Tags`
values once at wiring time and reuse them on the hot path.

## Counters, timers, gauges

```kotlin
meter.increment("kap.poller.cycles")
meter.increment("kap.handler.duration", Tags.of("verb" to "/tasks"))

// timed operations take the CALL — cancellation still records exactly once
val answer = meter.time("kap.brain.request") { brain.ask(prompt) }
val fetched = meter.timeSuspending("kap.http.get") { client.get(url) }

// a duration measured elsewhere
meter.record("kap.stream.callback", sample)

// gauges are read at scrape time, never cached
meter.gauge("kap.outbox.depth", Tags.of("lane" to "main")) { outbox.depth().toDouble() }
```

`MicrometerMeter` registers gauges with a **strong reference** to the
supplier: Micrometer holds gauge state weakly, and an unreferenced supplier
reads `NaN` after a GC (the documented pitfall). Register each gauge once at
wiring time; the supplier is consulted on every scrape.

## Protecting the measured code

The adapter follows the `Meter` contract and does not throw. If the registry
is external (or you want a hard guarantee around any implementation), wrap it
once with `Meter.guarded`: instrumentation failures are reported as
`MeterFailure(operation, name, tags, error)`, never swallowed and never thrown
into the block. A throwing gauge supplier then reads as `NaN` — the honest
"unknown", not a fabricated zero.

```kotlin
val meter = Meter.guarded(MicrometerMeter(registry)) { failure ->
    log.warn("metrics failure: ${failure.operation} ${failure.name}", failure.error)
}
```

## Testing

Use `SimpleMeterRegistry` for assertions and `PrometheusMeterRegistry` when the
scrape body itself is the contract:

```kotlin
val registry = SimpleMeterRegistry()
val meter = MicrometerMeter(registry)

meter.increment("kap.requests", Tags.of("route" to "/a"))

registry.find("kap.requests").tag("route", "/a").counter()!!.count()  // 1.0
```

The module's own tests pin the adapter laws: names are identities, counters and
timers aggregate exactly, gauges read at scrape time, the SPI's timed paths
record once, and Prometheus renders dot names and tag dimensions.
