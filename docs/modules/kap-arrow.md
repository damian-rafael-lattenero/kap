# kap-arrow

Arrow integration for parallel validation with error accumulation.

```kotlin
implementation("io.github.damian-rafael-lattenero:kap-arrow:2.7.0")
```

**Depends on:** `kap-core` + Arrow Core.
**Platforms:** JVM only.
**Tests:** 223 tests across 10 test classes.

---

## The Problem — Validation Round Trips

=== "Raw Coroutines (short-circuits)"

    ```kotlin
    // Sequential: returns FIRST error, user must fix and resubmit for each one
    suspend fun registerUser(name: String, email: String, age: Int, username: String): User {
        val validName = validateName(name)       // ← fails here? stops
        val validEmail = validateEmail(email)     // ← never reached
        val validAge = validateAge(age)           // ← never reached
        val validUsername = checkUsername(username) // ← never reached
        return User(validName, validEmail, validAge, validUsername)
    }
    // 5 invalid fields = 5 round trips = terrible UX
    ```

=== "Arrow (max 9 validators)"

    ```kotlin
    val result = Either.zipOrAccumulate(
        { validateName(name) },
        { validateEmail(email) },
        { validateAge(age) },
        { checkUsername(username) },
    ) { n, e, a, u -> User(n, e, a, u) }
    // All errors at once — but maxes out at 9 parameters
    ```

=== "KAP (max 22 validators, parallel)"

    ```kotlin
    val result: Either<NonEmptyList<RegError>, User> = zipV(
        { validateName(name) },
        { validateEmail(email) },
        { validateAge(age) },
        { checkUsername(username) },
    ) { n, e, a, u -> User(n, e, a, u) }
        .evalGraph()
    // All errors at once + all validators run in PARALLEL + scales to 22
    ```

---

## Writing Validators

Each validator returns `Either<NonEmptyList<E>, A>`:

```kotlin
sealed class RegError(val message: String) {
    class InvalidName(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class InvalidAge(msg: String) : RegError(msg)
    class WeakPassword(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class ValidPassword(val value: String)
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
    delay(25)  // async DB check
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}
```

---

## `zipV` — Parallel Validation (2-22 args)

### All pass

=== "Raw Coroutines"

    ```kotlin
    // Sequential: if all pass you get the result, but no parallelism
    suspend fun registerUser(name: String, email: String, age: Int, username: String): User {
        val validName = validateName("Alice").getOrElse { throw ValidationException(it) }
        val validEmail = validateEmail("alice@example.com").getOrElse { throw ValidationException(it) }
        val validAge = validateAge(25).getOrElse { throw ValidationException(it) }
        val validUsername = checkUsername("alice").getOrElse { throw ValidationException(it) }
        return User(validName, validEmail, validAge, validUsername)
    }
    // Works when all pass, but runs sequentially — no parallel speedup
    ```

=== "KAP"

    ```kotlin
    val valid: Either<NonEmptyList<RegError>, User> = zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
        .evalGraph()
    // Right(User(ValidName(Alice), ValidEmail(alice@example.com), ValidAge(25), ValidUsername(alice)))
    ```

=== "Arrow"

    ```kotlin
    val result = Either.zipOrAccumulate(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
    // Same error accumulation, but max 9 args and not parallel
    ```

### All fail — every error collected

=== "Raw Coroutines"

    ```kotlin
    // Sequential: returns FIRST error only, user must fix and resubmit
    suspend fun registerUser(): User {
        val validName = validateName("A").getOrElse { throw ValidationException(it) }
        // ↑ fails here — never reaches the rest
        val validEmail = validateEmail("bad").getOrElse { throw ValidationException(it) }
        val validAge = validateAge(10).getOrElse { throw ValidationException(it) }
        val validUsername = checkUsername("al").getOrElse { throw ValidationException(it) }
        return User(validName, validEmail, validAge, validUsername)
    }
    // Only InvalidName reported — 4 errors = 4 round trips
    ```

