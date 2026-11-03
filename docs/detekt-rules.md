# Detekt rules

`kap-detekt-rules` is a JVM-only tooling module: it packages Detekt rule sets
that enforce KAP's concurrency doctrine. It is not a runtime dependency and it
is not part of the published library artifacts.

## ManualWaitLoop

Flags a hand-rolled wait loop: a `while (true)` / `while(true)` whose body
waits with `kotlinx.coroutines.delay(...)` or `Thread.sleep(...)`.

The doctrine forbids polling loops as control flow. A cadence is a value — a
`Schedule` — interpreted by `repeat`/`retry`, so the delay, the jitter, the
stop condition and cancellation stay policy instead of code.

```kotlin
// noncompliant — the cadence lives in the loop body
while (true) {
    val job = api.poll()
    if (job != null) break
    delay(1.seconds)
}

// compliant — the cadence is a Schedule value
Kap { api.poll() }
    .repeatWhile { it == null }
    .retry(Schedule.spaced(1.seconds))
```

`delay` is only matched when it is the coroutines delay (imported from
`kotlinx.coroutines`, star-imported, or fully qualified); a same-named local
function is left alone. `Thread.sleep` is matched on `Thread` /
`java.lang.Thread`. The rule is syntactic and needs no type resolution.

### Dependency

```kotlin
// build.gradle.kts
dependencies {
    detektPlugins("io.github.damian-rafael-lattenero:kap-detekt-rules:4.2.0")
}
```

The module is built from source in this repository and is not published to
Maven Central yet; until it is, point `detektPlugins` at the local jar (or a
project/`includeBuild` dependency).

### Configuration

```yaml
# detekt.yml
kap:
  ManualWaitLoop:
    active: true
```

The rule is not enabled on KAP's own build; the ruleset ships for consumers.
Suppress a justified exception at the declaration with
`@Suppress("ManualWaitLoop")`.
