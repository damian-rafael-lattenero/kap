---
date: 2026-04-04
authors:
  - damian
categories:
  - Kotlin
  - Coroutines
  - Open Source
slug: from-30-lines-of-async-await-to-12
---

# I Replaced 90 Lines of Coroutine Spaghetti with 35. Here's How.

*Our checkout endpoint had 7 service calls, a retry loop, a circuit breaker, and a timeout. The async/await code worked, but nobody could read it anymore. So I built something better.*

<!-- more -->

## The endpoint that broke me

It started simple. Three async calls, three awaits, a data class. Clean.

Then product said: "Stock validation is flaky — add retry." So I added a while loop with exponential backoff. Then: "Payment service goes down sometimes — add a circuit breaker." So I added an if/try/catch block with state tracking. Then: "Promos service is slow — add a timeout."

After three iterations, my clean coroutine code looked like this:

```kotlin
coroutineScope {
    val dUser  = async { fetchUser() }
    val dCart  = async { fetchCart() }
    val dPromos = async { withTimeout(3.seconds) { fetchPromos() } }
    val user   = dUser.await()
    val cart    = dCart.await()
    val promos  = dPromos.await()

    // retry loop — broke the async/await rhythm
    var stock = false
    var attempt = 0
    var delay = 100.milliseconds
    while (true) {
        try { stock = validateStock(); break }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (++attempt >= 3) throw e
            delay(delay); delay *= 2
        }
    }

    val dShipping = async { calcShipping() }
    val dTax      = async { calcTax() }

    // circuit breaker — interleaved with business logic
    val payment = if (!breaker.shouldAttempt()) {
        throw CircuitBreakerOpenException()
    } else {
        try {
            val p = withTimeout(5.seconds) { reservePayment() }
            breaker.recordSuccess(); p
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { breaker.recordFailure(); throw e }
    }

    CheckoutResult(user, cart, promos, stock, dShipping.await(), dTax.await(), payment)
}
```

I asked a new teammate to add one more service call to this. He stared at it for 20 minutes and asked: "Which lines are phase 1 and which are phase 2?"

Fair question. I couldn't answer it quickly either.

## What I actually wanted

I wanted the code to **read like the execution plan**:

- Phase 1: fetch user, cart, promos in parallel. Timeout on promos.
- Phase 2: validate stock. Retry 3 times with backoff.
- Phase 3: calculate shipping and tax in parallel.
- Phase 4: reserve payment. Circuit breaker. Timeout.

Four phases. Each one should be one line of intent, not ten lines of ceremony.

## What I built

```kotlin
@KapTypeSafe
data class CheckoutResult(
    val user: String, val cart: String, val promos: String,
    val stock: Boolean,
    val shipping: Double, val tax: Double,
    val payment: String,
)

val retryPolicy = Schedule.exponential<Throwable>(100.milliseconds) and Schedule.times(3)
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

kap(::CheckoutResult)
    .withUser { fetchUser() }                                         // ┐
    .withCart { fetchCart() }                                          // ├─ phase 1: parallel
    .withPromos(Kap { fetchPromos() }.timeout(3.seconds))             // ┘  + timeout
    .thenStock(Kap { validateStock() }.retry(retryPolicy))            // ── phase 2: barrier + retry
    .withShipping { calcShipping() }                                  // ┐ phase 3: parallel
    .withTax { calcTax() }                                            // ┘
    .thenPayment(Kap { reservePayment() }                             // ── phase 4: barrier
        .withCircuitBreaker(breaker)                                  //    + circuit breaker
        .timeout(5.seconds))                                          //    + timeout
    .evalGraph()
```

Same 7 calls. Same retry, circuit breaker, timeout. But now the phases are visible. `.with` = parallel. `.then` = barrier. The retry is *on the call*, not around it. The circuit breaker is *on the call*, not interleaved with it.

My teammate understood it in 30 seconds.

## The three concepts

The entire library is three ideas:

| You write | What happens | Think of it as |
|---|---|---|
| `.withX { }` | Runs in parallel with everything else in the same phase | *"and at the same time..."* |
| `.thenX { }` | Waits for all above, then continues | *"once that's done..."* |
| `.andThen { result -> }` | Waits, passes the result, builds the next graph | *"using what we got..."* |

Everything else — retry, circuit breaker, racing, validation — is built on top of these three.

## "What if one call fails?"

By default, one failure cancels everything — that's structured concurrency. But sometimes a call is optional. `settled { }` wraps it so failure doesn't kill siblings:

```kotlin
kap(::HomePage)
    .withProfile { fetchProfile() }              // critical — failure cancels all
    .withFeed(settled { fetchFeed() })           // optional — returns Result.failure
    .withAds(settled { fetchAds() })             // optional — returns Result.failure
    .evalGraph()
```

Feed crashes? You still get the profile and ads. No `supervisorScope`, no `runCatching` per branch.

## The part nobody expected: compile-time parameter safety

`@KapTypeSafe` generates a **step class per field**. After `.withUser`, the IDE only offers `.withCart`. You can't swap, skip, or forget a field:

