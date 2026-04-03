# KAP vs Arrow vs Raw Coroutines

KAP doesn't replace Arrow or raw coroutines — it solves **multi-phase parallel orchestration** better than both.

---

## When to use what

| Scenario | Best tool | Why |
|---|---|---|
| 2-3 simple parallel calls | Raw coroutines | `coroutineScope { async {} }` is enough |
| Purely sequential code | `suspend` functions | No framework needed |
| Stream processing | `Flow` | Built for backpressure |
| Optics, typeclasses, full FP | Arrow | KAP doesn't cover these |
| **4+ parallel calls with phases** | **KAP** | Flat chain, visible barriers, type-safe |
| **Parallel validation (10+ fields)** | **KAP** | `zipV` scales to 22 (Arrow maxes at 9) |
| **Resilient orchestration** | **KAP** | timeout + circuit breaker + retry in one chain |
| **Same-type parameter safety** | **KAP + KSP** | `@KapTypeSafe` — nobody else has this |

---

## Feature comparison

| Feature | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| Multi-phase orchestration | Nested scopes, shuttle vars | Nested `parZip` blocks | **Flat chain with `.then`** |
| Compile-time arg order | No (positional) | No (named lambda) | **Typed function chain** |
| Same-type param safety | No | No | **`@KapTypeSafe` (KSP)** |
| Partial failure | `supervisorScope` (manual) | Not built-in | **`.settled()`** |
| Timeout + parallel fallback | Sequential (wastes time) | Not built-in | **`timeoutRace` (2.6x faster)** |
| Quorum (N-of-M) | Manual `select` + counting | Not built-in | **`raceQuorum`** |
| Success-only memoization | Manual Mutex + cache | Not built-in | **`.memoizeOnSuccess()`** |
| Parallel validation | Cancels siblings | `zipOrAccumulate` (max 9) | **`zipV` (max 22, parallel)** |
| Value-dependent phases | Manual variable threading | Sequential `parZip` | **`.andThen { ctx -> }`** |
| Retry + backoff | Manual loop (~20 lines) | `Schedule` | **`Schedule` (composable in chain)** |
| Resource safety | try/finally nesting | `Resource` monad | **`bracket` / `Resource` (parallel)** |
| Racing | Complex `select` | `raceN` | **`raceN` + `raceEither`** |
| Bounded traversal | Manual Semaphore | `parMap(concurrency)` | **`traverse(concurrency)`** |
| Circuit breaker | Manual state machine (~50 lines) | Separate module | **Composable in chain** |
| Flat multi-phase code | No | No | **Yes** |
| Multiplatform | Yes | Partial | **JVM, JS, WASM, Native, iOS** |

---

## The 11-service checkout

The litmus test: 11 microservice calls, 5 phases, dependencies between them.

=== "Raw Coroutines (30 lines)"

    ```kotlin
    val checkout = coroutineScope {
        val dUser = async { fetchUser() }
        val dCart = async { fetchCart() }
        val dPromos = async { fetchPromos() }
        val dInventory = async { fetchInventory() }
        val user = dUser.await()          // ← swap with cart? Same type, no error
        val cart = dCart.await()
        val promos = dPromos.await()
        val inventory = dInventory.await()

        val stock = validateStock()       // Where does phase 1 end? Read every line.

        val dShipping = async { calcShipping() }
        val dTax = async { calcTax() }
        val dDiscounts = async { calcDiscounts() }
        val shipping = dShipping.await()
        val tax = dTax.await()
        val discounts = dDiscounts.await()

        val payment = reservePayment()    // Another invisible barrier.

        val dConfirmation = async { generateConfirmation() }
        val dEmail = async { sendEmail() }

        CheckoutResult(
            user, cart, promos, inventory, stock,
            shipping, tax, discounts, payment,
            dConfirmation.await(), dEmail.await()
        )
    }
    // 30 lines. Invisible phases. Silent swap bugs. Shuttle variables.
    ```

