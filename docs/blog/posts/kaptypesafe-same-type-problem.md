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

KSP generates a scoped wrapper `UserKap<F>` with **per-slot tag interfaces**. Inside `.with { … }` the lambda's implicit receiver only resolves the field expected at the current curry position:

```kotlin
// Scoped builder + slot interfaces — no wrapper types at the call site
// kap(::User)         → UserKap<(UserFirstName) -> (UserLastName) -> (UserAge) -> User>
//                       lambda receiver = UserFirstNameSlot { val firstName: UserFirstNameTag }
// .with { firstName … } → UserKap<(UserLastName) -> (UserAge) -> User>
//                       lambda receiver = UserLastNameSlot { val lastName: UserLastNameTag }
// .with { lastName  … } → UserKap<(UserAge) -> User>
// .with { age       … } → UserKap<User> — runnable via .evalGraph()
```

Usage:

```kotlin
kap(::User)
    .with { firstName from fetchFirstName() }   // only `firstName` resolves here
    .with { lastName  from fetchLastName()  }   // only `lastName`  resolves here
    .with { age       from fetchAge()       }   // only `age`       resolves here
    .evalGraph()
```

Try `.with { lastName from … }` first? `lastName` is unresolved at that curry position — the lambda receiver is `UserFirstNameSlot`, which only exposes `firstName`. The compiler rejects it with a name that points directly at the expected slot. Done.

## Multiplatform by design

The generated scoped wrapper and slot interfaces are pure Kotlin — they work on every Kotlin target: JVM, JS, WASM, Native, iOS, macOS. The KSP processor runs on JVM during compilation, but the code it generates compiles everywhere. No platform restrictions.

The runtime cost is a single wrapper allocation per chain (the underlying `Kap<F>` would have been allocated anyway). The slot interfaces are compile-time IDE narrowing only — no virtual dispatch, no per-field state.

## Works on functions too

Not just constructors:

```kotlin
@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)

// KSP generates a BuildDashboard marker object and named step builders

kap(BuildDashboard)
    .with { userName from fetchUserName() }
    .with { cartSummary from fetchCartSummary() }
    .with { promoCode from fetchPromoCode() }
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

Dashboard generates `.with { dashboardUserName from  }`. Report generates `.with { reportUserName from  }`. No collision. Default is no prefix — clean and short for the common case.

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
4. Made it ergonomic (named step builders like `.with { firstName from  }`, `prefix` for collisions)
5. Eliminated wrapper types entirely — step builders enforce order without runtime overhead

Each step was driven by one question: "what would make the developer's life easier?"

## Try it

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:3.0.0")
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:3.0.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:3.0.0")
}
```

- [Full documentation](https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/)
- [Working example](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/ksp-demo)
- [GitHub](https://github.com/damian-rafael-lattenero/kap)

---

*KAP is open source (Apache 2.0). If you've hit the same-type parameter problem in your codebase, give `@KapTypeSafe` a try — and [let us know](https://github.com/damian-rafael-lattenero/kap/discussions) how it goes.*
