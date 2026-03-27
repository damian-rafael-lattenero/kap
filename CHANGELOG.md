# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
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

[Unreleased]: https://github.com/damian-rafael-lattenero/kap/compare/v2.3.0...HEAD
[2.3.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.3...v2.1.0
[2.0.3]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.2...v2.0.3
[2.0.2]: https://github.com/damian-rafael-lattenero/kap/compare/v2.0.0...v2.0.2
[2.0.0]: https://github.com/damian-rafael-lattenero/kap/releases/tag/v2.0.0
