package applicative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AsyncApplicativeTest {

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 1: CONCURRENCY PROOFS
    //
    //   Barrier-based tests that PROVE kap+with runs computations concurrently.
    //   If the wrong execution model were used, these tests would deadlock.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `kap+with runs both sides concurrently - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    latchA.complete(Unit)   // signal: A started
                    latchB.await()          // wait for B to start
                    "A"
                }
                .with {
                    latchB.complete(Unit)   // signal: B started
                    latchA.await()          // wait for A to start
                    "B"
                }
        }

        // If with were sequential, this would deadlock.
        // Completion proves true parallelism.
        assertEquals("A|B", result)
    }

    @Test
    fun `kap+with with three computations all run concurrently - barrier proof`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { latches[0].complete(Unit); latches.awaitOthers(0); "A" }
                .with { latches[1].complete(Unit); latches.awaitOthers(1); "B" }
                .with { latches[2].complete(Unit); latches.awaitOthers(2); "C" }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `then enforces sequential execution order`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { order.add("first"); "A" }
                .then { order.add("second"); "B" }
                .then { order.add("third"); "C" }
        }

        assertEquals("A|B|C", result)
        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `then then with fires concurrently - barrier proof`() = runTest {
        val apStarted = CompletableDeferred<Unit>()

        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .then { "A" }
                .with {
                    // B cannot complete until C has started
                    apStarted.await()
                    "B"
                }
                .with {
                    // C signals it started — proving it runs alongside B
                    apStarted.complete(Unit)
                    "C"
                }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `seven parallel kap+with calls all run concurrently - barrier proof`() = runTest {
        val latches = (0 until 7).map { CompletableDeferred<Unit>() }

        val result = Async {
            kap { a: String, b: String, c: String, d: String, e: String, f: String, g: String -> "$a|$b|$c|$d|$e|$f|$g" }
                .with { latches[0].complete(Unit); latches.awaitOthers(0); "A" }
                .with { latches[1].complete(Unit); latches.awaitOthers(1); "B" }
                .with { latches[2].complete(Unit); latches.awaitOthers(2); "C" }
                .with { latches[3].complete(Unit); latches.awaitOthers(3); "D" }
                .with { latches[4].complete(Unit); latches.awaitOthers(4); "E" }
                .with { latches[5].complete(Unit); latches.awaitOthers(5); "F" }
                .with { latches[6].complete(Unit); latches.awaitOthers(6); "G" }
        }

        // All seven must be running simultaneously or this deadlocks.
        assertEquals("A|B|C|D|E|F|G", result)
    }

    @Test
    fun `thirteen parallel kap+with calls - barrier proof`() = runTest {
        val latches = (0 until 13).map { CompletableDeferred<Unit>() }

        val result = Async {
            kap { s1: String, s2: String, s3: String, s4: String, s5: String, s6: String, s7: String, s8: String, s9: String, s10: String, s11: String, s12: String, s13: String ->
                listOf(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13).joinToString(";")
            }
                .with { latches[0].complete(Unit);  latches.awaitOthers(0);  "v1" }
                .with { latches[1].complete(Unit);  latches.awaitOthers(1);  "v2" }
                .with { latches[2].complete(Unit);  latches.awaitOthers(2);  "v3" }
                .with { latches[3].complete(Unit);  latches.awaitOthers(3);  "v4" }
                .with { latches[4].complete(Unit);  latches.awaitOthers(4);  "v5" }
                .with { latches[5].complete(Unit);  latches.awaitOthers(5);  "v6" }
                .with { latches[6].complete(Unit);  latches.awaitOthers(6);  "v7" }
                .with { latches[7].complete(Unit);  latches.awaitOthers(7);  "v8" }
                .with { latches[8].complete(Unit);  latches.awaitOthers(8);  "v9" }
                .with { latches[9].complete(Unit);  latches.awaitOthers(9);  "v10" }
                .with { latches[10].complete(Unit); latches.awaitOthers(10); "v11" }
                .with { latches[11].complete(Unit); latches.awaitOthers(11); "v12" }
                .with { latches[12].complete(Unit); latches.awaitOthers(12); "v13" }
        }

        assertEquals((1..13).joinToString(";") { "v$it" }, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 2: MIXED COMPOSITION (kap+with+then)
    //
    //   Complex dependency graphs with interleaved parallel and sequential.
    //   then enforces that earlier phases complete before the next begins.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `kap+with+then - ordering is correct`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            kap { a: String, b: String, c: String, d: String, e: String -> "$a|$b|$c|$d|$e" }
                .with { order.add("a"); "A" }
                .with { order.add("b"); "B" }
                .then { order.add("c"); "C" }
                .then { order.add("d"); "D" }
                .then { order.add("e"); "E" }
        }

        assertEquals("A|B|C|D|E", result)

        // c must come after both a and b (then barrier)
        val cIdx = order.indexOf("c")
        assertTrue(order.indexOf("a") < cIdx, "a should precede then c")
        assertTrue(order.indexOf("b") < cIdx, "b should precede then c")

        // e must come after c and d
        val eIdx = order.indexOf("e")
        assertTrue(cIdx < eIdx, "c should precede then e")
        assertTrue(order.indexOf("d") < eIdx, "d should precede then e")
    }

    @Test
    fun `e-commerce checkout - four phases with kap+with+then`() = runTest {
        data class CheckoutSummary(
            val user: UserProfile,
            val cart: ShoppingCart,
            val promos: PromotionBundle,
            val inventory: InventorySnapshot,
            val stock: StockConfirmation,
            val shipping: ShippingQuote,
            val tax: TaxBreakdown,
            val payment: PaymentAuth,
        )

        fun fetchUser() = UserProfile(name = "Alice", id = 1)
        fun fetchCart() = ShoppingCart(items = 3, total = 79.97)
        fun fetchPromotions() = PromotionBundle(code = "SUMMER20", discountPct = 20)
        fun fetchInventory() = InventorySnapshot(allInStock = true)
        fun validateStock() = StockConfirmation(confirmed = true)
        fun calcShipping() = ShippingQuote(amount = 5.00, method = "standard")
        fun calcTax() = TaxBreakdown(amount = 2.50, rate = 0.08)
        fun reservePayment() = PaymentAuth(cardLast4 = "4242", authorized = true)

        val result = Async {
            kap { user: UserProfile, cart: ShoppingCart, promos: PromotionBundle, inventory: InventorySnapshot,
                    stock: StockConfirmation, shipping: ShippingQuote, tax: TaxBreakdown, payment: PaymentAuth ->
                CheckoutSummary(user, cart, promos, inventory, stock, shipping, tax, payment)
            }
                .with { fetchUser() }
                .with { fetchCart() }
                .with { fetchPromotions() }
                .with { fetchInventory() }
                .then { validateStock() }
                .with { calcShipping() }
                .with { calcTax() }
                .then { reservePayment() }
        }

        assertEquals(
            CheckoutSummary(
                user = UserProfile(name = "Alice", id = 1),
                cart = ShoppingCart(items = 3, total = 79.97),
                promos = PromotionBundle(code = "SUMMER20", discountPct = 20),
                inventory = InventorySnapshot(allInStock = true),
                stock = StockConfirmation(confirmed = true),
                shipping = ShippingQuote(amount = 5.00, method = "standard"),
                tax = TaxBreakdown(amount = 2.50, rate = 0.08),
                payment = PaymentAuth(cardLast4 = "4242", authorized = true),
            ),
            result
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 3: NULLABLE HANDLING
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `kap+with with nullable values via optional`() = runTest {
        val absent: (suspend () -> String)? = null
        val present: (suspend () -> String)? = { "present" }

        val result = Async {
            kap { a: String, b: String?, c: String, d: String? ->
                "$a|${b ?: "nil"}|$c|${d ?: "nil"}"
            }
                .with { "A" }
                .with { absent?.invoke() }
                .with { "C" }
                .with { present?.invoke() }
        }

        assertEquals("A|nil|C|present", result)
    }

    @Test
    fun `then with null computation`() = runTest {
        val absent: (suspend () -> String)? = null

        val result = Async {
            kap { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .then { "A" }
                .then { absent?.invoke() }
        }

        assertEquals("A|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 4: ERROR PROPAGATION & STRUCTURED CONCURRENCY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exception in kap+with propagates through structured concurrency`() = runTest {
        val result = runCatching {
            Async {
                kap { a: String, b: String, c: String -> "$a|$b|$c" }
                    .with { "ok" }
                    .with { throw RuntimeException("boom") }
                    .with { "ok" }
            }
        }

        assertTrue(result.isFailure)
        assertIs<RuntimeException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `structured concurrency cancels siblings when one kap+with branch fails`() = runTest {
        val siblingStarted = CompletableDeferred<Unit>()
        val siblingCancelled = CompletableDeferred<Boolean>()

        runCatching {
            Async {
                kap { a: String, b: String -> "$a|$b" }
                    .with {
                        try {
                            siblingStarted.complete(Unit)
                            awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            siblingCancelled.complete(true)
                            throw e
                        }
                    }
                    .with {
                        siblingStarted.await() // ensure sibling is running
                        throw RuntimeException("fast-fail")
                    }
            }
        }

        assertTrue(siblingCancelled.await(), "Sibling should have been cancelled")
    }

    @Test
    fun `exception in then propagates correctly`() = runTest {
        val result = runCatching {
            Async {
                kap { a: String, b: String -> "$a|$b" }
                    .then { "ok" }
                    .then { throw RuntimeException("then failed") }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("then failed", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 5: COMBINATORS — map, andThen, zip, traverse, sequence, race
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map transforms computation result`() = runTest {
        val result = Async {
            Effect.of(42).map { it * 2 }.map { "result=$it" }
        }
        assertEquals("result=84", result)
    }

    @Test
    fun `andThen allows value-dependent continuation`() = runTest {
        val result = Async {
            Effect.of(42).andThen { n ->
                kap { doubled: Int, label: String -> "$label=$doubled" }
                    .with { n * 2 }
                    .with { "result" }
            }
        }
        assertEquals("result=84", result)
    }

    @Test
    fun `andThen chains are sequential`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            Effect.of("hello").andThen { greeting ->
                order.add("step1: $greeting")
                Effect.of("$greeting world")
            }.andThen { msg ->
                order.add("step2: $msg")
                Effect.of(msg.uppercase())
            }
        }

        assertEquals("HELLO WORLD", result)
        assertEquals(listOf("step1: hello", "step2: hello world"), order)
    }

    @Test
    fun `andThen mixes with kap+with - dependent fanout`() = runTest {
        val latchB = CompletableDeferred<Unit>()
        val latchC = CompletableDeferred<Unit>()

        val result = Async {
            Effect.of(10).andThen { base ->
                // After getting the base value, fan out in parallel
                kap { b: Int, c: Int -> base + b + c }
                    .with { latchB.complete(Unit); latchC.await(); base * 2 }
                    .with { latchC.complete(Unit); latchB.await(); base * 3 }
            }
        }

        // base=10, b=20 (parallel), c=30 (parallel) -> 10+20+30 = 60
        assertEquals(60, result)
    }

    @Test
    fun `zip combines two computations in parallel - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = Async {
            Effect { latchA.complete(Unit); latchB.await(); "A" }
                .zip(Effect { latchB.complete(Unit); latchA.await(); "B" })
        }

        // Deadlock if sequential -> proves parallelism
        assertEquals("A" to "B", result)
    }

    @Test
    fun `zip with transform combines and maps in parallel`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = Async {
            Effect<Int> { latchA.complete(Unit); latchB.await(); 21 }
                .zip(Effect { latchB.complete(Unit); latchA.await(); 21 }) { a, b -> a + b }
        }

        assertEquals(42, result)
    }

    @Test
    fun `traverse parallelizes over a collection - barrier proof`() = runTest {
        val latches = (0 until 5).map { CompletableDeferred<Unit>() }

        val result = Async {
            (0 until 5).toList().traverse { i ->
                Effect {
                    latches[i].complete(Unit)
                    latches.awaitOthers(i)
                    "v$i"
                }
            }
        }

        // All 5 must run concurrently or deadlock
        assertEquals(listOf("v0", "v1", "v2", "v3", "v4"), result)
    }

    @Test
    fun `sequence parallelizes a list of computations`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val result = Async {
            listOf(
                Effect<String> { latches[0].complete(Unit); latches.awaitOthers(0); "A" },
                Effect { latches[1].complete(Unit); latches.awaitOthers(1); "B" },
                Effect { latches[2].complete(Unit); latches.awaitOthers(2); "C" },
            ).sequence()
        }

        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun `traverse with bounded concurrency respects limit`() = runTest {
        var concurrent = 0
        var maxConcurrent = 0

        val result = Async {
            (0 until 10).toList().traverse(3) { i ->
                Effect {
                    concurrent++
                    if (concurrent > maxConcurrent) maxConcurrent = concurrent
                    delay(50)
                    concurrent--
                    "v$i"
                }
            }
        }

        assertEquals((0 until 10).map { "v$it" }, result)
        assertTrue(maxConcurrent <= 3, "Max concurrent was $maxConcurrent, expected <= 3")
    }

    @Test
    fun `traverse on empty list returns empty list`() = runTest {
        val result = Async {
            emptyList<Int>().traverse { Effect.of(it) }
        }
        assertEquals(emptyList(), result)
    }

    @Test
    fun `race returns the first computation to complete`() = runTest {
        val result = Async {
            race(
                fa = Effect { delay(10_000); "slow" },
                fb = Effect { "fast" }
            )
        }
        assertEquals("fast", result)
    }

    @Test
    fun `race cancels the loser`() = runTest {
        val loserCancelled = CompletableDeferred<Boolean>()

        val result = Async {
            race(
                fa = Effect {
                    try {
                        awaitCancellation()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        loserCancelled.complete(true)
                        throw e
                    }
                },
                fb = Effect { "winner" }
            )
        }

        assertEquals("winner", result)
        assertTrue(loserCancelled.await(), "Loser should have been cancelled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 6: DSL vs TRADITIONAL — THE VALUE PROPOSITION
    //
    //   Side-by-side with raw coroutines to demonstrate the DSL's advantage:
    //   same result, declarative structure, zero boilerplate.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `DSL vs traditional - seven parallel calls produce identical results`() = runTest {
        data class DashboardSummary(
            val user: UserProfile,
            val notifications: NotificationList,
            val prefs: Preferences,
            val feed: FeedContent,
            val suggestions: PeopleSuggestions,
            val recommendations: Recommendations,
            val badges: FeatureFlags,
        )

        fun fetchUser() = UserProfile(name = "Alice", id = 1)
        fun fetchNotifications() = NotificationList(unread = 3)
        fun fetchPrefs() = Preferences(theme = "dark", layout = "compact")
        fun fetchFeed() = FeedContent(posts = listOf("post1", "post2"))
        fun fetchSuggestions() = PeopleSuggestions(count = 12)
        fun fetchRecommendations() = Recommendations(suggested = listOf("item-a", "item-b"))
        fun fetchBadges() = FeatureFlags(flags = mapOf("beta" to true, "dark-mode" to true))

        // -- Traditional: 7 async + 7 await + manual combine --
        val traditional = coroutineScope {
            val d1 = async { fetchUser() }
            val d2 = async { fetchNotifications() }
            val d3 = async { fetchPrefs() }
            val d4 = async { fetchFeed() }
            val d5 = async { fetchSuggestions() }
            val d6 = async { fetchRecommendations() }
            val d7 = async { fetchBadges() }
            DashboardSummary(
                d1.await(), d2.await(), d3.await(), d4.await(),
                d5.await(), d6.await(), d7.await()
            )
        }

        // -- DSL: declarative, zero boilerplate --
        val dsl = Async {
            kap { user: UserProfile, notifications: NotificationList, prefs: Preferences,
                    feed: FeedContent, suggestions: PeopleSuggestions,
                    recommendations: Recommendations, badges: FeatureFlags ->
                DashboardSummary(user, notifications, prefs, feed, suggestions, recommendations, badges)
            }
                .with { fetchUser() }
                .with { fetchNotifications() }
                .with { fetchPrefs() }
                .with { fetchFeed() }
                .with { fetchSuggestions() }
                .with { fetchRecommendations() }
                .with { fetchBadges() }
        }

        assertEquals(traditional, dsl)
    }

    @Test
    fun `DSL vs traditional - mixed phases produce identical results`() = runTest {
        data class CheckoutPhased(
            val user: UserProfile,
            val cart: ShoppingCart,
            val promos: PromotionBundle,
            val stock: StockConfirmation,
            val shipping: ShippingQuote,
            val tax: TaxBreakdown,
            val payment: PaymentAuth,
        )

        fun fetchUser() = UserProfile(name = "Alice", id = 1)
        fun fetchCart() = ShoppingCart(items = 3, total = 79.97)
        fun fetchPromos() = PromotionBundle(code = "SUMMER20", discountPct = 20)
        fun validateStock() = StockConfirmation(confirmed = true)
        fun calcShipping() = ShippingQuote(amount = 5.00, method = "standard")
        fun calcTax() = TaxBreakdown(amount = 2.50, rate = 0.08)
        fun reservePayment() = PaymentAuth(cardLast4 = "4242", authorized = true)

        // -- Traditional: manual phase management --
        val traditional = coroutineScope {
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            val dPromos = async { fetchPromos() }
            val user = dUser.await(); val cart = dCart.await(); val promos = dPromos.await()

            val stock = validateStock()

            val dShipping = async { calcShipping() }
            val dTax = async { calcTax() }
            val shipping = dShipping.await(); val tax = dTax.await()

            val payment = reservePayment()

            CheckoutPhased(user, cart, promos, stock, shipping, tax, payment)
        }

        // -- DSL: the code IS the execution plan --
        val dsl = Async {
            kap { user: UserProfile, cart: ShoppingCart, promos: PromotionBundle, stock: StockConfirmation,
                    shipping: ShippingQuote, tax: TaxBreakdown, payment: PaymentAuth ->
                CheckoutPhased(user, cart, promos, stock, shipping, tax, payment)
            }
                .with { fetchUser() }
                .with { fetchCart() }
                .with { fetchPromos() }
                .then { validateStock() }
                .with { calcShipping() }
                .with { calcTax() }
                .then { reservePayment() }
        }

        assertEquals(traditional, dsl)
    }

    @Test
    fun `works with any suspend function - no framework coupling`() = runTest {
        // Simulate Retrofit, Room, Ktor -- zero adapters needed
        fun fetchFromRetrofit(): String = "user-data"
        fun queryFromRoom(): Int = 42
        fun callKtorClient(): List<String> = listOf("a", "b", "c")

        val result = Async {
            kap { user: String, count: Int, items: List<String> ->
                "$user|$count|${items.joinToString(",")}"
            }
                .with { fetchFromRetrofit() }
                .with { queryFromRoom() }
                .with { callKtorClient() }
        }

        assertEquals("user-data|42|a,b,c", result)
    }

    @Test
    fun `nine calls with three phases - readability comparison`() = runTest {
        data class FullPageLoad(
            val user: UserProfile,
            val prefs: Preferences,
            val flags: FeatureFlags,
            val auth: AuthToken,
            val feed: FeedContent,
            val notifications: NotificationList,
            val messages: MessageSummary,
            val recommendations: Recommendations,
            val trending: TrendingTopics,
        )

        fun fetchUser() = UserProfile(name = "Alice", id = 1)
        fun fetchPrefs() = Preferences(theme = "dark", layout = "compact")
        fun fetchFlags() = FeatureFlags(flags = mapOf("beta" to true))
        fun loadAuth() = AuthToken(token = "tok-abc", scope = "read")
        fun loadFeed() = FeedContent(posts = listOf("post1", "post2"))
        fun loadNotifications() = NotificationList(unread = 5)
        fun loadMessages() = MessageSummary(conversations = 3)
        fun loadRecommendations() = Recommendations(suggested = listOf("rec1"))
        fun loadTrending() = TrendingTopics(tags = listOf("kotlin", "coroutines"))

        // -- Traditional: manual phase orchestration --
        val traditional = coroutineScope {
            val d1 = async { fetchUser() }; val d2 = async { fetchPrefs() }; val d3 = async { fetchFlags() }
            val v1 = d1.await(); val v2 = d2.await(); val v3 = d3.await()
            val v4 = loadAuth(); val v5 = loadFeed()
            val d6 = async { loadNotifications() }; val d7 = async { loadMessages() }; val d8 = async { loadRecommendations() }
            val v6 = d6.await(); val v7 = d7.await(); val v8 = d8.await()
            val v9 = loadTrending()
            FullPageLoad(v1, v2, v3, v4, v5, v6, v7, v8, v9)
        }

        // -- DSL: the structure IS the execution plan --
        val dsl = Async {
            kap { user: UserProfile, prefs: Preferences, flags: FeatureFlags,
                    auth: AuthToken, feed: FeedContent,
                    notifications: NotificationList, messages: MessageSummary,
                    recommendations: Recommendations, trending: TrendingTopics ->
                FullPageLoad(user, prefs, flags, auth, feed, notifications, messages, recommendations, trending)
            }
                .with { fetchUser() }
                .with { fetchPrefs() }
                .with { fetchFlags() }
                .then { loadAuth() }
                .then { loadFeed() }
                .with { loadNotifications() }
                .with { loadMessages() }
                .with { loadRecommendations() }
                .then { loadTrending() }
        }

        assertEquals(traditional, dsl)
    }
}