=== "Arrow (40+ lines)"

    ```kotlin
    data class Phase1(val user: UserProfile, val cart: ShoppingCart,
                      val promos: PromotionBundle, val inventory: InventorySnapshot)
    data class Phase3(val shipping: ShippingQuote, val tax: TaxBreakdown,
                      val discounts: DiscountSummary)

    val p1 = parZip(
        { fetchUser() }, { fetchCart() }, { fetchPromos() }, { fetchInventory() },
    ) { user, cart, promos, inventory -> Phase1(user, cart, promos, inventory) }

    val stock = validateStock()

    val p3 = parZip(
        { calcShipping() }, { calcTax() }, { calcDiscounts() },
    ) { shipping, tax, discounts -> Phase3(shipping, tax, discounts) }

    val payment = reservePayment()

    val p5 = parZip(
        { generateConfirmation() }, { sendEmail() },
    ) { confirmation, email -> Pair(confirmation, email) }

    val checkout = CheckoutResult(
        p1.user, p1.cart, p1.promos, p1.inventory,
        stock,
        p3.shipping, p3.tax, p3.discounts,
        payment,
        p5.first, p5.second,
    )
    // 40+ lines. Intermediate data classes. Manual assembly.
    // Phases invisible. Swap p1.user/p1.cart? Same type, no error.
    ```

=== "KAP (12 lines)"

    ```kotlin
    @KapTypeSafe
    data class CheckoutResult(
        val user: User, val cart: Cart, val promos: Promos, val inventory: Inventory,
        val stock: StockCheck, val shipping: Shipping, val tax: Tax, val discounts: Discounts,
        val payment: Payment, val confirmation: Confirmation, val email: Email,
    )

    val checkout: CheckoutResult = kap(::CheckoutResult)
        .withUser { fetchUser() }              // ┐
        .withCart { fetchCart() }               // ├─ phase 1: parallel
        .withPromos { fetchPromos() }           // │
        .withInventory { fetchInventory() }     // ┘
        .thenStock { validateStock() }          // ── phase 2: barrier
        .withShipping { calcShipping() }        // ┐
        .withTax { calcTax() }                  // ├─ phase 3: parallel
        .withDiscounts { calcDiscounts() }      // ┘
        .thenPayment { reservePayment() }       // ── phase 4: barrier
        .withConfirmation { generateConfirmation() }  // ┐ phase 5: parallel
        .withEmail { sendEmail() }             // ┘
        .executeGraph()
    // 12 lines. Phases explicit. Swap any .withX → compile error.
    // No shuttle variables. No intermediate data classes.
    ```

---

## Resilient orchestration

=== "Raw Coroutines"

    ```kotlin
    // Manual: timeout + retry + circuit breaker + fallback
    var failures = 0
    val maxFailures = 5
    var circuitOpen = false

    suspend fun fetchWithResilience(): String {
        if (circuitOpen) return "cached-user"

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return withTimeout(500) { fetchUser() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                failures++
                if (failures >= maxFailures) circuitOpen = true
                delay(50L * (attempt + 1))
            }
        }
        return "cached-user"
    }
    // 20+ lines. Manual state. No composition. Fragile.
    ```

=== "Arrow"

    ```kotlin
    // Arrow has Schedule but no CircuitBreaker or timeoutRace
    val result = Schedule.recurs<Throwable>(3)
        .and(Schedule.exponential(50.milliseconds))
        .retry { fetchUser() }
    // No circuit breaker. No parallel timeout fallback.
    // No composable chain with .recover at the end.
    ```

=== "KAP"

    ```kotlin
    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

    val result = Kap { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3)
            and Schedule.exponential(50.milliseconds).jittered())
        .recover { "cached-user" }
        .executeGraph()
    // 8 lines. Composable. timeout → breaker → retry → fallback.
    ```

---

## Parallel validation

=== "Raw Coroutines"

    ```kotlin
    // Sequential: stops at first error. 5 invalid fields = 5 round trips.
    val name = validateName("A")       // fails → stop
    val email = validateEmail("bad")   // never reached
    val age = validateAge(10)          // never reached
    // User must fix and resubmit for each error. Terrible UX.
    ```

