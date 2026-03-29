---
date: 2026-03-27
authors:
  - damian
categories:
  - Kotlin
  - KSP
  - Type Safety
slug: solving-the-same-type-parameter-problem-with-ksp
---

# Solving the Same-Type Parameter Problem with KSP

*How we used Kotlin Symbol Processing to make same-type parameter swaps a compile error — something no other Kotlin framework does.*

<!-- more -->

## The problem nobody talks about

Every Kotlin developer has written this:

```kotlin
data class User(val firstName: String, val lastName: String, val age: Int)
```

Three parameters. Two of them are `String`. Now parallelize:

```kotlin
val user = coroutineScope {
    val dFirst = async { fetchFirstName() }
    val dLast = async { fetchLastName() }
    val dAge = async { fetchAge() }
    User(dFirst.await(), dLast.await(), dAge.await())
}
```

Swap `dFirst` and `dLast`? No compile error. Both are `String`. Wrong name in the wrong field. Silent bug. Production.

Arrow's `parZip` has the same problem. KAP's typed `.with` chain catches type mismatches (String vs Int) but not same-type swaps. Haskell has the same limitation with applicative functors. The standard answer everywhere is "use newtypes" — and leave it to the developer to create them manually.

We thought: what if the compiler did it for you?

## The solution: `@KapTypeSafe`

One annotation. KSP generates everything:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)
```

KSP generates:

```kotlin
// Distinct wrapper types — each String parameter gets its own type
data class UserFirstName(val value: String)
data class UserLastName(val value: String)
data class UserAge(val value: Int)

// Type-safe kap function
fun kapSafe(f: (String, String, Int) -> User): Kap<(UserFirstName) -> (UserLastName) -> (UserAge) -> User>

// Fluent extension functions
fun String.toFirstName(): UserFirstName
fun String.toLastName(): UserLastName
fun Int.toAge(): UserAge
```

Usage:

```kotlin
kapSafe(::User)
    .with { fetchFirstName().toFirstName() }   // UserFirstName
    .with { fetchLastName().toLastName() }     // UserLastName — swap? COMPILE ERROR
    .with { fetchAge().toAge() }               // UserAge
```

Swap `.toFirstName()` and `.toLastName()`? The compiler rejects it. `UserFirstName` is not `UserLastName`. Done.

## Multiplatform by design

The generated wrappers are `data class` — they work on every Kotlin target: JVM, JS, WASM, Native, iOS, macOS. The KSP processor runs on JVM during compilation, but the code it generates compiles everywhere. No platform restrictions.

The overhead is one small object per wrapper — negligible when you're wrapping network calls that take 50ms+. The type safety is what matters, and it's enforced at compile time.

## Works on functions too

Not just constructors:

```kotlin
@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

// Generated: kapSafeBuildDashboard(), .toUserName(), .toCartSummary(), .toPromoCode()

kapSafeBuildDashboard(::buildDashboard)
    .with { fetchUserName().toUserName() }
    .with { fetchCartSummary().toCartSummary() }
    .with { fetchPromoCode().toPromoCode() }
```

## Handling collisions with `prefix`

Two functions with `userName: String`? Use `prefix`:

```kotlin
@KapTypeSafe(prefix = "Dashboard")
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard

@KapTypeSafe(prefix = "Report")
fun buildReport(userName: String, dateRange: String, format: String): Report
```

Dashboard generates `.toDashboardUserName()`. Report generates `.toReportUserName()`. No collision. Default is no prefix — clean and short for the common case.

## Why nobody else does this

The "newtype" pattern is well-known. Haskell, Rust, Scala — everyone recommends it. But nobody automates it because:

1. **It requires code generation** — you can't do it with the type system alone
2. **The generated code needs to integrate with a specific API** — it's not a general-purpose tool, it needs to know about `Kap` and `.with`
3. **KSP2 just became stable** — the tooling wasn't ready until recently

KAP is (as far as we know) the first Kotlin framework to ship this. One annotation, zero boilerplate, compile-time enforcement, full multiplatform support.

## The design journey

This feature came from being honest about a limitation. The original KAP README said "swap any two .with lines and the compiler rejects it" — but that's only true when types differ. For same types, it was a lie.

Instead of hiding it, we:

1. Acknowledged the limitation
2. Explored solutions (value classes, compiler plugins, KSP)
3. Built the simplest thing that works (`@KapTypeSafe` + KSP2)
4. Made it ergonomic (`.toFirstName()` extensions, `prefix` for collisions)
5. Chose `data class` over `@JvmInline value class` for full multiplatform compatibility

Each step was driven by one question: "what would make the developer's life easier?"

## Try it

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.5.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.5.0")
}
```

- [Full documentation](https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/)
- [Working example](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ksp-demo)
- [GitHub](https://github.com/damian-rafael-lattenero/kap)

---

*KAP is open source (Apache 2.0). If you've hit the same-type parameter problem in your codebase, give `@KapTypeSafe` a try — and [let us know](https://github.com/damian-rafael-lattenero/kap/discussions) how it goes.*
