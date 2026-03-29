# Validated Forms

Validate multiple fields in parallel and collect **every** error at once — not just the first.

!!! note "Requires `kap-arrow`"
    This guide uses the `kap-arrow` module which provides `zipV`, `withV`, and Arrow's `Either`/`NonEmptyList` types.

## The problem

Standard Kotlin validation short-circuits on the first error:

```kotlin
// User submits a form with 5 invalid fields.
// You return: "Name is too short"
// User fixes it, resubmits.
// You return: "Email is invalid"
// User fixes it, resubmits.
// You return: "Age must be 18+"
// ... 5 round trips for 5 errors.
```

## The solution: parallel validation

```kotlin
val result: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
```

- All 4 validators run **in parallel**
- All pass? `Either.Right(User(...))`
- 3 fail? `Either.Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow))` — every error, one response

## Writing validators

Each validator returns `Either<NonEmptyList<E>, A>`:

```kotlin
sealed class RegError {
    data class NameTooShort(val min: Int) : RegError()
    data class InvalidEmail(val value: String) : RegError()
    data class AgeTooLow(val min: Int) : RegError()
    data class UsernameTaken(val name: String) : RegError()
}

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> =
    if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.NameTooShort(2)))

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> =
    if (email.contains("@")) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail(email)))

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> =
    if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.AgeTooLow(18)))

suspend fun checkUsername(name: String): Either<NonEmptyList<RegError>, ValidUsername> =
    if (!isUsernameTaken(name)) Either.Right(ValidUsername(name))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken(name)))
```

## Phased validation

Some validations depend on earlier results. Use `thenV` for barriers:

```kotlin
val result = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
    ) { name, email, age -> BasicInfo(name, email, age) }
        .thenV { info ->
            // Only runs if all 3 above pass
            zipV(
                { checkUsername(info.name.value) },
                { verifyEmailDomain(info.email.value) },
            ) { username, domain -> FullRegistration(info, username, domain) }
        }
}
```

Phase 1 collects all basic errors. Only if all pass does phase 2 run.

## Scales to 22 validators

```kotlin
// Arrow's zipOrAccumulate maxes at 9 parameters.
// KAP's zipV goes to 22:
val result = Async {
    zipV(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10,
         f11, f12, f13, f14, f15, f16, f17, f18, f19, f20,
         f21, f22) { /* all 22 validated values */ }
}
```

## Using `withV` chains

Alternative syntax using the curried builder:

```kotlin
val result = Async {
    kapV(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
}
```

Same parallel execution, same error accumulation — different style.

## Setup

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.4.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.4.0")
}
```

## Try it

```bash
./gradlew :examples:validated-registration:run
```
