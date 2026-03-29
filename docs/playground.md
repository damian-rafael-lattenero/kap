---
hide:
  - toc
---

# Cookbook

Real examples from the [`readme-examples`](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/readme-examples) project. All code compiles, runs, and is verified on every CI push.

```bash
# Run all examples yourself:
git clone https://github.com/damian-rafael-lattenero/kap.git
cd kap
./gradlew :examples:readme-examples:run
```

---

## Parallel Execution — 3 services at once

```kotlin
data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchDashUser(): String { delay(30); return "Alice" }
suspend fun fetchDashCart(): String { delay(20); return "3 items" }
suspend fun fetchDashPromos(): String { delay(10); return "SAVE20" }

val result = Async {
    kap(::Dashboard)
        .with { fetchDashUser() }     // ┐ all three in parallel
        .with { fetchDashCart() }      // │ total time = max(30, 20, 10) = 30ms
        .with { fetchDashPromos() }    // ┘ not 60ms
}
```

**Output:**
```
Dashboard(user=Alice, cart=3 items, promos=SAVE20)
```

---

## 11-Service Checkout — 5 phases, one flat chain

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }              // ┐
        .with { fetchCart() }               // ├─ phase 1: parallel
        .with { fetchPromos() }             // │
        .with { fetchInventory() }          // ┘
        .then { validateStock() }           // ── phase 2: barrier
        .with { calcShipping() }            // ┐
        .with { calcTax() }                 // ├─ phase 3: parallel
        .with { calcDiscounts() }           // ┘
        .then { reservePayment() }          // ── phase 4: barrier
        .with { generateConfirmation() }    // ┐ phase 5: parallel
        .with { sendEmail() }              // ┘
}
```

**Output:**
```
CheckoutResult(user=UserProfile(name=Alice, id=42), cart=ShoppingCart(items=3, total=147.5),
promos=PromotionBundle(code=SUMMER20, discountPct=20), inventory=InventorySnapshot(allInStock=true),
stock=StockConfirmation(confirmed=true), shipping=ShippingQuote(amount=5.99, method=standard),
tax=TaxBreakdown(amount=12.38, rate=0.08), discounts=DiscountSummary(amount=29.5, promoApplied=SUMMER20),
payment=PaymentAuth(cardLast4=4242, authorized=true), confirmation=OrderConfirmation(orderId=order-#90142),
email=EmailReceipt(sentTo=alice@example.com, orderId=order-#90142))
```

---

## Value-Dependent Phases — `.andThen`

Phase 2 needs phase 1's result:

```kotlin
val userId = "user-1"
val dashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐ phase 1: parallel
        .with { fetchPreferences(userId) }   // │
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::PersonalizedDashboard)
                .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2
                .with { fetchPromotions(ctx.tier) }           // │
                .with { fetchTrending(ctx.prefs) }            // ┘
        }
}
```

**Output:**
```
PersonalizedDashboard(recs=recs-for-profile-user-1, promos=promos-gold, trending=trending-prefs-dark)
```

---

## Partial Failure — `.settled()`

One service fails, the rest still complete:

```kotlin
val dashboard = Async {
    kap { user: Result<String>, cart: String, config: String ->
        PartialDashboard(user.getOrDefault("anonymous"), cart, config)
    }
        .with(Kap { fetchUserMayFail() }.settled())  // wrapped in Result
        .with { fetchCartAlways() }
        .with { fetchConfigAlways() }
}
```

**Output:**
```
PartialDashboard(user=anonymous, cart=cart-ok, config=config-ok)
```

---

## `traverseSettled` — Collect ALL results

```kotlin
val ids = listOf(1, 2, 3, 4, 5)
val results: List<Result<String>> = Async {
    ids.traverseSettled { id ->
        Kap {
            if (id % 2 == 0) throw RuntimeException("fail-$id")
            "user-$id"
        }
    }
}
```

**Output:**
```
successes=[user-1, user-3, user-5], failures=[fail-2, fail-4]
```

---

## Racing — Fastest region wins

```kotlin
val fastest = Async {
    raceN(
        Kap { fetchFromRegionUS() },   // 100ms
        Kap { fetchFromRegionEU() },   // 30ms
        Kap { fetchFromRegionAP() },   // 60ms
    )
}
```

**Output:**
```
EU-data  (at ~30ms, US and AP cancelled)
```

---

## Retry with Schedule

```kotlin
var attempts = 0
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(10.milliseconds) and
    Schedule.doWhile<Throwable> { it is RuntimeException }

val result = Async {
    Kap {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        "success on attempt $attempts"
    }.retry(policy)
}
```

**Output:**
```
success on attempt 3
```

---

## TimeoutRace — Parallel fallback

Both start at t=0. Fallback is already running when primary times out:

```kotlin
val result = Async {
    Kap { delay(200); "primary-data" }
        .timeoutRace(100.milliseconds, Kap { delay(30); "fallback-data" })
}
```

**Output:**
```
fallback-data  (at ~30ms — 2.6x faster than sequential timeout)
```

---

## Resource Safety — `bracket`

All three resources acquired, used in parallel, ALL released even on failure:

```kotlin
val result = Async {
    kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .with(bracket(
            acquire = { openDbConnection() },
            use = { conn -> Kap { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openCacheConnection() },
            use = { conn -> Kap { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openHttpClient() },
            use = { client -> Kap { client.get("/api") } },
            release = { client -> client.close() },
        ))
}
```

**Output:**
```
db:result-of-SELECT 1|cache:key|http:/api
```

---

## Parallel Validation — Collect every error

```kotlin
val result: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("A") },
        { validateEmail("bad") },
        { validateAge(10) },
        { checkUsername("al") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
```

**Output:**
```
4 errors: [Name must be >= 2 chars, Invalid email: bad, Must be >= 18 got 10, Username too short]
```

---

## Memoization — Cache only successes

```kotlin
var callCount = 0
val fetchOnce = Kap { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

val a = Async { fetchOnce }  // runs, callCount=1
val b = Async { fetchOnce }  // cached, callCount still 1
```

**Output:**
```
memoizeOnSuccess: a=expensive-result, b=expensive-result, callCount=1
```

---

## Real HTTP — GitHub API + Cat Facts

From [`examples/real-world-http`](https://github.com/damian-rafael-lattenero/kap/tree/master/examples/real-world-http):

```kotlin
val profile = Async {
    kap(::DeveloperProfile)
        .with { fetchGithubUser("JetBrains") }
        .with { fetchGithubRepos("JetBrains") }
        .with { fetchCatFact().fact }
}
```

**Output:**
```
User: JetBrains (JetBrains)
Repos: 805 public, top 5: ...
Fun fact: Contrary to popular belief, the cat is a social animal.
Time: 693ms (parallel, not sequential)
```

---

Ready to try? [Get Started](guide/quickstart.md){ .md-button .md-button--primary } &nbsp; [GitHub](https://github.com/damian-rafael-lattenero/kap){ .md-button }
