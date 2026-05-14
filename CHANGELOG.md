# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [3.0.0] - 2026-05-14

### Breaking
- **Step-class API removed entirely.** `kapDsl(...)`, `.withX { }`, `.thenX { }`, `XxxStep0/Step1/...` are no longer generated. The single supported entry point is `kap(::T)` (or `kap${FunctionName}(::fn)` for functions with colliding signatures), which returns a **scoped wrapper `${T}Kap<F>`** with per-slot tag interfaces.
- **Infix verb renamed: `eq` → `from`.** Search-and-replace `eq` → `from` inside `.with { … }` / `.then { … }` blocks. Reads more naturally as prose: `.with { user from fetchUser() }`.
- **`.with(field eq Kap { ... })` parens form is now `.with(WrapperKap.field from Kap { ... })`** — the tag val lives on the wrapper's companion object so it's accessible outside the slot lambda.
- **Marker objects are no longer generated for `@KapTypeSafe` functions.** Use `kap(::functionName)` directly. If two `@KapTypeSafe` functions share the same `(params) -> return` signature, the processor falls back to `kap${FunctionName}(::fn)` to disambiguate.
- **`@KapTypeSafe(prefix = "...")` semantics narrowed** — `prefix` now only affects generated **file names and tag class names**, not call-site method or tag names. The call site is always `.with { paramName from value }` using the original parameter name.

### Added
- **Per-slot interfaces `${BaseName}${Field}Slot`** — the lambda receiver of `.with { … }` / `.then { … }` exposes exactly one tag (`fieldName: ${BaseName}${Field}Tag`). The IDE narrows autocomplete to that single member; typing any other field is a compile error citing the expected tag.
- **Companion mirrors of tag vals** — `${BaseName}Kap.field` is accessible from anywhere, enabling the parens form `.with(${BaseName}Kap.field from Kap { ... })` for Kap-decorated values without entering the slot lambda.
- **Infix `from` with `Kap<T>` overload** — `WrapperKap.field from Kap { ... }.timeout(…)` keeps combinators inside the graph instead of escaping via `.evalGraph()` per slot.
- **`.asKap` member property** — `${BaseName}Kap<F>.asKap: Kap<F>` (via `KapLike<F>`) unwraps the scoped wrapper to a plain `Kap<F>` for external APIs or type-annotation conformance.
- **Last slot returns `Kap<R>` directly** — when the curry has reduced to the final parameter, `.with { lastField from … }` / `.then { lastField from … }` return `Kap<ReturnType>` instead of the wrapper. Chains inside `andThen { kap(::X)… }`, `bracketCase use { … }`, or followed by kap-core operators (`.map`, `.recover`, `.timeout`, …) no longer need `.asKap` at the end.
- **`KapLike<F>` marker interface in kap-core** — generated wrappers implement it; kap-core ships delegate extensions (`.map`, `.recover`, `.recoverWith`, `.timeout`, `.settled`, `.memoize`, `.timed`, `.andThen`, `.evalGraph`) that delegate to `asKap`. The wrapper is operatively interchangeable with `Kap<F>` for all kap-core operators, without extending the `Kap` hierarchy and without K2 overload conflicts.
- **`kapV<E>(::T)` auto-emitted for every `@KapTypeSafe` class when kap-arrow is in the classpath** — generates `${T}ValidatedKap<E, F>` with slot-narrowed `.withV { field from validate() }` / `.thenV { field from … }` operators, sharing the same slot interfaces as `kap(::T)`. The infix `from` gets a third overload accepting `Either<NonEmptyList<E>, FieldType>`, emitted in a separate `${T}KapBuilderValidated.kt` file. Migration: `kapV<E, P1, …, Pn, R>(::T).withV { fn() }` → `@KapTypeSafe data class T(…); kapV<E>(::T).withV { field from fn() }`.

### Migration guide
```kotlin
// Before (2.x step-class)
kap(::CheckoutResult)
    .withUser { fetchUser() }
    .withCart { fetchCart() }
    .thenStock { validateStock() }
    .evalGraph()

// After (3.0 scoped wrapper)
kap(::CheckoutResult)
    .with { user from fetchUser() }
    .with { cart from fetchCart() }
    .then { stock from validateStock() }
    .evalGraph()

// Kap-decorated values: parens + companion-qualified tag
kap(::CheckoutResult)
    .with(CheckoutResultKap.user from Kap { fetchUser() }.timeout(2.seconds))
    .with { cart from fetchCart() }
    .evalGraph()

// Inside .andThen returning another kap(::X)... chain — end with .asKap
kap(::UserContext)
    .with { profile from fetchProfile() }
    .with { tier from fetchTier() }
    .andThen { ctx ->
        kap(::PersonalizedDashboard)
            .with { recs from fetchRecs(ctx.profile) }
            .with { promos from fetchPromos(ctx.tier) }
            .asKap                            // ← drop wrapper to Kap<…>
    }
    .evalGraph()
```