```kotlin
kap(::CheckoutResult)
    .withUser { fetchUser() }     // Step 0 → only .withUser available
    .withCart { fetchCart() }      // Step 1 → only .withCart available
    .thenStock { ... }            // Step 2 → only .thenStock available
```

This is compile-time enforced. Not a runtime check. Not a lint rule. The wrong code literally doesn't compile. As far as I know, no other Kotlin framework does this.

## Performance: zero overhead

We run 119 JMH benchmarks on every push:

| Dimension | Raw Coroutines | KAP |
|---|---|---|
| Framework overhead (arity 3) | <0.01ms | <0.01ms |
| Multi-phase (9 calls, 4 phases) | 180.85ms | 180.98ms |
| 5 parallel calls @ 50ms each | 50.27ms | 50.31ms |

The abstraction compiles away. What's left is pure coroutines running in a structured scope.

## Everything together

Here's a real order placement. Read it top to bottom — each comment tells you what that line does and *why*:

```kotlin
// Retry policy: exponential backoff (100ms, 200ms, 400ms) with ±50% jitter, max 3 attempts
val retryPolicy = Schedule.exponential<Throwable>(100.milliseconds).jittered() and Schedule.times(3)

// Circuit breaker: after 5 consecutive failures, stop calling payment service for 30s
val paymentBreaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

suspend fun placeOrder(input: OrderInput): Either<Nel<OrderError>, OrderResult> {

    // ── PHASE 1: Validate everything in parallel ─────────────────
    // All three validators run concurrently.
    // If address AND card fail, you get BOTH errors — not just the first.
    val validated = kapV<OrderError, ValidAddress, ValidCard, ValidItems, ValidOrder>(::ValidOrder)
        .withV { validateAddress(input.address) }       // ┐ all three run at the same time
        .withV { validatePaymentInfo(input.card) }      // ├─ errors don't short-circuit
        .withV { validateItems(input.items) }           // ┘ they accumulate
        .evalGraph()

    // If any validation failed, return all errors immediately
    val order = validated.getOrElse { return Either.Left(it) }

    // ── PHASES 2–5: Process inside a DB transaction ──────────────
    // bracketCase guarantees: if anything fails, the transaction rolls back.
    // Even if a coroutine is cancelled mid-flight, release ALWAYS runs.
    return bracketCase(
        acquire = { db.beginTransaction() },
        use = { tx ->
            kap(::OrderResult)

                // PHASE 2: Get the best price — race 3 providers
                // All three start at t=0. First to respond wins. Losers are cancelled.
                .withFinalPrice(raceN(
                    Kap { pricingServiceA(order) },
                    Kap { pricingServiceB(order) },
                    Kap { pricingServiceC(order) },
                ))

                // PHASE 3: Reserve inventory — retry if the service is flaky
                // .then = barrier: waits for phase 2 to complete before starting
                // .retry = if it fails, try again with exponential backoff + jitter
                .thenReservationId(
                    Kap { reserveInventory(tx, order) }
                        .retry(retryPolicy)
                )

                // PHASE 4: Charge payment — circuit breaker + timeout
                // .then = barrier: waits for phase 3
                // .withCircuitBreaker = if payment service failed 5 times, fail fast
                // .timeout = don't wait more than 5 seconds
                .thenPaymentId(
                    Kap { chargePayment(tx, order) }
                        .withCircuitBreaker(paymentBreaker)
                        .timeout(5.seconds)
                )

                // PHASE 5: Send notifications — partial failure OK
                // All three run in parallel. If push notification fails,
                // email and analytics still complete. Each result is wrapped
                // in Result<Unit> so failures don't cancel siblings.
                .withNotifications(listOf(
                    Kap { sendEmail(order) },
                    Kap { sendPush(order) },
                    Kap { updateAnalytics(order) },
                ).sequenceSettled())

                .map { Either.Right(it) }
        },
        // CLEANUP: commit on success, rollback on any failure or cancellation
        release = { tx, exit -> when (exit) {
            is ExitCase.Completed -> tx.commit()
            else                  -> tx.rollback()
        }}
    ).evalGraph()
}
```

Count the features: **parallel validation with error accumulation**, **racing**, **phase barriers**, **retry with backoff and jitter**, **circuit breaker**, **timeout**, **partial failure tolerance**, **transactional safety with guaranteed cleanup**. Eight production concerns, one readable function.

The raw coroutines version of this is [~90 lines](https://github.com/damian-rafael-lattenero/kap#the-full-picture) of nested try/catch, manual state, and interleaved logic.

## Try it

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.7.0")
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.7.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.7.0")
}
```

Or clone the [starter project](https://github.com/damian-rafael-lattenero/kap-starter) and run `./gradlew run`.

- [GitHub](https://github.com/damian-rafael-lattenero/kap) — 900+ tests, Apache 2.0
- [Documentation](https://damian-rafael-lattenero.github.io/kap/)
- [Cookbook with 12 runnable examples](https://damian-rafael-lattenero.github.io/kap/playground/)

---

*KAP is open source. If your checkout endpoint looks like my "before" code, give it a try — and [let me know](https://github.com/damian-rafael-lattenero/kap/discussions) how it goes.*