=== "KAP"

    ```kotlin
    val invalid: Either<NonEmptyList<RegError>, User> = zipV(
        { validateName("A") },           // ← too short
        { validateEmail("bad") },         // ← no @
        { validateAge(10) },              // ← under 18
        { checkUsername("al") },          // ← too short
    ) { name, email, age, username -> User(name, email, age, username) }
        .evalGraph()
    // Left(NonEmptyList(InvalidName, InvalidEmail, InvalidAge, UsernameTaken))
    // ALL 4 errors in ONE response. All ran in parallel.
    ```

=== "Arrow"

    ```kotlin
    val result = Either.zipOrAccumulate(
        { validateName("A") },           // ← too short
        { validateEmail("bad") },         // ← no @
        { validateAge(10) },              // ← under 18
        { checkUsername("al") },          // ← too short
    ) { name, email, age, username -> User(name, email, age, username) }
    // Same error accumulation, but max 9 args and not parallel
    ```

Scales to **22 validators**. Arrow's `zipOrAccumulate` maxes at 9.

---

## `kapV` + `withV` — Curried Builder

Same parallel execution and error accumulation, typed chain syntax:

=== "Raw Coroutines"

    ```kotlin
    // Sequential calls, no error accumulation, no type-safe builder
    suspend fun registerUser(): Either<NonEmptyList<RegError>, User> {
        val name = validateName("Alice").getOrElse { return Either.Left(it) }
        val email = validateEmail("alice@example.com").getOrElse { return Either.Left(it) }
        val age = validateAge(25).getOrElse { return Either.Left(it) }
        val username = checkUsername("alice").getOrElse { return Either.Left(it) }
        return Either.Right(User(name, email, age, username))
    }
    // No parallel execution, short-circuits on first error
    ```

=== "KAP"

    ```kotlin
    val result = kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
        .withV { checkUsername("alice") }
        .evalGraph()
    ```

=== "Arrow"

    ```kotlin
    // No equivalent — Arrow has no curried builder for validation.
    // You must use zipOrAccumulate with all args in one call.
    ```

Swap two `.withV` lines? **Compile error** — same type safety as `kap` + `.with`.

---

## Phased Validation — `thenV` / `andThenV`

Some validations depend on earlier results. Phase 1 collects all basic errors. Only if all pass does phase 2 run:

=== "KAP"

    ```kotlin
    data class Identity(val name: ValidName, val email: ValidEmail, val age: ValidAge)
    data class Clearance(val notBlocked: Boolean, val available: Boolean)
    data class Registration(val identity: Identity, val clearance: Clearance)

    val result: Either<NonEmptyList<RegError>, Registration> = accumulate {
        // Phase 1: validate basic fields in parallel, collect ALL errors
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
    }.evalGraph()
    ```

=== "Raw Coroutines"

    ```kotlin
    // Sequential validation — stops at first error
    val name = validateName("Alice")       // fails? stop here
    val email = validateEmail("alice@ex.com") // never reached if name fails
    val age = validateAge(25)              // never reached if email fails
    // No error accumulation, no parallel execution
    // Each phase is just another if/else — no structured composition
    ```

---

## Entry Points

### `valid(a)` / `invalid(e)` / `invalidAll(errors)`

```kotlin
val success: Validated<RegError, ValidName> = valid(ValidName("Alice"))
val failure: Validated<RegError, ValidName> = invalid(RegError.InvalidName("too short"))
val multiError: Validated<RegError, ValidName> = invalidAll(
    nonEmptyListOf(RegError.InvalidName("too short"), RegError.InvalidName("no numbers"))
)
```

### `catching(toError) { }` — Exception to error bridge

```kotlin
val result = catching<RegError, String>({ e -> RegError.InvalidName(e.message ?: "unknown") }) {
    riskyOperation()
}.evalGraph()
```

---

## Guards — `ensureV` / `ensureVAll`

=== "Raw Coroutines"

    ```kotlin
    // Manual if/else — no composable guard abstraction
    fun validateAge(age: ValidAge): Either<NonEmptyList<RegError>, ValidAge> {
        return if (age.value >= 18) Either.Right(age)
        else Either.Left(nonEmptyListOf(RegError.InvalidAge("Must be 18+")))
    }

    // Multiple checks require manual error accumulation
    fun validatePassword(password: ValidPassword): Either<NonEmptyList<RegError>, ValidPassword> {
        val errors = buildList {
            if (password.value.length < 8) add(RegError.WeakPassword("Too short"))
            if (!password.value.any { it.isUpperCase() }) add(RegError.WeakPassword("No uppercase"))
            if (!password.value.any { it.isDigit() }) add(RegError.WeakPassword("No digit"))
        }
        return if (errors.isEmpty()) Either.Right(password)
        else Either.Left(nonEmptyListOf(errors.first(), *errors.drop(1).toTypedArray()))
    }
    // Must write a new function for every guard — no chaining
    ```

