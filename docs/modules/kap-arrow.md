# kap-arrow

Arrow integration for parallel validation with error accumulation.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-arrow:2.3.0")
```

**Depends on:** `kap-core` + Arrow Core.
**Platforms:** JVM only.
**Tests:** 223 tests across 10 test classes.

---

## The Problem

Standard validation short-circuits on the first error:

```kotlin
// User submits a form with 5 invalid fields.
// You return: "Name is too short"     ← user fixes, resubmits
// You return: "Email is invalid"      ← user fixes, resubmits
// You return: "Age must be 18+"       ← user fixes, resubmits
// ... 5 round trips for 5 errors.
```

KAP validates **all fields in parallel** and returns **every error at once**.

---

## Writing Validators

Each validator returns `Either<NonEmptyList<E>, A>`:

```kotlin
sealed class RegError(val message: String) {
    class InvalidName(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class InvalidAge(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> {
    delay(20)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.InvalidName("Name must be >= 2 chars")))
}

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> {
    delay(15)
    return if ("@" in email) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Invalid email: $email")))
}

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> {
    delay(10)
    return if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.InvalidAge("Must be >= 18, got $age")))
}

suspend fun checkUsername(username: String): Either<NonEmptyList<RegError>, ValidUsername> {
    delay(25)
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}
```

---

## `zipV` — Parallel Validation (2-22 args)

Run all validators in parallel, collect ALL errors:

```kotlin
// All pass:
val valid: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
// Right(User(ValidName(Alice), ValidEmail(alice@example.com), ValidAge(25), ValidUsername(alice)))

// All fail:
val invalid: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("A") },
        { validateEmail("bad") },
        { validateAge(10) },
        { checkUsername("al") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
// Left(NonEmptyList(InvalidName, InvalidEmail, InvalidAge, UsernameTaken))
// ALL 4 errors in ONE response. No round trips. All validated in parallel.
```

Scales to **22 validators**. Arrow's `zipOrAccumulate` maxes at 9.

---

## `kapV` + `withV` — Curried Builder Style

Same parallel execution and error accumulation, different syntax:

```kotlin
val result = Async {
    kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
        .withV { checkUsername("alice") }
}
```

Swap two `.withV` lines? **Compile error** — same type safety as `kap` + `.with`.

---

## Phased Validation — `thenV` / `andThenV`

Some validations depend on earlier results. Phase 1 collects all basic errors. Only if all pass does phase 2 run:

```kotlin
data class Identity(val name: ValidName, val email: ValidEmail, val age: ValidAge)
data class Clearance(val notBlocked: Boolean, val available: Boolean)
data class Registration(val identity: Identity, val clearance: Clearance)

val result: Either<NonEmptyList<RegError>, Registration> = Async {
    accumulate {
        // Phase 1: validate basic fields in parallel
        val identity = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()  // short-circuits if phase 1 fails

        // Phase 2: only runs if phase 1 passed — uses identity result
        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
```

---

## Entry Points

### `valid(a)` — Wrap a success

```kotlin
val v: Validated<RegError, ValidName> = valid(ValidName("Alice"))
```

### `invalid(e)` — Wrap a single error

```kotlin
val v: Validated<RegError, ValidName> = invalid(RegError.InvalidName("too short"))
```

### `invalidAll(errors)` — Wrap multiple errors

```kotlin
val v: Validated<RegError, ValidName> = invalidAll(
    nonEmptyListOf(RegError.InvalidName("too short"), RegError.InvalidName("no numbers"))
)
```

### `catching(toError) { }` — Exception → Error bridge

```kotlin
val v = Async {
    catching<RegError, String>({ e -> RegError.InvalidName(e.message ?: "unknown") }) {
        riskyOperation()
    }
}
```

---

## Transforms

| Combinator | What it does |
|---|---|
| `.mapV { }` | Transform the success value |
| `.mapError { }` | Transform the error type |
| `.recoverV { }` | Recover from validation errors |
| `.orThrow()` | Unwrap `Right` or throw on `Left` |
| `.ensureV(error) { pred }` | Guard with predicate |
| `.ensureVAll(errors) { pred }` | Guard returning multiple errors |

---

## Collection Operations

### `traverseV` — Validate each element, accumulate all errors

```kotlin
val emails = listOf("alice@example.com", "bad", "bob@example.com", "also-bad")
val result = Async {
    emails.traverseV { email -> validateEmail(email) }
}
// Left(NonEmptyList(InvalidEmail("bad"), InvalidEmail("also-bad")))
```

### `sequenceV` — Sequence validated computations

```kotlin
val validated: List<Validated<RegError, ValidEmail>> = emails.map { valid(ValidEmail(it)) }
val result = Async { validated.sequenceV() }
```

---

## Arrow Interop

### `.attempt()` — Catch to Either

```kotlin
val success: Either<Throwable, String> = Async {
    Kap { "hello" }.attempt()
}
// Right("hello")

val failure: Either<Throwable, String> = Async {
    Kap<String> { throw RuntimeException("boom") }.attempt()
}
// Left(RuntimeException("boom"))
```

### `raceEither(fa, fb)` — Race two different types

```kotlin
val result: Either<String, Int> = Async {
    raceEither(
        fa = Kap { delay(30); "fast-string" },
        fb = Kap { delay(100); 42 },
    )
}
// Left("fast-string") — String won the race
```

---

## `validated { }` / `accumulate { }` — Builder

### `accumulate { }` — Applicative builder with `.bindV()`

```kotlin
val result = Async {
    accumulate {
        val name = zipV({ validateName("Alice") }) { it }.bindV()
        val email = zipV({ validateEmail("alice@ex.com") }) { it }.bindV()
        User(name, email, ...)
    }
}
```

!!! warning "Short-circuit vs parallel"
    The `accumulate { }` builder short-circuits on the first failed `.bindV()` (monadic). For parallel error accumulation (applicative), use `zipV` — that's the whole point.

---

## Type Alias

```kotlin
typealias Validated<E, A> = Kap<Either<NonEmptyList<E>, A>>
```

This means you can use all `Kap` combinators (`.map`, `.timeout`, `.retry`, etc.) on validated computations.
