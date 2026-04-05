# Algebraic Laws — KAP Composition Guarantees

Unlike most Kotlin libraries, every algebraic law is **property-based tested** with random inputs via Kotest. This means refactoring with these combinators is provably safe — the laws guarantee substitutability.

## `Kap` satisfies Functor, Applicative, and Monad laws

| Law | Property | What it guarantees |
|---|---|---|
| Functor Identity | `fa.map { it } == fa` | `map` with identity is a no-op |
| Functor Composition | `fa.map(g).map(f) == fa.map { f(g(it)) }` | Fusing two maps is safe |
| Applicative Identity | `Kap.of(id) with fa == fa` | Lifting identity does nothing |
| Applicative Homomorphism | `Kap.of(f) with Kap.of(x) == Kap.of(f(x))` | Pure values compose purely |
| Applicative Interchange | `u with Kap.of(y) == Kap.of { f -> f(y) } with u` | Order of pure application doesn't matter |
| Applicative Composition | `Kap.of(compose) with u with v with w == u with (v with w)` | Composition is associative |
| Monad Left Identity | `Kap.of(a).andThen(f) == f(a)` | Wrapping then andThen is same as direct call |
| Monad Right Identity | `m.andThen { Kap.of(it) } == m` | andThen with wrap is identity |
| Monad Associativity | `(m.andThen(f)).andThen(g) == m.andThen { f(it).andThen(g) }` | andThen chains are associative |

## `Validated` (withV/zipV) satisfies Applicative laws

`Validated` is intentionally NOT a Monad — error accumulation requires applicative semantics. Making it a Monad would force short-circuit behavior, which defeats the purpose of collecting all errors.

| Law | Property |
|---|---|
| Applicative Identity | `valid(id) withV fa == fa` |
| Applicative Homomorphism | `valid(f) withV valid(x) == valid(f(x))` |
| Error Accumulation | `invalid(e1) withV invalid(e2)` accumulates both errors into `NonEmptyList` |

## Arrow Integration

Arrow's `NonEmptyList` is used natively in `kap-arrow` — no custom reimplementation. All validated operations produce standard `Either<NonEmptyList<E>, A>` values.

## Test Source

All laws are verified in [`ApplicativeLawsTest.kt`](kap-core/src/jvmTest/kotlin/kap/ApplicativeLawsTest.kt) using Kotest property-based testing with random inputs.

**906 tests across 61 suites in 3 modules. All passing.**
