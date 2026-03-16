package applicative

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Side-by-side comparison: this library vs raw coroutines.
 *
 * Each test implements the SAME scenario two ways:
 * 1. Raw coroutines (async/await) — the baseline
 * 2. This library's DSL (lift + ap + followedBy)
 *
 * Every .ap call returns a DISTINCT domain type so the compiler
 * enforces correct parameter ordering — swap two .ap lines and
 * it won't compile.
 */
class BenchmarkComparisonTest {

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 1: E-commerce checkout — 11 calls, 4 phases
    //
    //   Phase 1: fetch user, cart, promos, inventory (parallel)
    //   Phase 2: validate stock (sequential barrier)
    //   Phase 3: calc shipping, tax, discounts (parallel)
    //   Phase 4: reserve payment (sequential barrier)
    //   Phase 5: generate confirmation, send email (parallel)
    // ════════════════════════════════════════════════════════════════════════

    private fun fetchUser() = UserProfile(name = "Alice", id = 42)
    private fun fetchCart() = ShoppingCart(items = 3, total = 59.97)
    private fun fetchPromos() = PromotionBundle(code = "SAVE20", discountPct = 20)
    private fun fetchInventory() = InventorySnapshot(allInStock = true)
    private fun validateStock() = StockConfirmation(confirmed = true)
    private fun calcShipping() = ShippingQuote(amount = 5.00, method = "express")
    private fun calcTax() = TaxBreakdown(amount = 2.50, rate = 0.08)
    private fun calcDiscounts() = DiscountSummary(amount = 4.00, promoApplied = "SAVE20")
    private fun reservePayment() = PaymentAuth(cardLast4 = "4242", authorized = true)
    private fun generateConfirmation() = OrderConfirmation(orderId = "order-#9001")
    private fun sendEmail() = EmailReceipt(sentTo = "alice@example.com", orderId = "order-#9001")

    private val checkoutExpected = CheckoutResult(
        user = UserProfile(name = "Alice", id = 42),
        cart = ShoppingCart(items = 3, total = 59.97),
        promos = PromotionBundle(code = "SAVE20", discountPct = 20),
        inventory = InventorySnapshot(allInStock = true),
        stock = StockConfirmation(confirmed = true),
        shipping = ShippingQuote(amount = 5.00, method = "express"),
        tax = TaxBreakdown(amount = 2.50, rate = 0.08),
        discounts = DiscountSummary(amount = 4.00, promoApplied = "SAVE20"),
        payment = PaymentAuth(cardLast4 = "4242", authorized = true),
        confirmation = OrderConfirmation(orderId = "order-#9001"),
        email = EmailReceipt(sentTo = "alice@example.com", orderId = "order-#9001"),
    )

    @Test
    fun `scenario 1 - raw coroutines - 11 calls 4 phases`() = runTest {
        // ── Raw: 25 lines, manual phase management, implicit structure ──
        val result = coroutineScope {
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            val dPromos = async { fetchPromos() }
            val dInventory = async { fetchInventory() }
            val user = dUser.await()
            val cart = dCart.await()
            val promos = dPromos.await()
            val inventory = dInventory.await()

            val stock = validateStock()

            val dShipping = async { calcShipping() }
            val dTax = async { calcTax() }
            val dDiscounts = async { calcDiscounts() }
            val shipping = dShipping.await()
            val tax = dTax.await()
            val discounts = dDiscounts.await()

            val payment = reservePayment()

            val dConfirmation = async { generateConfirmation() }
            val dEmail = async { sendEmail() }
            val confirmation = dConfirmation.await()
            val email = dEmail.await()

            CheckoutResult(user, cart, promos, inventory, stock,
                           shipping, tax, discounts, payment,
                           confirmation, email)
        }

        assertEquals(checkoutExpected, result)
    }

