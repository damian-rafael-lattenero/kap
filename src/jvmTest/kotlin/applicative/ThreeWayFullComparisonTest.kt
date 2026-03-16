package applicative

import arrow.core.NonEmptyList as ArrowNel
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.parZip
import applicative.Either as AppEither
import applicative.Nel as AppNel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ════════════════════════════════════════════════════════════════════════════════
// Full Three-Way Comparison Test
//
// Every major operation implemented three ways:
//   1. Raw Coroutines  — manual async/await, standard Kotlin
//   2. Arrow           — parZip / zipOrAccumulate / arrow-fx
//   3. This Library    — lift + ap / liftV + apV / combinators
//
// Each test shows the same problem solved three ways side by side.
// The goal is to demonstrate where this library adds value and where
// raw coroutines or Arrow may be simpler.
// ════════════════════════════════════════════════════════════════════════════════

// ── Shared stubs ────────────────────────────────────────────────────────────

private suspend fun fetchUser(): UserProfile { delay(50.milliseconds); return UserProfile("Alice", 1) }
private suspend fun fetchCart(): ShoppingCart { delay(50.milliseconds); return ShoppingCart(3, 99.99) }
private suspend fun fetchPromos(): PromotionBundle { delay(50.milliseconds); return PromotionBundle("SAVE20", 20) }
private suspend fun calcShipping(): ShippingQuote { delay(50.milliseconds); return ShippingQuote(5.99, "ground") }
private suspend fun calcTax(): TaxBreakdown { delay(50.milliseconds); return TaxBreakdown(8.50, 0.085) }
private suspend fun fetchInventory(): InventorySnapshot { delay(50.milliseconds); return InventorySnapshot(true) }
private suspend fun validateStock(): StockConfirmation { delay(50.milliseconds); return StockConfirmation(true) }
private suspend fun reservePayment(): PaymentAuth { delay(50.milliseconds); return PaymentAuth("4242", true) }
private suspend fun fetchPrefs(): Preferences { delay(50.milliseconds); return Preferences("dark", "grid") }
private suspend fun fetchFlags(): FeatureFlags { delay(50.milliseconds); return FeatureFlags(mapOf("beta" to true)) }

// Stubs that fail
private suspend fun fetchUserSlow(): UserProfile { delay(500.milliseconds); return UserProfile("Slow", 99) }
private suspend fun fetchUserFailing(): UserProfile { throw RuntimeException("network error") }
private var attemptCount = 0
private suspend fun fetchUserFlaky(): UserProfile {
    attemptCount++
    if (attemptCount < 3) throw RuntimeException("flaky attempt $attemptCount")
    return UserProfile("Recovered", 1)
}

// Validation domain types (prefixed to avoid clashing with other test files)
private sealed class FErr(val msg: String) {
    class Name(msg: String) : FErr(msg)
    class Email(msg: String) : FErr(msg)
    class Age(msg: String) : FErr(msg)
    class Phone(msg: String) : FErr(msg)

    override fun equals(other: Any?) = other is FErr && this::class == other::class && msg == other.msg
    override fun hashCode() = this::class.hashCode() * 31 + msg.hashCode()
    override fun toString() = "${this::class.simpleName}($msg)"
}

private data class VName(val v: String)
private data class VEmail(val v: String)
private data class VAge(val v: Int)
private data class VPhone(val v: String)
private data class Reg3(val name: VName, val email: VEmail, val age: VAge)
private data class Reg4(val name: VName, val email: VEmail, val age: VAge, val phone: VPhone)

// Library validators (return Either<Nel<E>, A>)
private fun valName(s: String): AppEither<AppNel<FErr>, VName> =
    if (s.length >= 2) AppEither.Right(VName(s)) else AppEither.Left(AppNel(FErr.Name("too short: $s")))
private fun valEmail(s: String): AppEither<AppNel<FErr>, VEmail> =
    if ("@" in s) AppEither.Right(VEmail(s)) else AppEither.Left(AppNel(FErr.Email("no @: $s")))
private fun valAge(n: Int): AppEither<AppNel<FErr>, VAge> =
    if (n >= 18) AppEither.Right(VAge(n)) else AppEither.Left(AppNel(FErr.Age("too young: $n")))
private fun valPhone(s: String): AppEither<AppNel<FErr>, VPhone> =
    if (s.length >= 10) AppEither.Right(VPhone(s)) else AppEither.Left(AppNel(FErr.Phone("too short: $s")))

// Arrow validators (return Either<E, A> — Arrow accumulates into Nel automatically)
private fun valNameArrow(s: String): arrow.core.Either<FErr, VName> =
    if (s.length >= 2) arrow.core.Either.Right(VName(s)) else arrow.core.Either.Left(FErr.Name("too short: $s"))
private fun valEmailArrow(s: String): arrow.core.Either<FErr, VEmail> =
    if ("@" in s) arrow.core.Either.Right(VEmail(s)) else arrow.core.Either.Left(FErr.Email("no @: $s"))
private fun valAgeArrow(n: Int): arrow.core.Either<FErr, VAge> =
    if (n >= 18) arrow.core.Either.Right(VAge(n)) else arrow.core.Either.Left(FErr.Age("too young: $n"))