=== "KAP"

    ```kotlin
    val result = valid(ValidAge(15))
        .ensureV(RegError.InvalidAge("Must be 18+")) { it.value >= 18 }
        .evalGraph()
    // Left(NonEmptyList(InvalidAge("Must be 18+")))

    val result2 = valid(ValidPassword("123"))
        .ensureVAll { password ->
            buildList {
                if (password.value.length < 8) add(RegError.WeakPassword("Too short"))
                if (!password.value.any { it.isUpperCase() }) add(RegError.WeakPassword("No uppercase"))
                if (!password.value.any { it.isDigit() }) add(RegError.WeakPassword("No digit"))
            }.let { if (it.isEmpty()) null else nonEmptyListOf(it.first(), *it.drop(1).toTypedArray()) }
        }
        .evalGraph()
    // Left(NonEmptyList(WeakPassword("Too short"), WeakPassword("No uppercase")))
    ```

---

## Transforms

### `.mapV { }` — Transform success

```kotlin
val result = valid(ValidName("alice"))
    .mapV { it.value.uppercase() }
    .evalGraph()
// Right("ALICE")
```

### `.mapError { }` — Transform error type

```kotlin
val result = invalid(RegError.InvalidName("too short"))
    .mapError { ApiError(it.message) }
    .evalGraph()
```

### `.recoverV { }` — Recover from validation errors

```kotlin
val result = invalid(RegError.InvalidName("too short"))
    .recoverV { errors -> ValidName("default-${errors.size}-errors") }
    .evalGraph()
// Right(ValidName("default-1-errors"))
```

### `.orThrow()` — Unwrap or throw

```kotlin
val user: User = zipV(
    { validateName("Alice") },
    { validateEmail("alice@example.com") },
    { validateAge(25) },
    { checkUsername("alice") },
) { name, email, age, username -> User(name, email, age, username) }
    .orThrow()  // Right → value, Left → throws
    .evalGraph()
```

---

## Collection Operations

### `traverseV` — Validate each element

=== "KAP"

    ```kotlin
    val emails = listOf("alice@example.com", "bad", "bob@example.com", "also-bad")
    val result = emails.traverseV { email -> validateEmail(email) }
        .evalGraph()
    // Left(NonEmptyList(InvalidEmail("bad"), InvalidEmail("also-bad")))
    // ALL invalid emails reported, not just the first
    ```

=== "Raw Coroutines"

    ```kotlin
    val emails = listOf("alice@example.com", "bad", "bob@example.com", "also-bad")
    val errors = mutableListOf<RegError>()
    val results = mutableListOf<ValidEmail>()
    for (email in emails) {
        when (val r = validateEmail(email)) {
            is Either.Right -> results.add(r.value)
            is Either.Left -> errors.addAll(r.value)
        }
    }
    val result = if (errors.isEmpty()) Either.Right(results)
        else Either.Left(nonEmptyListOf(errors.first(), *errors.drop(1).toTypedArray()))
    // Manual loop, mutable state, no parallel execution
    ```

=== "Arrow"

    ```kotlin
    val emails = listOf("alice@example.com", "bad", "bob@example.com", "also-bad")
    val result: Either<NonEmptyList<RegError>, List<ValidEmail>> =
        emails.mapOrAccumulate { email -> validateEmail(email).bind() }
    // Arrow's mapOrAccumulate — accumulates errors but runs sequentially
    ```

### `sequenceV` — Sequence validated computations

