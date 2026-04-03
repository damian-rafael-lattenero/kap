# kap-ksp

KSP processor that makes same-type parameter swaps a **compile error**.

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.5.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.5.0")
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

This is the same problem raw coroutines and Arrow have. No type system can catch it — unless you give each parameter a distinct step.

## The Solution

`@KapTypeSafe` generates a step builder where each parameter gets its own named method. The IDE only shows the next parameter's method in autocomplete — you literally cannot wire them in the wrong order:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// KSP generates a step builder with named methods:
// kap(::User).withFirstName { }.withLastName { }.withAge { }.executeGraph()
// Each step only exposes the next method — no way to swap.
// No wrapper types. No companion object needed.
```

Usage — clean, fluent, compile-time safe:

```kotlin
kap(::User)
    .withFirstName { fetchFirstName() }   // Only withFirstName is available here
    .withLastName { fetchLastName() }     // Only withLastName is available here — swap? COMPILE ERROR
    .withAge { fetchAge() }               // Only withAge is available here
    .executeGraph()
```

**Multiplatform compatible** — generates plain Kotlin code that compiles on every Kotlin target (JVM, JS, WASM, Native, iOS, macOS). Zero overhead — no wrapper types, no extra allocations.

---

## Works on Functions Too

Not just constructors — any function:

```kotlin
data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)

@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

// KSP generates a marker object: BuildDashboard
// Usage: kap(BuildDashboard).withUserName { }.withCartSummary { }.withPromoCode { }.executeGraph()
```

```kotlin
kap(BuildDashboard)
    .withUserName { fetchUserName() }
    .withCartSummary { fetchCartSummary() }
    .withPromoCode { fetchPromoCode() }
    .executeGraph()
```

Generated entry point: `kap(::ClassName)` for classes, `kap(MarkerObject)` for functions (KSP generates the marker object from the function name in PascalCase).

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
kap(BuildDashboard)
    .withDashboardUserName { fetchUserName() }      // no collision
    .withDashboardCartSummary { fetchCartSummary() }
    .withDashboardPromoCode { fetchPromoCode() }
    .executeGraph()

// Report
kap(BuildReport)
    .withReportUserName { fetchUserName() }          // no collision
    .withReportDateRange { fetchDateRange() }
    .withReportFormat { fetchFormat() }
    .executeGraph()
```

**Default is no prefix** — clean and short. Add prefix only when you need it.

---

## @KapBridge — Third-Party Classes

Can't annotate a class you don't own? Use `@KapBridge` to generate a step builder for any third-party class:

```kotlin
@KapBridge(ThirdPartyUser::class)
class ThirdPartyUserBridge

// Now you can use the same step-builder pattern:
kap(::ThirdPartyUser)
    .withFirstName { fetchFirstName() }
    .withLastName { fetchLastName() }
    .withAge { fetchAge() }
    .executeGraph()
```

KSP reads the constructor parameters from the bridged class and generates the same named step methods as if the class had `@KapTypeSafe` directly.

---

## What Gets Generated

For each `@KapTypeSafe` annotated class or function:

| Generated | Example |
|---|---|
| Step builder chain | `kap(::User).withFirstName { }.withLastName { }.withAge { }.executeGraph()` |
| Named method per param | `.withFirstName { }`, `.withLastName { }`, `.withAge { }` |
| Marker object (functions only) | `object BuildDashboard` |

Each step interface exposes only the next parameter's method — IDE autocomplete enforces the correct order.

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
    // Every parameter gets a named step. Swap anything? COMPILE ERROR.
    kap(::User)
        .withFirstName { fetchFirstName() }   // Only withFirstName available
        .withLastName { fetchLastName() }     // Only withLastName available
        .withAge { fetchAge() }               // Only withAge available
        .executeGraph()
    ```

---

## Try It

```bash
./gradlew :examples:ksp-demo:run
```