    @Test
    fun `scenario 1 - this library - 11 calls 4 phases`() = runTest {
        // ── This library: 12 lines, the code IS the execution plan ──
        val result = Async {
            lift11(::CheckoutResult)
                .ap { fetchUser() }              // ┐ phase 1: parallel
                .ap { fetchCart() }              // │
                .ap { fetchPromos() }            // │
                .ap { fetchInventory() }         // ┘
                .followedBy { validateStock() }  // ── phase 2: barrier
                .ap { calcShipping() }           // ┐ phase 3: parallel
                .ap { calcTax() }               // │
                .ap { calcDiscounts() }          // ┘
                .followedBy { reservePayment() } // ── phase 4: barrier
                .ap { generateConfirmation() }   // ┐ phase 5: parallel
                .ap { sendEmail() }             // ┘
        }

        assertEquals(checkoutExpected, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 2: Flight booking with validation — error accumulation
    //
    //   Phase 1: validate passport, seat, payment (parallel, errors accumulate)
    //   Phase 2: confirm availability (sequential barrier)
    //   Phase 3: extras (parallel)
    // ════════════════════════════════════════════════════════════════════════

    private sealed class BookingError(val msg: String) {
        class InvalidPassport(msg: String) : BookingError(msg)
        class SeatUnavailable(msg: String) : BookingError(msg)
        class PaymentDeclined(msg: String) : BookingError(msg)
    }

    private fun validatePassport(p: String): Either<Nel<BookingError>, PassportId> =
        if (p.isNotEmpty()) Either.Right(PassportId(p))
        else Either.Left(BookingError.InvalidPassport("empty").nel())

    private fun validateSeat(s: String): Either<Nel<BookingError>, SeatAssignment> =
        if (s.matches(Regex("\\d+[A-F]"))) Either.Right(SeatAssignment(s))
        else Either.Left(BookingError.SeatUnavailable("bad seat").nel())

    private fun validatePayment(p: String): Either<Nel<BookingError>, PaymentConfirmation> =
        if (p.startsWith("visa")) Either.Right(PaymentConfirmation(p))
        else Either.Left(BookingError.PaymentDeclined("not visa").nel())

    private val bookingExpected = BookingResult(
        passport = PassportId("ABC123"),
        seat = SeatAssignment("14A"),
        payment = PaymentConfirmation("visa-ok"),
        availability = FlightAvailability("confirmed"),
        extras = LoungeAccess("lounge-B"),
    )

    @Test
    fun `scenario 2 - raw coroutines - validation with parallel is impossible`() = runTest {
        // Raw coroutines CANNOT accumulate errors from parallel branches.
        // Structured concurrency cancels siblings on first failure.
        // You MUST validate sequentially to collect all errors:
        val errors = mutableListOf<BookingError>()

        val passport = validatePassport("ABC123").fold({ errors.addAll(it); null }, { it })
        val seat = validateSeat("14A").fold({ errors.addAll(it); null }, { it })
        val payment = validatePayment("visa-ok").fold({ errors.addAll(it); null }, { it })

        // Sequential only, no parallelism, nullable types everywhere
        if (errors.isEmpty() && passport != null && seat != null && payment != null) {
            val availability = FlightAvailability("confirmed")
            val extras = LoungeAccess("lounge-B")
            val result = BookingResult(passport, seat, payment, availability, extras)
            assertEquals(bookingExpected, result)
        }

        assertEquals(0, errors.size)
    }

    @Test
    fun `scenario 2 - this library - validation plus phases with liftV+apV`() = runTest {
        val result = Async {
            liftV5<BookingError, PassportId, SeatAssignment, PaymentConfirmation, FlightAvailability, LoungeAccess, BookingResult>(::BookingResult)
                .apV { validatePassport("ABC123") }
                .apV { validateSeat("14A") }
                .apV { validatePayment("visa-ok") }
                .followedByV(valid(FlightAvailability("confirmed")))
                .apV(valid(LoungeAccess("lounge-B")))
        }

        assertEquals(Either.Right(bookingExpected), result)
    }

    @Test
    fun `scenario 2 - this library - all validations fail`() = runTest {
        fun failPassport(p: String): Either<Nel<BookingError>, PassportId> =
            Either.Left(BookingError.InvalidPassport("expired").nel())
        fun failSeat(s: String): Either<Nel<BookingError>, SeatAssignment> =
            Either.Left(BookingError.SeatUnavailable("taken").nel())
        fun failPayment(p: String): Either<Nel<BookingError>, PaymentConfirmation> =
            Either.Left(BookingError.PaymentDeclined("declined").nel())

        val result = Async {
            liftV5<BookingError, PassportId, SeatAssignment, PaymentConfirmation, FlightAvailability, LoungeAccess, BookingResult>(::BookingResult)
                .apV { failPassport("ABC123") }
                .apV { failSeat("14A") }
                .apV { failPayment("visa-ok") }
                .followedByV(valid(FlightAvailability("confirmed")))
                .apV(valid(LoungeAccess("lounge-B")))
        }

        // ALL THREE errors accumulated, not just the first
        assertIs<Either.Left<Nel<BookingError>>>(result)
        assertEquals(3, result.value.size)
        assertIs<BookingError.InvalidPassport>(result.value[0])
        assertIs<BookingError.SeatUnavailable>(result.value[1])
        assertIs<BookingError.PaymentDeclined>(result.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 3: Dashboard aggregation — 14 calls, 5 phases
    //
    //   Phase 1: user, prefs, feature-flags (parallel)
    //   Phase 2: authorize (barrier)
    //   Phase 3: feed, notifications, messages, recommendations (parallel)
    //   Phase 4: analytics enrichment (barrier)
    //   Phase 5: trending, suggestions, ads, social-proof, app-version (parallel)
    // ════════════════════════════════════════════════════════════════════════

    private fun fetchDashboardUser() = UserProfile(name = "Alice", id = 42)
    private fun fetchPrefs() = Preferences(theme = "dark", layout = "compact")
    private fun fetchFlags() = FeatureFlags(flags = mapOf("beta" to true))
    private fun authorize() = AuthToken(token = "tok-abc", scope = "read")
    private fun fetchFeed() = FeedContent(posts = listOf("post1", "post2"))
    private fun fetchNotifications() = NotificationList(unread = 3)
    private fun fetchMessages() = MessageSummary(conversations = 5)
    private fun fetchRecommendations() = Recommendations(suggested = listOf("rec1", "rec2"))
    private fun enrichAnalytics() = AnalyticsEnrichment(segment = "power-user")
    private fun fetchTrending() = TrendingTopics(tags = listOf("kotlin", "coroutines"))
    private fun fetchSuggestions() = PeopleSuggestions(count = 8)
    private fun fetchAds() = AdSlots(content = "sponsored-banner")
    private fun fetchSocial() = SocialProof(likesToday = 142)
    private fun fetchAppVersion() = AppVersion(version = "v2.1.0")

    private val dashboardExpected = DashboardView(
        user = UserProfile(name = "Alice", id = 42),
        prefs = Preferences(theme = "dark", layout = "compact"),
        flags = FeatureFlags(flags = mapOf("beta" to true)),
        auth = AuthToken(token = "tok-abc", scope = "read"),
        feed = FeedContent(posts = listOf("post1", "post2")),
        notifications = NotificationList(unread = 3),
        messages = MessageSummary(conversations = 5),
        recommendations = Recommendations(suggested = listOf("rec1", "rec2")),
        analytics = AnalyticsEnrichment(segment = "power-user"),
        trending = TrendingTopics(tags = listOf("kotlin", "coroutines")),
        suggestions = PeopleSuggestions(count = 8),
        ads = AdSlots(content = "sponsored-banner"),
        social = SocialProof(likesToday = 142),
        appVersion = AppVersion(version = "v2.1.0"),
    )

    @Test
    fun `scenario 3 - raw coroutines - 14 calls 5 phases`() = runTest {
        // ── Raw: 31 lines, phase structure buried in whitespace ──
        val result = coroutineScope {
            val dUser = async { fetchDashboardUser() }
            val dPrefs = async { fetchPrefs() }
            val dFlags = async { fetchFlags() }
            val user = dUser.await()
            val prefs = dPrefs.await()
            val flags = dFlags.await()

            val auth = authorize()

            val dFeed = async { fetchFeed() }
            val dNotif = async { fetchNotifications() }
            val dMsgs = async { fetchMessages() }
            val dRecs = async { fetchRecommendations() }
            val feed = dFeed.await()
            val notif = dNotif.await()
            val msgs = dMsgs.await()
            val recs = dRecs.await()

            val analytics = enrichAnalytics()

            val dTrending = async { fetchTrending() }
            val dSuggestions = async { fetchSuggestions() }
            val dAds = async { fetchAds() }
            val dSocial = async { fetchSocial() }
            val dAppVersion = async { fetchAppVersion() }
            val trending = dTrending.await()
            val suggestions = dSuggestions.await()
            val ads = dAds.await()
            val social = dSocial.await()
            val appVersion = dAppVersion.await()

            DashboardView(user, prefs, flags, auth, feed, notif, msgs, recs,
                          analytics, trending, suggestions, ads, social,
                          appVersion)
        }

        assertEquals(dashboardExpected, result)
    }

    @Test
    fun `scenario 3 - this library - 14 calls 5 phases`() = runTest {
        // ── This library: 16 lines, the SHAPE shows the architecture ──
        val result = Async {
            lift14(::DashboardView)
                .ap { fetchDashboardUser() }       // ┐ phase 1: parallel
                .ap { fetchPrefs() }               // │
                .ap { fetchFlags() }               // ┘
                .followedBy { authorize() }        // ── phase 2: barrier
                .ap { fetchFeed() }                // ┐ phase 3: parallel
                .ap { fetchNotifications() }       // │
                .ap { fetchMessages() }            // │
                .ap { fetchRecommendations() }     // ┘
                .followedBy { enrichAnalytics() }  // ── phase 4: barrier
                .ap { fetchTrending() }            // ┐ phase 5: parallel
                .ap { fetchSuggestions() }         // │
                .ap { fetchAds() }                 // │
                .ap { fetchSocial() }              // │
                .ap { fetchAppVersion() }          // ┘
        }

        assertEquals(dashboardExpected, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LINE COUNT SUMMARY
    //
    //   Scenario                    | Raw   | This lib | Savings
    //   ────────────────────────────┼───────┼──────────┼────────
    //   11 calls, 4 phases          |  25   |   12     |  52%
    //   5 calls + validation        |  12*  |   6      |  type-safe
    //   14 calls, 5 phases          |  31   |   16     |  48%
    //
    //   * Raw coroutines can't do parallel + validation, so it's
    //     sequential-only (and loses type safety with nullable types).
    //
    //   Key insight: as the number of phases grows, the savings
    //   compound. Raw adds O(n) ceremony per phase; this
    //   library adds exactly 1 line per call regardless.
    //   Additionally, every .ap returns a DISTINCT domain type,
    //   so the compiler catches parameter-ordering bugs at compile time.
    // ════════════════════════════════════════════════════════════════════════
}