## [Unreleased]


## [2.7.0] - 2026-04-04

### Breaking
- **`executeGraph()` renamed to `evalGraph()`** — shorter, clearer. Search-and-replace: `executeGraph` → `evalGraph`

### Added
- **`timed { }` top-level shorthand** — like `settled { }`, wraps a call to return `TimedResult<A>` with wall-clock duration
- **Extension properties for opaque types** — KSP generates `val String.firstNameUser: UserFirstName` for fluent `kapTyped` usage
- **Blog post: "I Replaced 90 Lines of Coroutine Spaghetti with 35"** — visceral storytelling for r/Kotlin

### Changed
- **Progressive disclosure README** — rewritten with "The problem" → "Start simple" → "What if one call fails?" → "The full picture" narrative
- README uses `Kap { }` SAM syntax exclusively (no `.toKap()`)
- Added Spring Boot example to "Works with your stack"
- Added real HTTP example (GitHub API) to superpowers section
- Added GitHub stars badge
- Updated docs site landing page with storytelling narrative
- LAWS.md: renamed `Effect` → `Kap`

## [2.6.0] - 2026-04-04

### Added
- **`timed { }` top-level shorthand** — like `settled { }`, wraps a call to return `TimedResult<A>` with wall-clock duration
- **Progressive disclosure README** — rewritten for open-source pitch with "The problem" → "Start simple" → "What if one call fails?" → "The full picture" narrative
- **Full placeOrder showcase** — realistic example combining `kapV`, `raceN`, `retry`, `CircuitBreaker`, `settled`, and `bracketCase` with raw coroutines comparison
- **"Composable superpowers" section** — standalone examples for `settled {}`, `raceN`, `traverse(concurrency)`, `timeoutRace`, `timed {}`, `memoizeOnSuccess`
- **All README examples verified** — every code block has a compilable, runnable function in readme-examples

### Changed
- README uses `Kap { }` SAM syntax exclusively (no `.toKap()`)
- README uses single `evalGraph()` per graph via `Kap<A>` overloads on `.withX()` / `.thenX()`
- Updated docs/index.md tagline and API-at-a-glance to match README narrative
- Added `timed { }` documentation to kap-core module docs

## [2.5.0] - 2026-03-29

### Added
- **`settled { }` top-level function** — shorthand for `Kap { }.settled()`, reads immediately
- **Native test coverage** for kap-resilience (ScheduleTest, CircuitBreakerTest, ResourceTest) — contributed by @guilherme-dionysio
- **38 Arrow comparison tabs** across all KDocs (every tabbed section now has Raw/Arrow/KAP)
- **36 new verified examples** in readme-examples (69 total, 100% API coverage)

### Changed
- KSP processor generates `data class` instead of `@JvmInline value class` — now fully multiplatform
- Documentation restructured: Guides + Modules merged into single "KDocs" tab
- Comparison page rewritten with 4 triple-tab code comparisons
- Cookbook rewritten with complete self-contained examples
- Quickstart rewritten with copy-paste-runnable code (no `/* ... */` comments)
- `.settled()` docs rewritten with progressive reveal (problem → solution)

### Fixed
- MkDocs CI pinned to stable versions (pygments compatibility)

## [2.4.0] - 2026-03-27

### Added
- **`kap-ksp`** — KSP2 processor for compile-time safe same-type parameters
  - `@KapTypeSafe` annotation generates value class wrappers per parameter
  - Works on data classes and functions
  - `prefix` parameter to avoid collisions
  - `.toParamName()` extension functions for fluent wrapping
- **`kap-ktor`** — Ktor server integration plugin
  - `Kap` plugin with circuit breaker registry and shared tracer
  - `respondAsync` / `respondKap` extensions for routing
  - `kapExceptionHandlers()` for StatusPages (503, 504, 400)
  - `ktorTracer()` / `structuredTracer()` for observability
