# kap-ksp

KSP processor that makes same-type parameter swaps a **compile error**.

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.6"
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:3.0.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:3.0.0")
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

This is the same problem raw coroutines and Arrow have. No type system can catch it — unless you give each parameter a distinct slot.

## The Solution

`@KapTypeSafe` generates a scoped wrapper where each parameter gets its own **tagged slot**. Inside `.with { … }`, the lambda's implicit receiver is the slot interface for the current curry position — the IDE shows exactly the field you owe, by name. Swapping fields is a compile error citing the expected tag:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// KSP generates a scoped wrapper UserKap<F> with per-slot tags:
// kap(::User).with { firstName from … }.with { lastName from … }.with { age from … }.evalGraph()
//                    ↑ the lambda's receiver exposes ONLY `firstName: UserFirstNameTag`
//                      — typing any other field is a compile error.
```

Usage — clean, fluent, compile-time safe:

```kotlin
kap(::User)
    .with { firstName from fetchFirstName() }   // Only `firstName` resolves here
    .with { lastName  from fetchLastName()  }   // Only `lastName`  resolves here — swap? COMPILE ERROR
    .with { age       from fetchAge()       }   // Only `age`       resolves here
    .evalGraph()
```

**Multiplatform compatible** — generates plain Kotlin code that compiles on every Kotlin target (JVM, JS, WASM, Native, iOS, macOS). Zero runtime overhead beyond a single wrapper instance per chain.

---

## Works on Functions Too

Not just constructors — any function:

```kotlin
data class Dashboard(val userName: String, val cartSummary: String, val promoCode: String)

@KapTypeSafe
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard =
    Dashboard(userName, cartSummary, promoCode)
```

```kotlin
kap(::buildDashboard)
    .with { userName    from fetchUserName()    }
    .with { cartSummary from fetchCartSummary() }
    .with { promoCode   from fetchPromoCode()   }
    .evalGraph()
```

Generated entry point: `kap(::callable)` for both classes and functions. If multiple `@KapTypeSafe` functions share the same `(params) -> return` signature, the processor emits suffixed fallbacks like `kapBuildDashboard(::buildDashboard)` to avoid identical-signature overloads.

---

## Prefix — Avoiding Generated-Type Collisions

Two functions with overlapping parameter names? Use `prefix` to namespace the generated **file names and tag class names** — call-site tag names stay as the original parameter names:

```kotlin
@KapTypeSafe(prefix = "Dashboard")
fun buildDashboard(userName: String, cartSummary: String, promoCode: String): Dashboard = ...

@KapTypeSafe(prefix = "Report")
fun buildReport(userName: String, dateRange: String, format: String): Report = ...
```

The prefix prevents the generated `*UserNameTag` classes from colliding in your module. The call sites read the same as if there were no prefix:

```kotlin
// Dashboard — call-site uses original parameter names
kap(::buildDashboard)
    .with { userName    from fetchUserName()    }
    .with { cartSummary from fetchCartSummary() }
    .with { promoCode   from fetchPromoCode()   }
    .evalGraph()

// Report — same, no collision because tag classes are namespaced
kap(::buildReport)
    .with { userName  from fetchUserName()  }
    .with { dateRange from fetchDateRange() }
    .with { format    from fetchFormat()    }
    .evalGraph()
```

**Default is no prefix** — clean and short. Add prefix only when generated-type names would otherwise collide.

---

## @KapBridge — Third-Party Classes

Can't annotate a class you don't own? Use `@KapBridge` to generate the scoped wrapper for any third-party class:

```kotlin
@file:KapBridge(ThirdPartyUser::class)

// Now you can use the same scoped-builder pattern:
kap(::ThirdPartyUser)
    .with { firstName from fetchFirstName() }
    .with { lastName  from fetchLastName()  }
    .with { age       from fetchAge()       }
    .evalGraph()
```

KSP reads the constructor parameters from the bridged class and generates the same scoped wrapper as if the class had `@KapTypeSafe` directly.

---

## Composing With kap-core Operators

The wrapper is intentionally NOT a `Kap<F>` — it shadows the slot members against the generic `Kap.with(suspend () -> A)` extension. However, two mechanisms eliminate almost all friction:

**1. Last slot returns `Kap<R>` directly.** When the curry reduces to the final parameter, `.with { lastField from … }` returns `Kap<ReturnType>`. Kap-core operators (`.map`, `.recover`, `.timeout`, …) chain naturally:

```kotlin
val user: User = kap(::User)
    .with { firstName from fetchFirstName() }
    .with { lastName  from fetchLastName()  }
    .with { age       from fetchAge()       }   // ← Kap<User> from here
    .recover { User("Anon", "", 0) }
    .timeout(2.seconds)
    .evalGraph()
```

**2. `KapLike<F>` on the wrapper.** Partial wrappers (mid-chain) implement `KapLike<F>`, which ships delegate extensions for all kap-core operators. If you need to apply operators before the chain is complete, they are available directly — no `.asKap` required for kap-core operators.

For Kap-decorated **per-slot** values (timeout/retry on a single field), use the parens form — `from` has a `Kap<T>` overload:

```kotlin
kap(::User)
    .with(UserKap.firstName from Kap { fetchFirstName() }.timeout(500.milliseconds))
    .with(UserKap.lastName  from Kap { fetchLastName()  }.retry(retryPolicy))
    .with(UserKap.age       from Kap { fetchAge()       })
    .evalGraph()
```

`.asKap` is still available as a member (`UserKap<F>.asKap: Kap<F>`) when you need to pass the raw `Kap<F>` to an external API that expects it.

---

## What Gets Generated

For each `@KapTypeSafe` annotated class or function:

| Generated | Example |
|---|---|
| Scoped wrapper | `class UserKap<F>(...)` — holds the chain's `Kap<F>`, exposes per-slot tags |
| Per-slot interface | `interface UserFirstNameSlot { val firstName: UserFirstNameTag }` |
| Per-slot `.with`/`.then` | `UserKap<(UserFirstName) -> Rest>.with(UserFirstNameSlot.() -> UserFirstName)` |
| Infix `from` | `infix fun UserFirstNameTag.from(value: String): UserFirstName` |
| Entry point | `fun kap(f: (String, String, Int) -> User): UserKap<...>` |
| `KapLike<F>` impl | `class UserKap<F> : KapLike<F>, …` — kap-core operators available on partial wrappers |
| `.asKap` member | `UserKap<F>.asKap: Kap<F>` — raw `Kap<F>` for external APIs |
| Last-slot optimization | Last `.with`/`.then` returns `Kap<R>` for direct chaining of kap-core operators |
| Validated builder (kap-arrow) | `class UserValidatedKap<E, F>`, `fun <E> kapV(f: ...): UserValidatedKap<E, ...>` |

The slot-specific `.with` overload only matches when the chain's curried function head is that slot's wrapper type — so at each position only ONE field is in scope, and the IDE narrows accordingly.

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
    // Every parameter gets a named slot. Swap anything? COMPILE ERROR.
    kap(::User)
        .with { firstName from fetchFirstName() }   // Only `firstName` in scope
        .with { lastName  from fetchLastName()  }   // Only `lastName`  in scope
        .with { age       from fetchAge()       }   // Only `age`       in scope
        .evalGraph()
    ```

---

## Try It

```bash
./gradlew :examples:ksp-demo:run
```
