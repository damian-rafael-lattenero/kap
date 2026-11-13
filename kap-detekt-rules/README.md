# kap-detekt-rules

Detekt rules that enforce the kap doctrine as a **mechanical gate**: no
hand-rolled wait loops, no busy-waiting as control flow — a cadence is a
`Schedule` value interpreted by `Kap.repeat` / `KapStream`, never a `while`;
and no swallowed cancellation — every broad catch arms a
`CancellationException` rethrow first. Born from the 2026-11-08 concurrency
audits of the three consumer repos, where the same defect classes (laundered
cancellation, bounded-but-scary `while(true)`s, CE-eating catch-alls) kept
escaping review checklists.

## Install

```kotlin
dependencies {
    detektPlugins("io.github.damian-rafael-lattenero:kap-detekt-rules:5.2.0")
}
```

The ruleset registers itself through
`META-INF/services/dev.detekt.api.RuleSetProvider` — no manual wiring. The
detekt API rides `compileOnly`, so the jar never bundles the API it implements.

## Configure

```yaml
kap:
  ManualWaitLoop:
    active: true
  CancellationArmBeforeCatchAll:
    active: true
  VolatileReadModifyWrite:
    active: true
```

## Rules

| Rule | Flags | Points to |
|---|---|---|
| `ManualWaitLoop` | `while (true)` / `do { } while (true)` whose body waits with `delay(...)` or a blocking sleep | `Kap.repeat(schedule)`, `KapStream`, `Schedule` — the declarative replacements |
| `CancellationArmBeforeCatchAll` | a broad catch (`Exception` / `RuntimeException` / `Throwable`) with no earlier `CancellationException` clause, no inline `is CancellationException` rethrow, and no unconditional rethrow — a CE-eating catch-all | the sibling `catch (e: CancellationException) { throw e }` arm — cancellation must always propagate |
| `VolatileReadModifyWrite` | a `@Volatile` property used as a read-modify-write accumulator (`x++`, `x += n`, `x = x + 1`, `x = f(x)`) — a shared cell that races and loses writes | `kap.Ref` (suspend) or `Ref.sync` / `SyncCell` (lock-free atomic) — never a `@Volatile` RMW cell |

## Verify locally

```bash
./gradlew :kap-detekt-rules:build
./gradlew :kap-detekt-rules:publishToMavenLocal -PskipSigning
```
