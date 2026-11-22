# The denotational map — kap's foundations

ADR, written 22/11 as the sibling of agent-brain's map, after a full census
plus line-by-line read of the hotspots. The method is the same: one sentence
per domain saying what it DENOTES, stripped of implementation — if a change
breaks its sentence, that is a design signal: redesign the denotation, never
patch the code.

The verdict, with receipts: the census came back CLEAN. Every `println`
hotspot lives in `examples/` or `readme-examples/` (demo code — printing is
the demo's job); the library's catches concentrate in `kap-arrow` (the
interop bridge — translating failure families IS its job) and in
`kap-detekt-rules/CancellationArmBeforeCatchAll.kt` (a rule ABOUT catches
necessarily references them). Zero else-if chains in library code. kap is
the doctrine's reference implementation — the laws were distilled FROM it —
and the census shows it. No code debts were found; this map is the whole
deliverable.

## The modules — one sentence each

- **kap-core** (`Kap`, `Combine`, `ZipCombineOverloads`, `KapLike`): a
  denotation of a lazily-evaluated value with applicative structure —
  `Kap<A>` MEANS "a plan that produces an A when run"; `with`/`withV` build
  the plan, `evalGraph()` runs it, rows pick branches and only the winner
  pays.
- **kap-core state** (`Ref`, `SyncCell`, `Isolation`): controlled mutation —
  a `Ref` denotes one variable with an isolation strategy, so the functional
  core stays pure and state lives in named, typed boxes.
- **kap-core edges** (`Combinators`, `WithGuards`, `Repeat`, `Race`): the
  defer law as code — guards before builds, memoize as the once-payment,
  `settled()` as the Result boundary where errors become values.
- **kap-resilience** (`Schedule`, retry, circuit): time as a value — a
  `Schedule` denotes a decision policy (repeat/retry/stop) that composes,
  never a loop that blocks.
- **kap-stream** (`FlowIntegration`): the bridge to Kotlin Flow — a Kap plan
  can denote a stream's lifecycle without the stream leaking into the core.
- **kap-arrow** (`Validated`, `Raise`, `ArrowExtensions`): the vocabulary
  bridge — kap graphs compose with Arrow's `Either`/`Validated`/`Raise`,
  error accumulation stays applicative (the no-monad ruling for validated).
- **kap-ktor** / **kap-micrometer**: the speaking edges — one turns graphs
  into HTTP handlers with translated exceptions, the other into metrics;
  neither leaks inward.
- **kap-ksp** + `kap-ksp-annotations`: `@KapTypeSafe` — the compiler writes
  the builder boilerplate so hand-written code cannot get the shape wrong.
- **kap-kotest**: the laws as property tests — functor/monad/applicative
  identities pinned so refactorings are substitutable.
- **kap-detekt-rules**: the doctrine enforced mechanically — ManualWaitLoop,
  CancellationArmBeforeCatchAll, VolatileReadModifyWrite run on every
  consumer (agent-brain wires them since 5.5.0).

## The homomorphism check

The meaning of a composed plan follows from the meanings of its parts —
that is what the kotest law batteries pin. A failing law is a design signal
in the DENOTATION (what `with` means), never a patch in the machinery.