=== "Raw Coroutines"

    ```kotlin
    val validated: List<Either<NonEmptyList<RegError>, ValidEmail>> = emails.map { email ->
        validateEmail(email)
    }
    val errors = validated.filterIsInstance<Either.Left<NonEmptyList<RegError>>>()
        .flatMap { it.value }
    val results = validated.filterIsInstance<Either.Right<ValidEmail>>()
        .map { it.value }
    val result = if (errors.isEmpty()) Either.Right(results)
        else Either.Left(nonEmptyListOf(errors.first(), *errors.drop(1).toTypedArray()))
    // Manual filtering, no structured composition
    ```

=== "KAP"

    ```kotlin
    val validated: List<Validated<RegError, ValidEmail>> = emails.map { email ->
        Kap { validateEmail(email) }
    }
    val result = validated.sequenceV().evalGraph()
    ```

---

## Arrow Interop

### `.attempt()` — Catch to Either

=== "Raw Coroutines"

    ```kotlin
    val result: Either<Throwable, String> = try {
        Either.Right(riskyOperation())
    } catch (e: Exception) {
        Either.Left(e)
    }
    ```

=== "KAP"

    ```kotlin
    val success: Either<Throwable, String> = Kap { "hello" }.attempt().evalGraph()
    // Right("hello")

    val failure: Either<Throwable, String> = Kap<String> { throw RuntimeException("boom") }
        .attempt().evalGraph()
    // Left(RuntimeException("boom"))
    ```

### `raceEither(fa, fb)` — Race two different types

=== "KAP"

    ```kotlin
    val result: Either<String, Int> = raceEither(
        fa = Kap { delay(30); "fast-string" },
        fb = Kap { delay(100); 42 },
    ).evalGraph()
    // Left("fast-string") — String won the race
    // Loser cancelled automatically
    ```

=== "Raw Coroutines"

    ```kotlin
    sealed class RaceResult {
        data class FromA(val value: String) : RaceResult()
        data class FromB(val value: Int) : RaceResult()
    }

    val result = coroutineScope {
        select<RaceResult> {
            async { delay(30); "fast-string" }.onAwait { RaceResult.FromA(it) }
            async { delay(100); 42 }.onAwait { RaceResult.FromB(it) }
        }
    }
    // Requires a sealed class wrapper for different types
    // Must manually handle cancellation of the loser
    ```

---

## `accumulate { }` Builder

For imperative-style validation with `.bindV()`:

=== "Raw Coroutines"

    ```kotlin
    // Sequential, no error accumulation within phases, no parallel execution
    suspend fun register(): Either<NonEmptyList<RegError>, Registration> {
        val name = validateName("Alice").getOrElse { return Either.Left(it) }
        val email = validateEmail("alice@example.com").getOrElse { return Either.Left(it) }
        val age = validateAge(25).getOrElse { return Either.Left(it) }
        val identity = Identity(name, email, age)

        // Phase 2 — also sequential, also short-circuits on first error
        val notBlocked = checkNotBlacklisted(identity).getOrElse { return Either.Left(it) }
        val available = checkUsernameAvailable(identity.email.value).getOrElse { return Either.Left(it) }
        val cleared = Clearance(notBlocked, available)

        return Either.Right(Registration(identity, cleared))
    }
    // No parallel execution within phases, no error accumulation
    ```

=== "KAP"

    ```kotlin
    val result = accumulate<RegError, Registration> {
        val identity = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()  // short-circuits if phase 1 fails

        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }.evalGraph()
    ```

=== "Arrow"

    ```kotlin
    val result = either<NonEmptyList<RegError>, Registration> {
        // Arrow's either { } block — monadic, short-circuits on first Left
        val identity = Either.zipOrAccumulate(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bind()  // short-circuits if phase 1 fails

        val cleared = Either.zipOrAccumulate(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bind()

        Registration(identity, cleared)
    }
    // Similar structure, but zipOrAccumulate is not parallel and maxes at 9 args
    ```

!!! warning "Short-circuit vs parallel"
    `.bindV()` short-circuits (monadic): if phase 1 fails, phase 2 never runs.
    `zipV` accumulates (applicative): all validators run, all errors collected.
    Use `zipV` within a phase, `bindV` between phases.

---

## Type Alias

```kotlin
typealias Validated<E, A> = Kap<Either<NonEmptyList<E>, A>>
```

This means all `Kap` combinators (`.map`, `.timeout`, `.retry`, `.traced`, etc.) work on validated computations too.