- **`kap-kotest`** — Test matchers and utilities
  - `shouldSucceedWith`, `shouldSucceed`, `shouldFailWith`, `shouldFailWithMessage`
  - `shouldBeMillis`, `shouldBeAtMostMillis`, `shouldProveParallel` (virtual-time)
  - `shouldBeClosed/Open/HalfOpen`, `CircuitBreakerTracker` (resilience)
  - `shouldBeRight/Left`, `shouldHaveErrors`, `shouldContainError` (Arrow)
  - `LifecycleTracker` for resource lifecycle assertions
- **`kap-ksp-annotations`** — Multiplatform annotation module for `@KapTypeSafe`
- **WASM target** (`wasmJs`) for kap-core and kap-resilience
- MkDocs Material documentation site with full API coverage
- Blog section at `/blog/`
- Migration guides: "Coming from Arrow" and "Coming from Raw Coroutines"
- CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md, CHANGELOG.md
- Issue templates, PR template, GitHub Discussions
- `kotlinx-binary-compatibility-validator` for API stability
- 8 good first issues for contributors

### Changed
- README rewritten — 769 lines → 176 lines with triple comparison (Raw/Arrow/KAP)
- Logo redesigned — Pac-Man K with Kotlin eye and `.with` `.then` `.andThen` syntax
- Renamed package `applicative` to `kap`, type `Effect` to `Kap`
- Updated all references to new repo URL `github.com/damian-rafael-lattenero/kap`

## [2.3.0] - 2025-12-01

### Changed
- Renamed core API for mainstream adoption (`Effect` -> `Kap`, idiomatic naming)
- Rewrote README — cut 60%, front-loaded pain/payoff/quickstart
- Eliminated all `@Suppress("UNCHECKED_CAST")` and `@Suppress("UNREACHABLE_CODE")` from production code

### Fixed
- JMH benchmark compilation and warnings
- Version centralized to single source of truth for CI compatibility

## [2.2.0] - 2025-11-01

### Changed
- Renamed public API to JVM-idiomatic names
- Radical README rewrite for adoption conversion
- Added `readme-examples` project — every code snippet compiled and verified
- Comprehensive Ktor integration example with 28 tests

### Added
- Benchmark dashboard badge and link
- `readme-examples` project verifying all README code in CI

## [2.1.0] - 2025-10-01

### Changed
- Major dependency upgrades:
  - Kotlin 2.3.20
  - kotlinx-coroutines 1.10.2
  - Dokka 2.1.0
  - Arrow 2.1.2
- Migrated to Gradle version catalog (`libs.versions.toml`)
- Bumped all CI actions to latest versions

## [2.0.3] - 2025-09-15

### Fixed
- Local project references for examples
- CI permissions for benchmark tracking
- Codegen + signing configuration for local builds

## [2.0.2] - 2025-09-10

### Fixed
- GitHub token for benchmark tracking action
- Native commonizer repository configuration
- CI codegen regeneration with signing skip for mavenLocal

### Added
- Comprehensive CI/CD pipeline with benchmark tracking on `gh-pages`

## [2.0.0] - 2025-09-01

### Added
- **Modular architecture**: Split monolith into `kap-core`, `kap-resilience`, `kap-arrow`
- **kap-core**: Multiplatform (JVM, JS, Native) orchestration with `Kap`, `with`, `then`, `andThen`, `zip`, `combine`, `race`, `raceN`, `traverse`, `sequence`, Flow integration, tracing
- **kap-resilience**: `Schedule` (composable retry policies), `CircuitBreaker`, `bracket`, `Resource`, `timeoutRace`, `raceQuorum`
- **kap-arrow**: `Validated` DSL with error accumulation, `zipV`/`kapV` (arity 2-22), `attempt`, `raceEither`, Either/Nel bridges
- **Benchmarks**: 119 JMH benchmarks with historical tracking dashboard
- **Examples**: 7 runnable example applications (ecommerce, dashboard, validation, Ktor, resilience, full-stack)
- **CI/CD**: Full pipeline — tests, platform compilation, codegen verification, benchmark tracking, Maven Central publishing
- Maven Central publication for all modules
- Property-based testing with Kotest
- Algebraic law verification (Functor, Applicative, Monad)
- Code generation for arities 2-22 (curry, kap, zip, combine, zipV, kapV, Resource.zip)

[Unreleased]: https://github.com/damian-rafael-lattenero/kap/compare/v2.7.0...HEAD
[2.7.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.6.0...v2.7.0
[2.6.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.5.0...v2.6.0
[2.5.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.4.0...v2.5.0
[2.4.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.2...v2.0.3
[2.0.2]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.0...v2.0.2
[2.0.0]: https://github.com/damian-rafael-lattenero/kap/releases/tag/v2.0.0
