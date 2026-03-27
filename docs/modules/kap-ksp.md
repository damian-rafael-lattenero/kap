# kap-ksp

KSP processor that makes same-type parameter swaps a **compile error**.

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.3.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.3.0")
}
```

!!! warning "Unreleased"
    This module is available in source but **not yet published to Maven Central**. To use it now, build from source. Maven Central publication is planned for the next release.

**Depends on:** KSP2 2.3.6 (compatible with Kotlin 2.3.20).

---

## The Problem

KAP catches type swaps when types differ. But when two parameters share a type, the swap is silent:

```kotlin
data class User(val firstName: String, val lastName: String, val age: Int)

kap(::User)
    .with { fetchLastName() }    // String ← WRONG ORDER
    .with { fetchFirstName() }   // String ← WRONG ORDER
    .with { fetchAge() }         // Int    ← this one is safe
// Compiles. Wrong data. Production bug.
```

This is the same problem raw coroutines and Arrow have. No type system can catch it — unless you give each parameter a distinct type.

## The Solution

`@KapTypeSafe` generates distinct value class wrappers automatically:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// KSP generates:
// @JvmInline value class UserFirstName(val value: String)
// @JvmInline value class UserLastName(val value: String)
// @JvmInline value class UserAge(val value: Int)
// fun kapSafe(f: (String, String, Int) -> User): Kap<(UserFirstName) -> (UserLastName) -> (UserAge) -> User>
// fun String.toFirstName(): UserFirstName
// fun String.toLastName(): UserLastName
// fun Int.toAge(): UserAge
```

Usage — clean, fluent, compile-time safe:

```kotlin
kapSafe(::User)
    .with { fetchFirstName().toFirstName() }   // UserFirstName
    .with { fetchLastName().toLastName() }     // UserLastName — swap? COMPILE ERROR
    .with { fetchAge().toAge() }               // UserAge
```

**Zero runtime overhead** — value classes are inlined by the Kotlin compiler.

---

## Works on Functions Too

Not just constructors — any function:

```kotlin
data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)

@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

// Generated: kapSafeBuildDashboard(), .toUserName(), .toCartSummary(), .toPromoCode()

kapSafeBuildDashboard(::buildDashboard)
    .with { fetchUserName().toUserName() }
    .with { fetchCartSummary().toCartSummary() }
    .with { fetchPromoCode().toPromoCode() }
```

Generated function name: `kapSafe` for classes, `kapSafe{FunctionName}` for functions.

---

## Prefix — Avoiding Collisions

Two functions with the same parameter name? Use `prefix`:

```kotlin
@KapTypeSafe(prefix = "Dashboard")
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard = ...

@KapTypeSafe(prefix = "Report")
fun buildReport(userName: String, dateRange: String, format: String): Report = ...
```

Both have `userName: String`, but no collision:

```kotlin
// Dashboard
kapSafeBuildDashboard(::buildDashboard)
    .with { fetchUserName().toDashboardUserName() }      // no collision
    .with { fetchCartSummary().toDashboardCartSummary() }
    .with { fetchPromoCode().toDashboardPromoCode() }

// Report
kapSafeBuildReport(::buildReport)
    .with { fetchUserName().toReportUserName() }          // no collision
    .with { fetchDateRange().toReportDateRange() }
    .with { fetchFormat().toReportFormat() }
```

**Default is no prefix** — clean and short. Add prefix only when you need it.

---

## What Gets Generated

For each `@KapTypeSafe` annotated class or function:

| Generated | Example |
|---|---|
| Value class per param | `@JvmInline value class UserFirstName(val value: String)` |
| `kapSafe` function | `fun kapSafe(f: (String, String, Int) -> User): Kap<(UserFirstName) -> ...>` |
| Extension per param | `fun String.toFirstName(): UserFirstName` |

---

## Comparison

=== "Raw Coroutines"

    ```kotlin
    // Three String params. Swap any two? No error. Good luck.
    val user = coroutineScope {
        val dFirst = async { fetchFirstName() }
        val dLast = async { fetchLastName() }
        val dAge = async { fetchAge() }
        User(dFirst.await(), dLast.await(), dAge.await())
    }
    ```

=== "KAP (without KSP)"

    ```kotlin
    // Catches String vs Int swaps. Not String vs String.
    kap(::User)
        .with { fetchFirstName() }   // String
        .with { fetchLastName() }    // String — swap? no error
        .with { fetchAge() }         // Int
    ```

=== "KAP + @KapTypeSafe"

    ```kotlin
    // Every parameter has a unique type. Swap anything? COMPILE ERROR.
    kapSafe(::User)
        .with { fetchFirstName().toFirstName() }   // UserFirstName
        .with { fetchLastName().toLastName() }     // UserLastName
        .with { fetchAge().toAge() }               // UserAge
    ```

---

## Try It

```bash
./gradlew :examples:ksp-demo:run
```