=== "Arrow"

    ```kotlin
    // Parallel error accumulation, but max 9 validators
    val result = Either.zipOrAccumulate(
        { validateName("A") },
        { validateEmail("bad") },
        { validateAge(10) },
        { checkUsername("al") },
    ) { name, email, age, username -> User(name, email, age, username) }
    // All errors at once. But: max 9 args, not parallel execution.
    ```

=== "KAP"

    ```kotlin
    // Parallel execution + error accumulation + scales to 22
    val result: Either<NonEmptyList<RegError>, User> = zipV(
        { validateName("A") },
        { validateEmail("bad") },
        { validateAge(10) },
        { checkUsername("al") },
    ) { name, email, age, username -> User(name, email, age, username) }
        .executeGraph()
    // 4 errors in one response. All validators ran in parallel.
    // Scales to 22 validators. Arrow maxes at 9.
    ```

---

## Same-type parameter safety

=== "Raw Coroutines"

    ```kotlin
    data class Booking(val guestName: String, val hotelName: String, val nights: Int, val guests: Int)

    // Swap guestName/hotelName? Both String. No error.
    // Swap nights/guests? Both Int. No error.
    Booking(dGuest.await(), dHotel.await(), dNights.await(), dGuests.await())
    ```

=== "Arrow"

    ```kotlin
    // Same problem. parZip with positional lambdas.
    parZip({ fetchGuest() }, { fetchHotel() }, { fetchNights() }, { fetchGuests() }) {
        guest, hotel, nights, guests ->
        Booking(guest, hotel, nights, guests)  // swap guest/hotel? No error.
    }
    ```

=== "KAP + @KapTypeSafe"

    ```kotlin
    @KapTypeSafe
    data class Booking(val guestName: String, val hotelName: String, val nights: Int, val guests: Int)

    // KSP generates named step builders — each step only shows the next parameter
    kap(::Booking)
        .withGuestName { fetchGuest() }     // only .withGuestName available here
        .withHotelName { fetchHotel() }     // only .withHotelName — swap? COMPILE ERROR
        .withNights { fetchNights() }       // only .withNights available here
        .withGuests { fetchGuests() }       // only .withGuests — swap with nights? COMPILE ERROR
        .executeGraph()
    ```

---

## Performance

All numbers from **119 JMH benchmarks** on JDK 21, Ubuntu 24.04. [Live dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/).

| Dimension | Raw Coroutines | Arrow | KAP | Winner |
|---|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | <0.01ms | Tie (Raw/KAP) |
| **Framework overhead** (arity 9) | <0.01ms | 0.03ms | <0.01ms | Tie (Raw/KAP) |
| **Simple parallel** (5 x 50ms) | 50.27ms | 50.33ms | 50.31ms | Tie |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | 180.98ms | Tie |
| **Race** (50ms vs 100ms) | 100.34ms | 50.51ms | 50.40ms | Tie (Arrow/KAP) |
| **timeoutRace** (primary wins) | 180.55ms | — | **30.34ms** | **KAP (6x)** |
| **Max validation arity** | — | 9 | **22** | **KAP** |

KAP overhead is **indistinguishable from raw coroutines**. Zero reflection, zero runtime codegen.

---

## KAP + Arrow together

KAP doesn't compete with Arrow — the `kap-arrow` module **uses** Arrow's types:

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.5.0")
}
```

Use Arrow for `Either`, `NonEmptyList`, optics, typeclasses. Use KAP for orchestration, phases, and `zipV` where Arrow's arity limit (9) isn't enough.

| You need... | Use... |
|---|---|
| Parallel orchestration with phases | KAP `kap-core` |
| Parallel validation with error accumulation | KAP `kap-arrow` (uses Arrow's Either) |
| Optics / lenses / prisms | Arrow `arrow-optics` |
| Typed errors with `Raise` | Arrow `arrow-core` |
| Resilience (retry, circuit breaker, bracket) | KAP `kap-resilience` |
| Ktor server integration | KAP `kap-ktor` |
| Same-type compile safety | KAP `kap-ksp` |
