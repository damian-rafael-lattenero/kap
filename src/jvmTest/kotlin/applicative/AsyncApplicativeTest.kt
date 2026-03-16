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
    //   Barrier-based tests that PROVE lift+ap runs computations concurrently.
    //   If the wrong execution model were used, these tests would deadlock.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `lift+ap runs both sides concurrently - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap {
                    latchA.complete(Unit)   // signal: A started
                    latchB.await()          // wait for B to start
                    "A"
                }
                .ap {
                    latchB.complete(Unit)   // signal: B started
                    latchA.await()          // wait for A to start
                    "B"
                }
        }

        // If ap were sequential, this would deadlock.
        // Completion proves true parallelism.
        assertEquals("A|B", result)
    }

    @Test
    fun `lift+ap with three computations all run concurrently - barrier proof`() = runTest {
        val latches = (0 until 3).map { CompletableDeferred<Unit>() }

        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { latches[0].complete(Unit); latches.awaitOthers(0); "A" }
                .ap { latches[1].complete(Unit); latches.awaitOthers(1); "B" }
                .ap { latches[2].complete(Unit); latches.awaitOthers(2); "C" }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `followedBy enforces sequential execution order`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { order.add("first"); "A" }
                .followedBy { order.add("second"); "B" }
                .followedBy { order.add("third"); "C" }
        }

        assertEquals("A|B|C", result)
        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `followedBy then ap fires concurrently - barrier proof`() = runTest {
        val apStarted = CompletableDeferred<Unit>()

        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .followedBy { "A" }
                .ap {
                    // B cannot complete until C has started
                    apStarted.await()
                    "B"
                }
                .ap {
                    // C signals it started — proving it runs alongside B
                    apStarted.complete(Unit)
                    "C"
                }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `seven parallel lift+ap calls all run concurrently - barrier proof`() = runTest {
        val latches = (0 until 7).map { CompletableDeferred<Unit>() }

        val result = Async {
            lift7 { a: String, b: String, c: String, d: String, e: String, f: String, g: String -> "$a|$b|$c|$d|$e|$f|$g" }
                .ap { latches[0].complete(Unit); latches.awaitOthers(0); "A" }
                .ap { latches[1].complete(Unit); latches.awaitOthers(1); "B" }
                .ap { latches[2].complete(Unit); latches.awaitOthers(2); "C" }
                .ap { latches[3].complete(Unit); latches.awaitOthers(3); "D" }
                .ap { latches[4].complete(Unit); latches.awaitOthers(4); "E" }
                .ap { latches[5].complete(Unit); latches.awaitOthers(5); "F" }
                .ap { latches[6].complete(Unit); latches.awaitOthers(6); "G" }
        }

        // All seven must be running simultaneously or this deadlocks.
        assertEquals("A|B|C|D|E|F|G", result)
    }

    @Test
    fun `thirteen parallel lift+ap calls - barrier proof`() = runTest {
        val latches = (0 until 13).map { CompletableDeferred<Unit>() }

        val result = Async {
            lift13 { s1: String, s2: String, s3: String, s4: String, s5: String, s6: String, s7: String, s8: String, s9: String, s10: String, s11: String, s12: String, s13: String ->
                listOf(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13).joinToString(";")
            }
                .ap { latches[0].complete(Unit);  latches.awaitOthers(0);  "v1" }
                .ap { latches[1].complete(Unit);  latches.awaitOthers(1);  "v2" }
                .ap { latches[2].complete(Unit);  latches.awaitOthers(2);  "v3" }
                .ap { latches[3].complete(Unit);  latches.awaitOthers(3);  "v4" }
                .ap { latches[4].complete(Unit);  latches.awaitOthers(4);  "v5" }
                .ap { latches[5].complete(Unit);  latches.awaitOthers(5);  "v6" }
                .ap { latches[6].complete(Unit);  latches.awaitOthers(6);  "v7" }
                .ap { latches[7].complete(Unit);  latches.awaitOthers(7);  "v8" }
                .ap { latches[8].complete(Unit);  latches.awaitOthers(8);  "v9" }
                .ap { latches[9].complete(Unit);  latches.awaitOthers(9);  "v10" }
                .ap { latches[10].complete(Unit); latches.awaitOthers(10); "v11" }
                .ap { latches[11].complete(Unit); latches.awaitOthers(11); "v12" }
                .ap { latches[12].complete(Unit); latches.awaitOthers(12); "v13" }
        }

        assertEquals((1..13).joinToString(";") { "v$it" }, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 2: MIXED COMPOSITION (lift+ap+followedBy)
    //
    //   Complex dependency graphs with interleaved parallel and sequential.
    //   followedBy enforces that earlier phases complete before the next begins.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `lift+ap+followedBy - ordering is correct`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            lift5 { a: String, b: String, c: String, d: String, e: String -> "$a|$b|$c|$d|$e" }
                .ap { order.add("a"); "A" }
                .ap { order.add("b"); "B" }
                .followedBy { order.add("c"); "C" }
                .followedBy { order.add("d"); "D" }
                .followedBy { order.add("e"); "E" }
        }

        assertEquals("A|B|C|D|E", result)

        // c must come after both a and b (followedBy barrier)
        val cIdx = order.indexOf("c")
        assertTrue(order.indexOf("a") < cIdx, "a should precede followedBy c")
        assertTrue(order.indexOf("b") < cIdx, "b should precede followedBy c")

        // e must come after c and d
        val eIdx = order.indexOf("e")
        assertTrue(cIdx < eIdx, "c should precede followedBy e")
        assertTrue(order.indexOf("d") < eIdx, "d should precede followedBy e")
    }

    @Test
    fun `e-commerce checkout - four phases with lift+ap+followedBy`() = runTest {
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
            lift8 { user: UserProfile, cart: ShoppingCart, promos: PromotionBundle, inventory: InventorySnapshot,
                    stock: StockConfirmation, shipping: ShippingQuote, tax: TaxBreakdown, payment: PaymentAuth ->
                CheckoutSummary(user, cart, promos, inventory, stock, shipping, tax, payment)
            }
                .ap { fetchUser() }
                .ap { fetchCart() }
                .ap { fetchPromotions() }
                .ap { fetchInventory() }
                .followedBy { validateStock() }
                .ap { calcShipping() }
                .ap { calcTax() }
                .followedBy { reservePayment() }
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
    fun `lift+ap with nullable values via optional`() = runTest {
        val absent: (suspend () -> String)? = null
        val present: (suspend () -> String)? = { "present" }

        val result = Async {
            lift4 { a: String, b: String?, c: String, d: String? ->
                "$a|${b ?: "nil"}|$c|${d ?: "nil"}"
            }
                .ap { "A" }
                .ap { absent?.invoke() }
                .ap { "C" }
                .ap { present?.invoke() }
        }

        assertEquals("A|nil|C|present", result)
    }

    @Test
    fun `followedBy with null computation`() = runTest {
        val absent: (suspend () -> String)? = null

        val result = Async {
            lift2 { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .followedBy { "A" }
                .followedBy { absent?.invoke() }
        }

        assertEquals("A|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 4: ERROR PROPAGATION & STRUCTURED CONCURRENCY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exception in lift+ap propagates through structured concurrency`() = runTest {
        val result = runCatching {
            Async {
                lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                    .ap { "ok" }
                    .ap { throw RuntimeException("boom") }
                    .ap { "ok" }
            }
        }

        assertTrue(result.isFailure)
        assertIs<RuntimeException>(result.exceptionOrNull())
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `structured concurrency cancels siblings when one lift+ap branch fails`() = runTest {
        val siblingStarted = CompletableDeferred<Unit>()
        val siblingCancelled = CompletableDeferred<Boolean>()

        runCatching {
            Async {
                lift2 { a: String, b: String -> "$a|$b" }
                    .ap {
                        try {
                            siblingStarted.complete(Unit)
                            awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            siblingCancelled.complete(true)
                            throw e
                        }
                    }
                    .ap {
                        siblingStarted.await() // ensure sibling is running
                        throw RuntimeException("fast-fail")
                    }
            }
        }

        assertTrue(siblingCancelled.await(), "Sibling should have been cancelled")
    }

    @Test
    fun `exception in followedBy propagates correctly`() = runTest {
        val result = runCatching {
            Async {
                lift2 { a: String, b: String -> "$a|$b" }
                    .followedBy { "ok" }
                    .followedBy { throw RuntimeException("followedBy failed") }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("followedBy failed", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 5: COMBINATORS — map, flatMap, zip, traverse, sequence, race
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map transforms computation result`() = runTest {
        val result = Async {
            pure(42).map { it * 2 }.map { "result=$it" }
        }
        assertEquals("result=84", result)
    }

    @Test
    fun `flatMap allows value-dependent continuation`() = runTest {
        val result = Async {
            pure(42).flatMap { n ->
                lift2 { doubled: Int, label: String -> "$label=$doubled" }
                    .ap { n * 2 }
                    .ap { "result" }
            }
        }
        assertEquals("result=84", result)
    }

    @Test
    fun `flatMap chains are sequential`() = runTest {
        val order = mutableListOf<String>()

        val result = Async {
            pure("hello").flatMap { greeting ->
                order.add("step1: $greeting")
                pure("$greeting world")
            }.flatMap { msg ->
                order.add("step2: $msg")
                pure(msg.uppercase())
            }
        }

        assertEquals("HELLO WORLD", result)
        assertEquals(listOf("step1: hello", "step2: hello world"), order)
    }

    @Test
    fun `flatMap mixes with lift+ap - dependent fanout`() = runTest {
        val latchB = CompletableDeferred<Unit>()
        val latchC = CompletableDeferred<Unit>()

        val result = Async {
            pure(10).flatMap { base ->
                // After getting the base value, fan out in parallel
                lift2 { b: Int, c: Int -> base + b + c }
                    .ap { latchB.complete(Unit); latchC.await(); base * 2 }
                    .ap { latchC.complete(Unit); latchB.await(); base * 3 }
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
            Computation { latchA.complete(Unit); latchB.await(); "A" }
                .zip(Computation { latchB.complete(Unit); latchA.await(); "B" })
        }

        // Deadlock if sequential -> proves parallelism
        assertEquals("A" to "B", result)
    }

    @Test
    fun `zip with transform combines and maps in parallel`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val result = Async {
            Computation<Int> { latchA.complete(Unit); latchB.await(); 21 }
                .zip(Computation { latchB.complete(Unit); latchA.await(); 21 }) { a, b -> a + b }
        }

        assertEquals(42, result)
    }

    @Test
    fun `traverse parallelizes over a collection - barrier proof`() = runTest {
        val latches = (0 until 5).map { CompletableDeferred<Unit>() }

        val result = Async {
            (0 until 5).toList().traverse { i ->
                Computation {
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
                Computation<String> { latches[0].complete(Unit); latches.awaitOthers(0); "A" },
                Computation { latches[1].complete(Unit); latches.awaitOthers(1); "B" },
                Computation { latches[2].complete(Unit); latches.awaitOthers(2); "C" },
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
                Computation {
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
            emptyList<Int>().traverse { pure(it) }
        }
        assertEquals(emptyList(), result)
    }

    @Test
    fun `race returns the first computation to complete`() = runTest {
        val result = Async {
            race(
                fa = Computation { delay(10_000); "slow" },
                fb = Computation { "fast" }
            )
        }
        assertEquals("fast", result)
    }

    @Test
    fun `race cancels the loser`() = runTest {
        val loserCancelled = CompletableDeferred<Boolean>()

        val result = Async {
            race(
                fa = Computation {
                    try {
                        awaitCancellation()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        loserCancelled.complete(true)
                        throw e
                    }
                },
                fb = Computation { "winner" }
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
            lift7 { user: UserProfile, notifications: NotificationList, prefs: Preferences,
                    feed: FeedContent, suggestions: PeopleSuggestions,
                    recommendations: Recommendations, badges: FeatureFlags ->
                DashboardSummary(user, notifications, prefs, feed, suggestions, recommendations, badges)
            }
                .ap { fetchUser() }
                .ap { fetchNotifications() }
                .ap { fetchPrefs() }
                .ap { fetchFeed() }
                .ap { fetchSuggestions() }
                .ap { fetchRecommendations() }
                .ap { fetchBadges() }
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
            lift7 { user: UserProfile, cart: ShoppingCart, promos: PromotionBundle, stock: StockConfirmation,
                    shipping: ShippingQuote, tax: TaxBreakdown, payment: PaymentAuth ->
                CheckoutPhased(user, cart, promos, stock, shipping, tax, payment)
            }
                .ap { fetchUser() }
                .ap { fetchCart() }
                .ap { fetchPromos() }
                .followedBy { validateStock() }
                .ap { calcShipping() }
                .ap { calcTax() }
                .followedBy { reservePayment() }
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
            lift3 { user: String, count: Int, items: List<String> ->
                "$user|$count|${items.joinToString(",")}"
            }
                .ap { fetchFromRetrofit() }
                .ap { queryFromRoom() }
                .ap { callKtorClient() }
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
            lift9 { user: UserProfile, prefs: Preferences, flags: FeatureFlags,
                    auth: AuthToken, feed: FeedContent,
                    notifications: NotificationList, messages: MessageSummary,
                    recommendations: Recommendations, trending: TrendingTopics ->
                FullPageLoad(user, prefs, flags, auth, feed, notifications, messages, recommendations, trending)
            }
                .ap { fetchUser() }
                .ap { fetchPrefs() }
                .ap { fetchFlags() }
                .followedBy { loadAuth() }
                .followedBy { loadFeed() }
                .ap { loadNotifications() }
                .ap { loadMessages() }
                .ap { loadRecommendations() }
                .followedBy { loadTrending() }
        }

        assertEquals(traditional, dsl)
    }
}