private fun valPhoneArrow(s: String): arrow.core.Either<FErr, VPhone> =
    if (s.length >= 10) arrow.core.Either.Right(VPhone(s)) else arrow.core.Either.Left(FErr.Phone("too short: $s"))


@OptIn(ExperimentalCoroutinesApi::class)
class ThreeWayFullComparisonTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. MAP — Transform the result of a computation
    //
    // Raw Coroutines:  val x = fetchUser(); x.copy(name = x.name.uppercase())
    // Arrow:           not applicable (Arrow doesn't wrap computations)
    // This Library:    pure(42).map { it * 2 }
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map - raw coroutines - transform result inline`() = runTest {
        // ── Raw: just call the function and transform ──────────────────────
        val user = fetchUser()
        val result = user.copy(name = user.name.uppercase())

        assertEquals("ALICE", result.name)
    }

    @Test
    fun `map - arrow - no direct equivalent, just transform`() = runTest {
        // ── Arrow: no Computation wrapper, so just call and transform ──────
        val user = fetchUser()
        val result = user.copy(name = user.name.uppercase())

        assertEquals("ALICE", result.name)
    }

    @Test
    fun `map - this library - transforms inside computation chain`() = runTest {
        // ── This Library: map composes lazily ──────────────────────────────
        // Value: map is useful when the computation is part of a larger chain
        // and you want to transform before passing to the next combinator.
        val result = Async {
            Computation { fetchUser() }
                .map { it.copy(name = it.name.uppercase()) }
        }

        assertEquals("ALICE", result.name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. FLATMAP — Value-dependent sequential composition
    //
    // Fetch user, then use their ID to fetch their friends.
    // The second call DEPENDS on the first's result.
    // ════════════════════════════════════════════════════════════════════════

    data class FriendList(val friends: List<String>)
    private suspend fun fetchFriends(userId: Long): FriendList {
        delay(50.milliseconds); return FriendList(listOf("Bob", "Charlie"))
    }

    @Test
    fun `flatMap - raw coroutines - sequential dependency`() = runTest {
        // ── Raw: natural sequential code ───────────────────────────────────
        val user = fetchUser()
        val friends = fetchFriends(user.id)

        assertEquals(listOf("Bob", "Charlie"), friends.friends)
    }

    @Test
    fun `flatMap - arrow - no direct equivalent for suspend chains`() = runTest {
        // ── Arrow: same as raw — just sequential suspend calls ─────────────
        val user = fetchUser()
        val friends = fetchFriends(user.id)

        assertEquals(listOf("Bob", "Charlie"), friends.friends)
    }

    @Test
    fun `flatMap - this library - value-dependent then fan-out`() = runTest {
        // ── This Library: flatMap enables dependent-then-parallel patterns ──
        // Value: after flatMap, you can fan out again with ap.
        // This is impossible with raw coroutines in a single expression.
        data class UserWithFriends(val user: UserProfile, val friends: FriendList, val prefs: Preferences)

        val result = Async {
            Computation { fetchUser() }.flatMap { user ->
                lift3(::UserWithFriends)
                    .ap(pure(user))                      // pass user through
                    .ap { fetchFriends(user.id) }        // depends on user.id
                    .ap { fetchPrefs() }                  // independent, parallel
            }
        }

        assertEquals("Alice", result.user.name)
        assertEquals(listOf("Bob", "Charlie"), result.friends.friends)
        assertEquals("dark", result.prefs.theme)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. ZIP — Combine two computations in parallel
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip - raw coroutines - manual async+await`() = runTest {
        // ── Raw: 4 lines ───────────────────────────────────────────────────
        val result = coroutineScope {
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            dUser.await() to dCart.await()
        }

        assertEquals("Alice", result.first.name)
        assertEquals(3, result.second.items)
    }

    @Test
    fun `zip - arrow - parZip for two`() = runTest {
        // ── Arrow: parZip with 2 args ──────────────────────────────────────
        val result = parZip(
            { fetchUser() },
            { fetchCart() },
        ) { user, cart -> user to cart }

        assertEquals("Alice", result.first.name)
        assertEquals(3, result.second.items)
    }

    @Test
    fun `zip - this library - zip operator`() = runTest {
        // ── This Library: single operator ──────────────────────────────────
        val result = Async {
            Computation { fetchUser() }.zip(Computation { fetchCart() })
        }

        assertEquals("Alice", result.first.name)
        assertEquals(3, result.second.items)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. TRAVERSE — Map over a collection in parallel
    //
    // Given a list of user IDs, fetch all profiles concurrently.
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun fetchById(id: Long): UserProfile {
        delay(50.milliseconds); return UserProfile("User$id", id)
    }

    @Test
    fun `traverse - raw coroutines - manual async over list`() = runTest {
        // ── Raw: map + async + awaitAll ────────────────────────────────────
        val ids = listOf(1L, 2L, 3L)
        val results = coroutineScope {
            ids.map { id -> async { fetchById(id) } }.map { it.await() }
        }

        assertEquals(3, results.size)
        assertEquals("User1", results[0].name)
        assertEquals("User3", results[2].name)
    }

    @Test
    fun `traverse - arrow - parMap`() = runTest {
        // ── Arrow: no built-in parTraverse in arrow-fx-coroutines 1.2.x ────
        // Arrow 1.x removed parTraverse; you'd use parZip or manual async.
        // So in practice, it's the same as raw coroutines for collections.
        val ids = listOf(1L, 2L, 3L)
        val results = coroutineScope {
            ids.map { id -> async { fetchById(id) } }.map { it.await() }
        }

        assertEquals(3, results.size)
        assertEquals("User1", results[0].name)
    }

    @Test
    fun `traverse - this library - traverse operator`() = runTest {
        // ── This Library: one-liner, preserves order ───────────────────────
        val ids = listOf(1L, 2L, 3L)
        val results = Async {
            ids.traverse { id -> Computation { fetchById(id) } }
        }

        assertEquals(3, results.size)
        assertEquals("User1", results[0].name)
        assertEquals("User3", results[2].name)
    }

    @Test
    fun `traverse with bounded concurrency - this library`() = runTest {
        // ── This Library: concurrency-limited traverse ─────────────────────
        // Raw coroutines need a manual Semaphore. Arrow doesn't have this.
        val ids = (1L..20L).toList()
        val results = Async {
            ids.traverse(concurrency = 5) { id -> Computation { fetchById(id) } }
        }

        assertEquals(20, results.size)
        assertEquals("User1", results[0].name)
        assertEquals("User20", results[19].name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. SEQUENCE — Execute a list of computations in parallel
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequence - raw coroutines - map async + awaitAll`() = runTest {
        val computations = listOf(
            suspend { fetchUser() },
            suspend { UserProfile("Bob", 2) },
            suspend { UserProfile("Charlie", 3) },
        )
        val results = coroutineScope {
            computations.map { fn -> async { fn() } }.map { it.await() }
        }

        assertEquals(3, results.size)
        assertEquals("Alice", results[0].name)
    }

    @Test
    fun `sequence - this library - sequence operator`() = runTest {
        val computations = listOf(
            Computation { fetchUser() },
            pure(UserProfile("Bob", 2)),
            pure(UserProfile("Charlie", 3)),
        )
        val results = Async { computations.sequence() }

        assertEquals(3, results.size)
        assertEquals("Alice", results[0].name)
        assertEquals("Bob", results[1].name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. RACE — First to succeed wins, loser is cancelled
    //
    // Fetch from primary and fallback; whoever responds first wins.
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun fetchFromPrimary(): String { delay(200.milliseconds); return "primary" }
    private suspend fun fetchFromFallback(): String { delay(50.milliseconds); return "fallback" }

    @Test
    fun `race - raw coroutines - manual select`() = runTest {
        // ── Raw: supervisorScope + select (11 lines) ───────────────────────
        val result = supervisorScope {
            val a = async { fetchFromPrimary() }
            val b = async { fetchFromFallback() }
            val winner = select<String> {
                a.onAwait { it }
                b.onAwait { it }
            }
            a.cancel()
            b.cancel()
            winner
        }

        assertEquals("fallback", result)  // fallback is faster
    }

    @Test
    fun `race - arrow - raceN from arrow-fx`() = runTest {
        // ── Arrow: arrow.fx.coroutines.raceN ───────────────────────────────
        val result = arrow.fx.coroutines.raceN(
            { fetchFromPrimary() },
            { fetchFromFallback() },
        ).fold({ it }, { it })

        assertEquals("fallback", result)
    }

    @Test
    fun `race - this library - race operator`() = runTest {
        // ── This Library: one expression ───────────────────────────────────
        val result = Async {
            race(
                Computation { fetchFromPrimary() },
                Computation { fetchFromFallback() },
            )
        }

        assertEquals("fallback", result)
    }

    @Test
    fun `raceN - this library - three-way race`() = runTest {
        // ── This Library: N-way race, first success wins ───────────────────
        val result = Async {
            raceN(
                Computation { delay(300.milliseconds); "slow" },
                Computation { delay(50.milliseconds); "fast" },
                Computation { delay(200.milliseconds); "medium" },
            )
        }

        assertEquals("fast", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. TIMEOUT — Fail or fallback if computation is too slow
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout - raw coroutines - withTimeout`() = runTest {
        // ── Raw: withTimeoutOrNull ──────────────────────────────────────────
        val result = withTimeoutOrNull(100.milliseconds) { fetchUserSlow() }

        assertEquals(null, result)  // timed out
    }

    @Test
    fun `timeout - arrow - no built-in, use kotlinx directly`() = runTest {
        // ── Arrow: defers to kotlinx.coroutines.withTimeout ────────────────
        val result = withTimeoutOrNull(100.milliseconds) { fetchUserSlow() }

        assertEquals(null, result)
    }

    @Test
    fun `timeout - this library - timeout with default`() = runTest {
        // ── This Library: composable timeout combinator ────────────────────
        val result = Async {
            Computation { fetchUserSlow() }
                .timeout(100.milliseconds, UserProfile("Default", 0))
        }

        assertEquals("Default", result.name)
    }

    @Test
    fun `timeout with fallback computation - this library`() = runTest {
        // ── This Library: timeout that runs a fallback computation ──────────
        val result = Async {
            Computation { fetchUserSlow() }
                .timeout(100.milliseconds, Computation { UserProfile("FromCache", 0) })
        }

        assertEquals("FromCache", result.name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. RECOVER — Catch exceptions and return a fallback value
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recover - raw coroutines - try-catch`() = runTest {
        // ── Raw: manual try-catch ──────────────────────────────────────────
        val result = try {
            fetchUserFailing()
        } catch (e: RuntimeException) {
            UserProfile("Fallback", 0)
        }

        assertEquals("Fallback", result.name)
    }

    @Test
    fun `recover - arrow - no built-in, use try-catch`() = runTest {
        // ── Arrow: same as raw, no built-in recover for suspend ────────────
        val result = try {
            fetchUserFailing()
        } catch (e: RuntimeException) {
            UserProfile("Fallback", 0)
        }

        assertEquals("Fallback", result.name)
    }

    @Test
    fun `recover - this library - composable recover combinator`() = runTest {
        // ── This Library: recover chains naturally ─────────────────────────
        // Value: composes with ap, timeout, retry without nesting try-catch.
        val result = Async {
            Computation<UserProfile> { fetchUserFailing() }
                .recover { UserProfile("Recovered: ${it.message}", 0) }
        }

        assertEquals("Recovered: network error", result.name)
    }

    @Test
    fun `recoverWith - this library - switch to fallback computation`() = runTest {
        // ── This Library: fallback is itself a computation ─────────────────
        val result = Async {
            Computation<UserProfile> { fetchUserFailing() }
                .recoverWith { Computation { UserProfile("FromFallbackService", 0) } }
        }

        assertEquals("FromFallbackService", result.name)
    }

    @Test
    fun `fallback - this library - shorthand for recoverWith`() = runTest {
        val result = Async {
            Computation<UserProfile> { fetchUserFailing() }
                .fallback(pure(UserProfile("Default", 0)))
        }

        assertEquals("Default", result.name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. RETRY — Retry on failure with backoff
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry - raw coroutines - manual loop`() = runTest {
        // ── Raw: manual retry loop (10 lines) ──────────────────────────────
        var attempts = 0
        var lastError: Throwable? = null
        var result: String? = null

        repeat(3) {
            try {
                attempts++
                result = if (attempts < 3) throw RuntimeException("fail $attempts") else "success"
                return@repeat
            } catch (e: RuntimeException) {
                lastError = e
                delay(10.milliseconds)
            }
        }

        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry - arrow - no built-in retry, use Schedule or manual`() = runTest {
        // ── Arrow: arrow.fx.coroutines.Schedule exists but is complex ──────
        // Most teams just write a manual retry loop like raw coroutines.
        var attempts = 0
        var result: String? = null

        repeat(3) {
            try {
                attempts++
                result = if (attempts < 3) throw RuntimeException("fail") else "success"
                return@repeat
            } catch (_: RuntimeException) {
                delay(10.milliseconds)
            }
        }

        assertEquals("success", result)
    }

    @Test
    fun `retry - this library - retry combinator with exponential backoff`() = runTest {
        // ── This Library: one-liner with policy ────────────────────────────
        attemptCount = 0  // reset global counter
        val result = Async {
            Computation { fetchUserFlaky() }
                .retry(maxAttempts = 3, delay = 10.milliseconds, backoff = exponential)
        }

        assertEquals("Recovered", result.name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. MULTI-PHASE ORCHESTRATION — The main differentiator
    //
    //   Phase 1: fetch user + cart + promos (parallel)
    //   Phase 2: validate stock (barrier)
    //   Phase 3: calc shipping + tax (parallel)
    //   Phase 4: reserve payment (barrier)
    //
    // This is WHERE the library truly shines vs raw coroutines and Arrow.
    // ════════════════════════════════════════════════════════════════════════

    private data class FullCheckout(
        val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
        val stock: StockConfirmation,
        val shipping: ShippingQuote, val tax: TaxBreakdown,
        val payment: PaymentAuth,
    )

    private val expectedCheckout = FullCheckout(
        user = UserProfile("Alice", 1), cart = ShoppingCart(3, 99.99),
        promos = PromotionBundle("SAVE20", 20),
        stock = StockConfirmation(true),
        shipping = ShippingQuote(5.99, "ground"), tax = TaxBreakdown(8.50, 0.085),
        payment = PaymentAuth("4242", true),
    )

    @Test
    fun `multi-phase - raw coroutines - 4 phases, 17 lines`() = runTest {
        // ── Raw Coroutines ─────────────────────────────────────────────────
        // 7 calls, 4 phases, 17 lines of plumbing.
        // Phases are invisible without comments.
        // Move one await() above its async and you silently serialize.
        val result = coroutineScope {
            // Phase 1: parallel
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            val dPromos = async { fetchPromos() }
            val user = dUser.await()
            val cart = dCart.await()
            val promos = dPromos.await()

            // Phase 2: barrier
            val stock = validateStock()

            // Phase 3: parallel
            val dShipping = async { calcShipping() }
            val dTax = async { calcTax() }
            val shipping = dShipping.await()
            val tax = dTax.await()

            // Phase 4: barrier
            val payment = reservePayment()

            FullCheckout(user, cart, promos, stock, shipping, tax, payment)
        }

        assertEquals(expectedCheckout, result)
    }

    @Test
    fun `multi-phase - arrow - 4 phases, needs intermediate types`() = runTest {
        // ── Arrow ──────────────────────────────────────────────────────────
        // Arrow needs an intermediate type per phase boundary.
        // Each parZip is a separate expression.
        data class P1(val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle)

        // Phase 1
        val p1 = parZip(
            { fetchUser() }, { fetchCart() }, { fetchPromos() },
        ) { user, cart, promos -> P1(user, cart, promos) }

        // Phase 2: barrier
        val stock = validateStock()

        // Phase 3
        val (shipping, tax) = parZip(
            { calcShipping() }, { calcTax() },
        ) { s, t -> s to t }

        // Phase 4: barrier
        val payment = reservePayment()

        val result = FullCheckout(p1.user, p1.cart, p1.promos, stock, shipping, tax, payment)
        assertEquals(expectedCheckout, result)
    }

    @Test
    fun `multi-phase - this library - 4 phases, 7 lines, flat`() = runTest {
        // ── This Library ───────────────────────────────────────────────────
        // 7 calls, 4 phases, 7 lines. The shape IS the execution plan.
        // ap = parallel. followedBy = wait. No intermediate types.
        val result = Async {
            lift7(::FullCheckout)
                .ap { fetchUser() }             // ┐ phase 1: parallel
                .ap { fetchCart() }             // │
                .ap { fetchPromos() }           // ┘
                .followedBy { validateStock() } // ── phase 2: barrier
                .ap { calcShipping() }          // ┐ phase 3: parallel
                .ap { calcTax() }               // ┘
                .followedBy { reservePayment() } // ── phase 4: barrier
        }

        assertEquals(expectedCheckout, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. ERROR ACCUMULATION — Parallel validation, collect ALL errors
    //
    // All 3 validators fail. Each approach must report ALL errors.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `error accumulation - raw coroutines - sequential, cannot parallelize`() = runTest {
        // ── Raw: sequential, mutable, nullable ─────────────────────────────
        // Raw coroutines CANNOT accumulate errors from parallel branches
        // because async cancels siblings on first failure.
        val errors = mutableListOf<FErr>()
        val name = valName("A").let { when (it) {
            is AppEither.Right -> it.value
            is AppEither.Left -> { errors.addAll(it.value); null }
        }}
        val email = valEmail("bad").let { when (it) {
            is AppEither.Right -> it.value
            is AppEither.Left -> { errors.addAll(it.value); null }
        }}
        val age = valAge(5).let { when (it) {
            is AppEither.Right -> it.value
            is AppEither.Left -> { errors.addAll(it.value); null }
        }}

        // Sequential. Nullable. Manual. No parallelism.
        assertEquals(3, errors.size)
        assertEquals(null, name)
        assertEquals(null, email)
        assertEquals(null, age)
    }

    @Test
    fun `error accumulation - arrow - zipOrAccumulate`() = runTest {
        // ── Arrow: zipOrAccumulate (max 9 args) ────────────────────────────
        val result: arrow.core.Either<ArrowNel<FErr>, Reg3> = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() },
                { valEmailArrow("bad").bind() },
                { valAgeArrow(5).bind() },
            ) { n, e, a -> Reg3(n, e, a) }
        }

        val left = assertIs<arrow.core.Either.Left<ArrowNel<FErr>>>(result)
        assertEquals(3, left.value.size)
    }

    @Test
    fun `error accumulation - this library - zipV`() = runTest {
        // ── This Library: zipV, parallel, all errors collected ──────────────
        val result = Async {
            zipV(
                { valName("A") },
                { valEmail("bad") },
                { valAge(5) },
            ) { n, e, a -> Reg3(n, e, a) }
        }

        val left = assertIs<AppEither.Left<AppNel<FErr>>>(result)
        assertEquals(3, left.value.size)
        assertIs<FErr.Name>(left.value[0])
        assertIs<FErr.Email>(left.value[1])
        assertIs<FErr.Age>(left.value[2])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. ERROR ACCUMULATION — 4+ validators (Arrow needs nesting at 10+)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `error accumulation 4 validators - arrow - zipOrAccumulate`() = runTest {
        // ── Arrow: still flat at 4 args ────────────────────────────────────
        val result: arrow.core.Either<ArrowNel<FErr>, Reg4> = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() },
                { valEmailArrow("bad").bind() },
                { valAgeArrow(5).bind() },
                { valPhoneArrow("123").bind() },
            ) { n, e, a, p -> Reg4(n, e, a, p) }
        }

        val left = assertIs<arrow.core.Either.Left<ArrowNel<FErr>>>(result)
        assertEquals(4, left.value.size)
    }

    @Test
    fun `error accumulation 4 validators - this library - zipV`() = runTest {
        // ── This Library: same pattern, scales to 22 ───────────────────────
        val result = Async {
            zipV(
                { valName("A") },
                { valEmail("bad") },
                { valAge(5) },
                { valPhone("123") },
            ) { n, e, a, p -> Reg4(n, e, a, p) }
        }

        val left = assertIs<AppEither.Left<AppNel<FErr>>>(result)
        assertEquals(4, left.value.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 13. PHASED VALIDATION — Phase 1 validates, phase 2 only if phase 1 passes
    //
    // Phase 1: validate name + email (parallel, accumulate errors)
    // Phase 2: validate age (only runs if phase 1 passes)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `phased validation - raw coroutines - impossible to do well`() = runTest {
        // ── Raw: manual, no parallelism, no error accumulation ─────────────
        val errors = mutableListOf<FErr>()
        val name = valName("A").let { when (it) {
            is AppEither.Right -> it.value; is AppEither.Left -> { errors.addAll(it.value); null }
        }}
        val email = valEmail("bad").let { when (it) {
            is AppEither.Right -> it.value; is AppEither.Left -> { errors.addAll(it.value); null }
        }}

        // Phase gate
        if (errors.isNotEmpty()) {
            assertEquals(2, errors.size) // both name and email failed
            return@runTest
        }
        // Phase 2 would run here if phase 1 passed
    }

    @Test
    fun `phased validation - arrow - zipOrAccumulate + flatMap`() = runTest {
        // ── Arrow: zipOrAccumulate for phase 1, then flatMap for phase 2 ───
        // Arrow requires: either { zipOrAccumulate { } } per phase, manual gating.
        val phase1Result = either {
            zipOrAccumulate(
                { valNameArrow("A").bind() },
                { valEmailArrow("bad").bind() },
            ) { n, e -> "$n|$e" }  // stringified to avoid private-type issues
        }

        // Phase 1 fails → phase 2 never runs
        assertIs<arrow.core.Either.Left<ArrowNel<FErr>>>(phase1Result)
        assertEquals(2, phase1Result.value.size) // name + email errors
    }

    @Test
    fun `phased validation - this library - zipV + flatMapV`() = runTest {
        // ── This Library: zipV for parallel, flatMapV for phase gate ───────
        val result = Async {
            zipV(
                { valName("A") },       // ┐ phase 1: parallel
                { valEmail("bad") },    // ┘ accumulate errors
            ) { n, e -> n to e }
            .flatMapV { (name, email) ->
                // Phase 2: only runs if phase 1 passed
                Computation { valAge(25) }.mapV { age -> Reg3(name, email, age) }
            }
        }

        // Phase 1 fails → phase 2 never runs
        val left = assertIs<AppEither.Left<AppNel<FErr>>>(result)
        assertEquals(2, left.value.size) // name + email errors only
    }

    // ════════════════════════════════════════════════════════════════════════
    // 14. CATCHING — Bridge exceptions into validated error accumulation
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun riskyCall(): String = throw IllegalArgumentException("bad input")

    @Test
    fun `catching - raw coroutines - try-catch to Either manually`() = runTest {
        // ── Raw: manual try-catch, manual Either construction ──────────────
        val result: AppEither<String, String> = try {
            AppEither.Right(riskyCall())
        } catch (e: Exception) {
            AppEither.Left("caught: ${e.message}")
        }

        assertIs<AppEither.Left<String>>(result)
        assertEquals("caught: bad input", result.value)
    }

    @Test
    fun `catching - this library - catching combinator`() = runTest {
        // ── This Library: bridges exceptions into validated world ───────────
        val result = Async {
            Computation<String> { riskyCall() }
                .catching { "caught: ${it.message}" }
        }

        assertIs<AppEither.Left<AppNel<String>>>(result)
        assertEquals("caught: bad input", result.value[0])
    }

    // ════════════════════════════════════════════════════════════════════════
    // 15. VALIDATE — Predicate-based validation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `validate - this library - predicate returns error or null`() = runTest {
        // ── This Library: validate combinator ──────────────────────────────
        val tooShort = Async {
            pure("A").validate { s -> if (s.length < 2) "too short" else null }
        }
        val valid = Async {
            pure("Alice").validate { s -> if (s.length < 2) "too short" else null }
        }

        assertIs<AppEither.Left<AppNel<String>>>(tooShort)
        assertEquals("too short", tooShort.value[0])
        assertIs<AppEither.Right<String>>(valid)
        assertEquals("Alice", valid.value)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 16. TRAVERSE-V — Parallel traverse with error accumulation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverseV - raw coroutines - impossible to accumulate in parallel`() = runTest {
        // ── Raw: can traverse but first error cancels siblings ──────────────
        // valName requires length >= 2; "A" and "B" fail
        val inputs = listOf("Alice", "A", "ok-name", "B")
        val errors = mutableListOf<FErr>()
        inputs.map { input ->
            valName(input).let { when (it) {
                is AppEither.Right -> it.value
                is AppEither.Left -> { errors.addAll(it.value); null }
            }}
        }

        // Sequential, manual, 2 errors collected ("A" and "B")
        assertEquals(2, errors.size)
    }

    @Test
    fun `traverseV - this library - parallel with error accumulation`() = runTest {
        // ── This Library: traverseV ────────────────────────────────────────
        // valName requires length >= 2; "A" and "B" fail
        val inputs = listOf("Alice", "A", "ok-name", "B")
        val result = Async {
            inputs.traverseV { input -> Computation { valName(input) } }
        }

        // "A" and "B" fail → errors accumulated in parallel
        assertIs<AppEither.Left<AppNel<FErr>>>(result)
        assertEquals(2, result.value.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 17. SEQUENCE-V — Execute list of validated computations in parallel
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `sequenceV - this library - parallel sequence with accumulation`() = runTest {
        val computations = listOf(
            Computation { valName("Alice") },     // Right
            Computation { valEmail("bad") },      // Left
            Computation { valName("B") },         // Left
        )
        val result = Async { computations.sequenceV() }

        assertIs<AppEither.Left<AppNel<FErr>>>(result)
        assertEquals(2, result.value.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 18. OR-THROW — Unwrap validated result or throw
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `orThrow - this library - Right returns value`() = runTest {
        val result = Async {
            valid<String, Int>(42).orThrow()
        }

        assertEquals(42, result)
    }

    @Test
    fun `orThrow - this library - Left throws ValidationException`() = runTest {
        val error = runCatching {
            Async {
                invalid<String, Int>("bad").orThrow()
            }
        }

        assertTrue(error.isFailure)
        assertIs<ValidationException>(error.exceptionOrNull())
    }

    // ════════════════════════════════════════════════════════════════════════
    // 19. MAP-ERROR — Unify error types across different validation domains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapError - this library - unify different error types`() = runTest {
        // ── This Library: mapError transforms error types for unification ───
        // Without mapError, you can't combine validators with different error types
        // in the same zipV/apV chain. mapError converts them to a shared type.
        val result = Async {
            invalid<String, Int>("name error").mapError { "wrapped: $it" }
        }

        assertIs<AppEither.Left<AppNel<String>>>(result)
        assertEquals("wrapped: name error", result.value[0])

        // Composing two different error domains into one
        val combined = Async {
            zipV(
                { AppEither.Left<AppNel<String>>(AppNel("name bad")) },
                { AppEither.Left<AppNel<String>>(AppNel("age bad")) },
            ) { n: String, a: String -> "$n-$a" }
        }

        assertIs<AppEither.Left<AppNel<String>>>(combined)
        assertEquals(2, combined.value.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 20. INTEROP — Deferred, Flow, suspend lambda bridges
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `interop - Deferred toComputation - wraps existing async work`() = runTest {
        val result = coroutineScope {
            val deferred = async { fetchUser() }
            Async { deferred.toComputation() }
        }

        assertEquals("Alice", result.name)
    }

    @Test
    fun `interop - Flow firstAsComputation - takes first emission`() = runTest {
        val userFlow = flow {
            emit(UserProfile("FromFlow", 1))
            emit(UserProfile("Second", 2))  // never consumed
        }

        val result = Async { userFlow.firstAsComputation() }
        assertEquals("FromFlow", result.name)
    }

    @Test
    fun `interop - suspend lambda toComputation`() = runTest {
        val suspendFn: suspend () -> UserProfile = { fetchUser() }
        val result = Async { suspendFn.toComputation() }

        assertEquals("Alice", result.name)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 21. CONTEXT / ON — Dispatcher switching
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on - this library - switch dispatcher per computation`() = runTest {
        // ── This Library: each branch can run on different dispatcher ───────
        val result = Async {
            lift2 { a: UserProfile, b: ShoppingCart -> a.name to b.items }
                .ap(Computation { fetchUser() }.on(kotlinx.coroutines.Dispatchers.Default))
                .ap(Computation { fetchCart() }.on(kotlinx.coroutines.Dispatchers.Default))
        }

        assertEquals("Alice" to 3, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 22. TRACED — Observability hooks (new feature)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traced - raw coroutines - manual timing`() = runTest {
        // ── Raw: manual timing around every call ───────────────────────────
        val events = mutableListOf<String>()
        val result = coroutineScope {
            val start = System.nanoTime()
            val user = fetchUser()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            events += "fetchUser: ${elapsed}ms"
            user
        }

        assertEquals("Alice", result.name)
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `traced - this library - composable tracing`() = runTest {
        // ── This Library: traced composes with any combinator ──────────────
        val events = mutableListOf<String>()

        val result = Async {
            lift2 { a: UserProfile, b: ShoppingCart -> a.name to b.items }
                .ap(Computation { fetchUser() }.traced("user",
                    onStart = { events += "start:$it" },
                    onSuccess = { n, d -> events += "done:$n" },
                ))
                .ap(Computation { fetchCart() }.traced("cart",
                    onStart = { events += "start:$it" },
                    onSuccess = { n, d -> events += "done:$n" },
                ))
        }

        assertEquals("Alice" to 3, result)
        assertTrue("start:user" in events)
        assertTrue("start:cart" in events)
        assertTrue("done:user" in events)
        assertTrue("done:cart" in events)
    }

    @Test
    fun `traced with ComputationTracer - this library`() = runTest {
        // ── This Library: structured tracer interface ──────────────────────
        val events = mutableListOf<TraceEvent>()
        val tracer = ComputationTracer { events += it }

        Async {
            lift2 { a: UserProfile, b: ShoppingCart -> a.name to b.items }
                .ap(Computation { fetchUser() }.traced("user", tracer))
                .ap(Computation { fetchCart() }.traced("cart", tracer))
        }

        assertEquals(4, events.size) // 2 starts + 2 successes
        assertTrue(events.filterIsInstance<TraceEvent.Started>().map { it.name }.containsAll(listOf("user", "cart")))
        assertTrue(events.filterIsInstance<TraceEvent.Succeeded>().map { it.name }.containsAll(listOf("user", "cart")))
    }

    // ════════════════════════════════════════════════════════════════════════
    // 23. AP-OR-NULL — Handle optional computations
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `apOrNull - raw coroutines - manual null check`() = runTest {
        // ── Raw: manual null handling ──────────────────────────────────────
        val insurance: (suspend () -> InsurancePlan)? = null
        val result = coroutineScope {
            val user = fetchUser()
            val ins = insurance?.invoke()
            user.name to ins?.provider
        }

        assertEquals("Alice" to null, result)
    }

    @Test
    fun `apOrNull - this library - null-safe in the chain`() = runTest {
        // ── This Library: apOrNull passes null when computation is null ────
        val insurance: Computation<InsurancePlan>? = null

        val result = Async {
            lift2 { user: UserProfile, ins: InsurancePlan? -> user.name to ins?.provider }
                .ap { fetchUser() }
                .apOrNull(insurance)
        }

        assertEquals("Alice" to null, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 24. COMPOSING COMBINATORS — timeout + retry + recover + traced
    //
    // Real-world pattern: try up to 3 times, with timeout per attempt,
    // fall back to cache, and trace everything.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `composed combinators - raw coroutines - deeply nested`() = runTest {
        // ── Raw: manual nesting of try-catch, timeout, retry ───────────────
        var attempts = 0
        val result = run {
            var lastError: Throwable? = null
            var res: UserProfile? = null

            repeat(3) { attempt ->
                try {
                    attempts++
                    res = withTimeoutOrNull(200.milliseconds) {
                        if (attempts < 3) throw RuntimeException("flaky")
                        fetchUser()
                    }
                    if (res != null) return@repeat
                } catch (e: RuntimeException) {
                    lastError = e
                    delay(10.milliseconds)
                }
            }
            res ?: UserProfile("Cache", 0)
        }

        assertNotNull(result)
    }

    @Test
    fun `composed combinators - this library - flat chain`() = runTest {
        // ── This Library: combinators compose without nesting ──────────────
        attemptCount = 0
        val events = mutableListOf<String>()

        val result = Async {
            Computation { fetchUserFlaky() }
                .timeout(200.milliseconds)
                .retry(maxAttempts = 3, delay = 10.milliseconds, backoff = exponential)
                .recover { UserProfile("Cache: ${it.message}", 0) }
                .traced("fetchUser",
                    onStart = { events += "start" },
                    onSuccess = { _, _ -> events += "success" })
        }

        assertNotNull(result)
        assertTrue("start" in events)
        assertTrue("success" in events)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 25. EITHER API — The foundation for validated computations
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `either - swap, bimap, onLeft, onRight, merge`() = runTest {
        // swap
        assertEquals(left(42), right(42).swap())
        assertEquals(right("err"), left("err").swap())

        // bimap
        assertEquals(right("V=42"), right(42).bimap({ "E:$it" }, { "V=$it" }))
        assertEquals(left("E:err"), left("err").bimap({ "E:$it" }, { "V=$it" }))

        // onLeft / onRight
        var captured: String? = null
        left("err").onLeft { captured = it }
        assertEquals("err", captured)
        right(42).onLeft { captured = "wrong" }
        assertEquals("err", captured) // unchanged

        // merge
        assertEquals("hello", right("hello").merge())
        assertEquals("hello", left("hello").merge())
    }

    // ════════════════════════════════════════════════════════════════════════
    // 26. NEL — Non-empty list operations
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `nel - construction, concatenation, list compliance`() = runTest {
        // Construction
        val single = "hello".nel()
        assertEquals(1, single.size)
        assertEquals("hello", single.head)

        val multi = AppNel.of("a", "b", "c")
        assertEquals(3, multi.size)
        assertEquals("a", multi[0])
        assertEquals("c", multi[2])

        // Concatenation preserves order
        val combined = AppNel.of("x", "y") + AppNel.of("z")
        assertEquals(listOf("x", "y", "z"), combined.toList())

        // Never empty
        assertTrue(!single.isEmpty())

        // toString
        assertEquals("Nel(a, b, c)", multi.toString())
    }
}
