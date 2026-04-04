# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [2.6.0] - 2026-04-04

### Added
- **`timed { }` top-level shorthand** — like `settled { }`, wraps a call to return `TimedResult<A>` with wall-clock duration
- **Progressive disclosure README** — rewritten for open-source pitch with "The problem" → "Start simple" → "What if one call fails?" → "The full picture" narrative
- **Full placeOrder showcase** — realistic example combining `kapV`, `raceN`, `retry`, `CircuitBreaker`, `settled`, and `bracketCase` with raw coroutines comparison
- **"Composable superpowers" section** — standalone examples for `settled {}`, `raceN`, `traverse(concurrency)`, `timeoutRace`, `timed {}`, `memoizeOnSuccess`
- **All README examples verified** — every code block has a compilable, runnable function in readme-examples

### Changed
- README uses `Kap { }` SAM syntax exclusively (no `.toKap()`)
- README uses single `executeGraph()` per graph via `Kap<A>` overloads on `.withX()` / `.thenX()`
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

[Unreleased]: https://github.com/damian-rafael-lattenero/kap/compare/v2.6.0...HEAD
[2.6.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.5.0...v2.6.0
[2.5.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.4.0...v2.5.0
[2.4.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.2...v2.0.3
[2.0.2]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.0...v2.0.2
[2.0.0]: https://github.com/damian-rafael-lattenero/kap/releases/tag/v2.0.0
