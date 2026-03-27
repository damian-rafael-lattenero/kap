# Contributing to KAP

Thank you for considering contributing to KAP! Every contribution matters, whether it's a bug report, feature suggestion, documentation improvement, or code change.

## Getting Started

### Prerequisites

- JDK 21+
- Kotlin 2.3.20+

### Building the Project

```bash
# Clone and build
git clone https://github.com/damian-rafael-lattenero/kap.git
cd kap
./gradlew build

# Run all tests
./gradlew :kap-core:jvmTest :kap-resilience:jvmTest :kap-arrow:test

# Run a specific example
./gradlew :examples:ecommerce-checkout:run

# Run benchmarks
./gradlew :benchmarks:jmh
```

### Project Structure

```
kap/
├── kap-core/          # Core orchestration (multiplatform)
├── kap-resilience/    # Schedule, CircuitBreaker, Resource, bracket (multiplatform)
├── kap-arrow/         # Arrow integration: Validated, Either, Nel (JVM)
├── benchmarks/        # JMH benchmarks (119 benchmarks)
└── examples/          # 7 runnable example applications
```

## How to Contribute

### Reporting Bugs

Open an issue using the **Bug Report** template. Include:
- A minimal reproducer (ideally a failing test)
- KAP version, Kotlin version, platform (JVM/JS/Native)
- Expected vs actual behavior

### Suggesting Features

Open an issue using the **Feature Request** template. Describe:
- The use case and why existing APIs don't cover it
- A rough API sketch if you have one in mind

### Submitting Code

1. **Fork** the repository and create a branch from `master`
2. **Write tests** for your change — we maintain 900+ tests and don't want to regress
3. **Follow existing patterns** — look at nearby code for style guidance
4. **Run the full test suite** before opening a PR:
   ```bash
   ./gradlew :kap-core:jvmTest :kap-resilience:jvmTest :kap-arrow:test
   ```
5. **Verify codegen is up to date** if you changed any generated files:
   ```bash
   ./gradlew :kap-core:generateAll :kap-resilience:generateResourceZip :kap-arrow:generateValidatedOverloads
   ```
6. **Open a PR** against `master` with a clear description of what and why

### Code Style

- Follow Kotlin conventions and the patterns already in the codebase
- Public API must have KDoc documentation
- No `@Suppress` annotations unless absolutely necessary (we eliminated them all)
- Prefer `suspend` functions over blocking code
- Respect structured concurrency — no `GlobalScope`, no uncancellable coroutine builders

### What Makes a Good PR

- **Focused**: One logical change per PR
- **Tested**: New behavior has tests; bug fixes include a regression test
- **Documented**: Public API changes include KDoc updates
- **Backward compatible**: Don't break existing public API without discussion first

## Areas Where Help is Welcome

- **Documentation**: Tutorials, guides, blog posts, examples
- **Integrations**: Ktor client plugin, Spring support, Kotest matchers
- **Platform testing**: JS and Native platform test coverage
- **Bug reports**: Edge cases, concurrency issues, platform-specific problems

## Mentoring

New to open source or functional programming? No problem. Open a draft PR or an issue with your question and we'll help you get there. We're happy to mentor contributors at any level.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
