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

KSP generates a step builder chain where each step only exposes the method for the next parameter:

```kotlin
// Step builder interfaces — no wrapper types needed
// kap(::User) returns a builder that only has .withFirstName
// .withFirstName { } returns a builder that only has .withLastName
// .withLastName { } returns a builder that only has .withAge
// .withAge { } returns an executable graph
```

Usage:

```kotlin
kap(::User)
    .withFirstName { fetchFirstName() }   // only .withFirstName is available here
    .withLastName { fetchLastName() }     // only .withLastName is available here
    .withAge { fetchAge() }               // only .withAge is available here
    .evalGraph()
```

Try calling `.withLastName` before `.withFirstName`? The compiler rejects it — that method doesn't exist on the current step. Done.

## Multiplatform by design

The generated step builders are pure Kotlin interfaces — they work on every Kotlin target: JVM, JS, WASM, Native, iOS, macOS. The KSP processor runs on JVM during compilation, but the code it generates compiles everywhere. No platform restrictions.

There are no wrapper types or extra allocations — the step builders guide the developer at compile time and add zero runtime overhead. The type safety is what matters, and it's enforced at compile time.

## Works on functions too

Not just constructors:

```kotlin
@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

// KSP generates a BuildDashboard marker object and named step builders

kap(BuildDashboard)
    .withUserName { fetchUserName() }
    .withCartSummary { fetchCartSummary() }
    .withPromoCode { fetchPromoCode() }
    .evalGraph()
```

## Handling collisions with `prefix`

Two functions with `userName: String`? Use `prefix`:

```kotlin
@KapTypeSafe(prefix = "Dashboard")
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard

@KapTypeSafe(prefix = "Report")
fun buildReport(userName: String, dateRange: String, format: String): Report
```

Dashboard generates `.withDashboardUserName { }`. Report generates `.withReportUserName { }`. No collision. Default is no prefix — clean and short for the common case.

## Why nobody else does this

The "newtype" pattern is well-known. Haskell, Rust, Scala — everyone recommends it. But nobody automates it because:

1. **It requires code generation** — you can't do it with the type system alone
2. **The generated code needs to integrate with a specific API** — it's not a general-purpose tool, it needs to know about `Kap` and the step builder chain
3. **KSP2 just became stable** — the tooling wasn't ready until recently

KAP is (as far as we know) the first Kotlin framework to ship this. One annotation, zero boilerplate, compile-time enforcement, full multiplatform support.

## The design journey

This feature came from being honest about a limitation. The original KAP README said "swap any two .with lines and the compiler rejects it" — but that's only true when types differ. For same types, it was a lie.

Instead of hiding it, we:

1. Acknowledged the limitation
2. Explored solutions (value classes, compiler plugins, KSP)
3. Built the simplest thing that works (`@KapTypeSafe` + KSP2)
4. Made it ergonomic (named step builders like `.withFirstName { }`, `prefix` for collisions)
5. Eliminated wrapper types entirely — step builders enforce order without runtime overhead

Each step was driven by one question: "what would make the developer's life easier?"

## Try it

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.6.0")
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.6.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.6.0")
}
```

- [Full documentation](https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/)
- [Working example](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ksp-demo)
- [GitHub](https://github.com/damian-rafael-lattenero/kap)

---

*KAP is open source (Apache 2.0). If you've hit the same-type parameter problem in your codebase, give `@KapTypeSafe` a try — and [let us know](https://github.com/damian-rafael-lattenero/kap/discussions) how it goes.*
