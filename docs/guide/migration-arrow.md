# Coming from Arrow

If you're already using Arrow's `parZip` for parallel execution, KAP can complement or replace it for multi-phase orchestration. This guide shows the migration path.

## KAP + Arrow: complementary, not competing

KAP's `kap-arrow` module **uses** Arrow's `Either` and `NonEmptyList`. You don't have to choose one or the other:

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.5.0") // uses Arrow Core
}
```

## Simple parallel: `parZip` → `combine`

=== "Arrow"

    ```kotlin
    val result = parZip(
        { fetchUser() },
        { fetchCart() },
        { fetchPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
    ```

=== "KAP"

    ```kotlin
    val result = combine(
        { fetchUser() },
        { fetchCart() },
        { fetchPromos() },
    ) { user, cart, promos -> Dashboard(user, cart, promos) }
        .executeGraph()
    ```

Almost identical. KAP's `combine` is Arrow's `parZip` equivalent.

## Type-safe ordering: `parZip` → `kap` + `.with`

=== "Arrow"

    ```kotlin
    // Named lambda params — swap user/cart? No compiler error.
    val result = parZip(
        { fetchUser() },
        { fetchCart() },
    ) { user, cart -> Page(user, cart) }
    ```

=== "KAP"

    ```kotlin
    @KapTypeSafe
    data class Page(val user: String, val cart: String)

    // Typed chain — swap .with lines? COMPILE ERROR.
    val result = kap(::Page)
        .withUser { fetchUser() }
        .withCart { fetchCart() }
        .executeGraph()
    ```

## Multi-phase: sequential `parZip` → flat chain

This is where KAP shines. Arrow requires separate `parZip` calls with intermediate variables:

=== "Arrow"

    ```kotlin
    // Phase 1
    val (user, cart) = parZip(
        { fetchUser() }, { fetchCart() },
    ) { u, c -> Pair(u, c) }

    // Phase 2 (barrier — just a suspend call)
    val validated = validate(user, cart)

    // Phase 3
    val (shipping, tax) = parZip(
        { calcShipping() }, { calcTax() },
    ) { s, t -> Pair(s, t) }

    val result = Result(user, cart, validated, shipping, tax)
    ```

=== "KAP"

    ```kotlin
    @KapTypeSafe
    data class Result(val user: User, val cart: Cart, val validated: Validated, val shipping: Shipping, val tax: Tax)

    val result = kap(::Result)
        .withUser { fetchUser() }           // ┐ phase 1
        .withCart { fetchCart() }            // ┘
        .thenValidated { validate() }       // ── phase 2: barrier
        .withShipping { calcShipping() }    // ┐ phase 3
        .withTax { calcTax() }              // ┘
        .executeGraph()
    ```

## Value-dependent phases: nested `parZip` → `.andThen`

=== "Arrow"

    ```kotlin
    val ctx = parZip(
        { fetchProfile(userId) }, { fetchPrefs(userId) },
    ) { profile, prefs -> UserContext(profile, prefs) }

    // ctx needed for phase 2
    val enriched = parZip(
        { fetchRecs(ctx.profile) }, { fetchPromos(ctx.prefs) },
    ) { recs, promos -> Enriched(recs, promos) }
    ```

=== "KAP"

    ```kotlin
    @KapTypeSafe
    data class UserContext(val profile: String, val prefs: String)
    @KapTypeSafe
    data class Enriched(val recs: String, val promos: String)

    val enriched = kap(::UserContext)
        .withProfile { fetchProfile(userId) }
        .withPrefs { fetchPrefs(userId) }
        .andThen { ctx ->
            kap(::Enriched)
                .withRecs { fetchRecs(ctx.profile) }
                .withPromos { fetchPromos(ctx.prefs) }
        }
        .executeGraph()
    ```

## Validation: `zipOrAccumulate` → `zipV`

=== "Arrow (max 9)"

    ```kotlin
    val result = Either.zipOrAccumulate(
        { validateName(name) },
        { validateEmail(email) },
        { validateAge(age) },
    ) { n, e, a -> User(n, e, a) }
    ```

=== "KAP (max 22, parallel)"

    ```kotlin
    val result = zipV(
        { validateName(name) },
        { validateEmail(email) },
        { validateAge(age) },
    ) { n, e, a -> User(n, e, a) }
        .executeGraph()
    // Same error accumulation, but validators run in parallel, and scales to 22
    ```

## Features KAP adds over Arrow

| Feature | Arrow | KAP |
|---|---|---|
| Visible phases | No | `.then` / `.andThen` |
| Compile-time arg order | No | Typed function chain |
| Max arity | 9 | 22 |
| `timeoutRace` | No | Parallel fallback, 2.6x faster |
| `raceQuorum` | No | N-of-M consensus |
| `.settled()` | No | Partial failure tolerance |
| `.memoizeOnSuccess()` | No | Cache only successes |
| Parallel validation | `zipOrAccumulate` | `zipV` (parallel + arity 22) |
