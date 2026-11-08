# Concurrency/FP audit — findings and verdicts (2026-11-08)

The library auditing ITSELF with the same machinery its three consumer repos
ran the same day (bot, agent-brain, whatsdigest-kt). Companion to
[`concurrency-audit.md`](concurrency-audit.md) (U0: 271 suspects across all
library modules' main sources + benchmarks). U1 = two review agents
(concurrency-classification + purity/error-semantics); U2 = the fixes (merged
`6856221`); U3 = this document.

## Score

| U0 category | hits | REAL | lawful/justified |
|---|---|---|---|
| forbidden | 153 | **0 in library mains** | 141 = benchmark JMH rows (the measurement instrument); the rest = KDoc prose and the detekt rule's own fixtures; the one real `while(true)`+`delay` in the repo is the examples' deliberate raw-problem exhibit (now signposted) |
| handrolled | 12 | 0 | SyncCell/Ref/LifecycleMatchers = **LAWFUL-BY-DESIGN** (implementation-internal sync: a library cannot import itself); Memoized's double-checked locking is documented and CE-safe |
| impure | 2 | 0 | a comparison test whose measured SUBJECT is wall-clock (scanner slip pulled it from `benchmarks/src/test` — glob fixed) |
| errors | 104 | **1 REAL + 1 letter** | every other throw is a declared typed edge refusal; every broad catch rethrows CE first; every KSP `!!` proven by construction |

## The REAL findings (merged `6856221`)

1. **`traverseSettled`/`sequenceSettled` violated error-semantics Law 1**:
   bare `runCatching` captured a child's CancellationException (its own
   `timeout()`) into `Result.failure(CE)` — diverging from `Kap.settled()`,
   which rethrows. Fixed via the shared `settledBody` (one law, one
   implementation) + the timeout-fuzz property test that closed the coverage
   gap (CancellationPropertyTest).
2. **Process observer hooks swallowed CE by subclassing** (`catch (_:
   RuntimeException)` catches `CancellationException` ⊂ ISE ⊂ RuntimeException).
   Fixed: CE arm first — the transparency law keeps swallowing hook failures;
   it must never swallow the caller's cancellation.

## The verdict that matters: the cancellation law HOLDS repo-wide

Every error-collecting or failure-counting operator rethrows CE **before**
recording, verified at file:line: `settled` · `recover`/`recoverWith` ·
`retry` (both modules) · `orElse`/`firstSuccessOf` · `breaker` (CE never
counts) · `race`/`raceN`/`raceEither`/`raceQuorum` (racer CE is terminal,
never accumulated) · `catching`/`attempt`/`orThrow` · `bracket` (CE caught
only for `NonCancellable` finalizers, always rethrown) · `repeat` ·
`memoize` · `supervisor` · `process`. **KapStream carries zero catch blocks
in main** — cancellation propagates structurally. LAWS.md statements match
the implementation at every cited line; verify-claims counts are honest
(1234 tests / 92 suites / 144 benchmarks).

## Laws this audit pinned (library checklist)

1. LAWFUL-BY-DESIGN is a distinct verdict: implementation-internal sync
   (SyncCell's AtomicReference, Ref's Mutex) is exempt from the "use kap"
   recursion — a library cannot import itself.
2. Benchmarks are the measurement edge: `runBlocking` at a JMH entry is the
   instrument, not a violation.
3. A `settled`-family operator MUST share one settled-body implementation —
   divergent copies drift into law violations (the R1 root cause).
4. CE extends RuntimeException on the JVM: an observer-hook
   `catch (RuntimeException)` is a cancellation trap; CE arm first.
5. The U0 scanner's globs are part of the audit's truth: `benchmarks/src/**`
   leaked test-side hits; scope `src/jmh` for benchmarks.

## Documented-justified / signposted

- `examples/readme-examples` raw-problem exhibit: the deliberate anti-pattern,
  now carrying the DO-NOT-COPY signpost (and flagged by kap-detekt-rules
  itself — the module that audits its own repository's example).
- KSP `!!` on PSI-model invariants: proven-by-construction (the STAR branch
  precedes); optional future: `logger.error` diagnostics instead of NPE.

## Open candidates (not scheduled)

- More detekt rules from the audit laws (CE-arm-before-catch-all,
  @Volatile-for-RMW) — each is a S-M detekt-rule unit in kap-detekt-rules.
- `BusPipeline`-style typed transport ADT equivalent: none needed — kap has
  no config surface (the consumer repos carry that burden).
