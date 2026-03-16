package applicative

import arrow.core.NonEmptyList as ArrowNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.fx.coroutines.parZip
import applicative.Either as AppEither
import applicative.Nel as AppNel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// ════════════════════════════════════════════════════════════════════════════
// Three-Way Comparison Test
//
// Each scenario is implemented three ways:
//   1. Raw Coroutines  (manual async/await)
//   2. Arrow           (parZip / zipOrAccumulate)
//   3. This Library     (lift + ap / liftV + apV)
//
// The purpose is visual clarity: the same problem, three styles, side by side.
// ════════════════════════════════════════════════════════════════════════════

// ── Shared stub functions ─────────────────────────────────────────────────

private suspend fun fetchUser() = UserProfile("Alice", 1)
private suspend fun fetchCart() = ShoppingCart(3, 99.99)
private suspend fun fetchPromos() = PromotionBundle("SAVE20", 20)
private suspend fun calcShipping() = ShippingQuote(5.99, "ground")
private suspend fun calcTax() = TaxBreakdown(8.50, 0.085)
private suspend fun fetchInventory() = InventorySnapshot(true)
private suspend fun validateStock() = StockConfirmation(true)
private suspend fun reservePayment() = PaymentAuth("4242", true)

// ── Scenario 1 result type ───────────────────────────────────────────────

private data class SimpleFanout(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val shipping: ShippingQuote,
    val tax: TaxBreakdown,
)

// ── Scenario 2 result types ──────────────────────────────────────────────

private data class Phase1Result(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val inventory: InventorySnapshot,
)

private data class CheckoutPhased(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val inventory: InventorySnapshot,
    val stock: StockConfirmation,
    val shipping: ShippingQuote,
    val tax: TaxBreakdown,
)

// ── Scenario 3 domain types ─────────────────────────────────────────────

private sealed class FormError(val msg: String) {
    class InvalidName(msg: String) : FormError(msg)
    class InvalidEmail(msg: String) : FormError(msg)
    class InvalidAge(msg: String) : FormError(msg)

    override fun equals(other: Any?): Boolean =
        other is FormError && this::class == other::class && msg == other.msg

    override fun hashCode(): Int = this::class.hashCode() * 31 + msg.hashCode()
    override fun toString(): String = "${this::class.simpleName}($msg)"
}

private data class ValidName(val v: String)
private data class ValidEmail(val v: String)
private data class ValidAge(val v: Int)
private data class Registration(val name: ValidName, val email: ValidEmail, val age: ValidAge)

// ── Scenario 3 validators (library types) ────────────────────────────────

private fun validateNameV(input: String): AppEither<AppNel<FormError>, ValidName> =
    if (input.length >= 2) AppEither.Right(ValidName(input))
    else AppEither.Left(AppNel(FormError.InvalidName("Name too short: $input")))

private fun validateEmailV(input: String): AppEither<AppNel<FormError>, ValidEmail> =
    if ("@" in input) AppEither.Right(ValidEmail(input))
    else AppEither.Left(AppNel(FormError.InvalidEmail("Missing @: $input")))

private fun validateAgeV(age: Int): AppEither<AppNel<FormError>, ValidAge> =
    if (age >= 18) AppEither.Right(ValidAge(age))
    else AppEither.Left(AppNel(FormError.InvalidAge("Too young: $age")))

// ── Scenario 3 validators (Arrow types — single error, not Nel) ──────────
// Arrow's zipOrAccumulate accumulates individual E values into NonEmptyList<E>.
// So each validator returns Either<FormError, _> (not Either<Nel<FormError>, _>).

private fun validateNameArrow(input: String): arrow.core.Either<FormError, ValidName> =
    if (input.length >= 2) arrow.core.Either.Right(ValidName(input))
    else arrow.core.Either.Left(FormError.InvalidName("Name too short: $input"))

private fun validateEmailArrow(input: String): arrow.core.Either<FormError, ValidEmail> =
    if ("@" in input) arrow.core.Either.Right(ValidEmail(input))
    else arrow.core.Either.Left(FormError.InvalidEmail("Missing @: $input"))

private fun validateAgeArrow(age: Int): arrow.core.Either<FormError, ValidAge> =
    if (age >= 18) arrow.core.Either.Right(ValidAge(age))
    else arrow.core.Either.Left(FormError.InvalidAge("Too young: $age"))

// ════════════════════════════════════════════════════════════════════════════

class ThreeWayComparisonTest {

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 1: Simple parallel fan-out (5 calls)
    // ════════════════════════════════════════════════════════════════════════

    private val expectedFanout = SimpleFanout(
        user = UserProfile("Alice", 1),
        cart = ShoppingCart(3, 99.99),
        promos = PromotionBundle("SAVE20", 20),
        shipping = ShippingQuote(5.99, "ground"),
        tax = TaxBreakdown(8.50, 0.085),
    )

    @Test
    fun `scenario 1 - raw coroutines - simple fan-out`() = runTest {
        // ── Raw Coroutines (7 lines) ──────────────────────────────────────
        val result = coroutineScope {
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            val dPromos = async { fetchPromos() }
            val dShipping = async { calcShipping() }
            val dTax = async { calcTax() }
            SimpleFanout(dUser.await(), dCart.await(), dPromos.await(), dShipping.await(), dTax.await())
        }

        assertEquals(expectedFanout, result)
    }

    @Test
    fun `scenario 1 - arrow - simple fan-out`() = runTest {
        // ── Arrow parZip (7 lines) ────────────────────────────────────────
        val result = parZip(
            { fetchUser() },
            { fetchCart() },
            { fetchPromos() },
            { calcShipping() },
            { calcTax() },
        ) { user, cart, promos, shipping, tax ->
            SimpleFanout(user, cart, promos, shipping, tax)
        }

        assertEquals(expectedFanout, result)
    }

    @Test
    fun `scenario 1 - this library - simple fan-out`() = runTest {
        // ── This Library (5 lines) ────────────────────────────────────────
        val result = Async {
            lift5(::SimpleFanout)
                .ap { fetchUser() }
                .ap { fetchCart() }
                .ap { fetchPromos() }
                .ap { calcShipping() }
                .ap { calcTax() }
        }

        assertEquals(expectedFanout, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 2: Multi-phase checkout (7 calls, 3 phases)
    //
    //   Phase 1: 4 parallel calls (user, cart, promos, inventory)
    //   Phase 2: barrier (validateStock — sequential)
    //   Phase 3: 2 parallel calls (shipping, tax)
    // ════════════════════════════════════════════════════════════════════════

    private val expectedPhased = CheckoutPhased(
        user = UserProfile("Alice", 1),
        cart = ShoppingCart(3, 99.99),
        promos = PromotionBundle("SAVE20", 20),
        inventory = InventorySnapshot(true),
        stock = StockConfirmation(true),
        shipping = ShippingQuote(5.99, "ground"),
        tax = TaxBreakdown(8.50, 0.085),
    )

    @Test
    fun `scenario 2 - raw coroutines - multi-phase checkout`() = runTest {
        // ── Raw Coroutines (14 lines) ─────────────────────────────────────
        val result = coroutineScope {
            // Phase 1: parallel
            val dUser = async { fetchUser() }
            val dCart = async { fetchCart() }
            val dPromos = async { fetchPromos() }
            val dInventory = async { fetchInventory() }
            val user = dUser.await()
            val cart = dCart.await()
            val promos = dPromos.await()
            val inventory = dInventory.await()

            // Phase 2: barrier
            val stock = validateStock()

            // Phase 3: parallel
            val dShipping = async { calcShipping() }
            val dTax = async { calcTax() }
            CheckoutPhased(user, cart, promos, inventory, stock, dShipping.await(), dTax.await())
        }

        assertEquals(expectedPhased, result)
    }

    @Test
    fun `scenario 2 - arrow - multi-phase checkout`() = runTest {
        // ── Arrow parZip (15 lines) ───────────────────────────────────────
        // Phase 1
        val phase1 = parZip(
            { fetchUser() },
            { fetchCart() },
            { fetchPromos() },
            { fetchInventory() },
        ) { user, cart, promos, inv -> Phase1Result(user, cart, promos, inv) }

        // Phase 2: barrier
        val stock = validateStock()

        // Phase 3
        val result = parZip(
            { calcShipping() },
            { calcTax() },
        ) { shipping, tax ->
            CheckoutPhased(phase1.user, phase1.cart, phase1.promos, phase1.inventory, stock, shipping, tax)
        }

        assertEquals(expectedPhased, result)
    }

    @Test
    fun `scenario 2 - this library - multi-phase checkout`() = runTest {
        // ── This Library (7 lines) ────────────────────────────────────────
        val result = Async {
            lift7(::CheckoutPhased)
                .ap { fetchUser() }            // phase 1: parallel
                .ap { fetchCart() }            // phase 1: parallel
                .ap { fetchPromos() }          // phase 1: parallel
                .ap { fetchInventory() }       // phase 1: parallel
                .followedBy { validateStock() } // phase 2: barrier
                .ap { calcShipping() }         // phase 3: parallel
                .ap { calcTax() }              // phase 3: parallel
        }

        assertEquals(expectedPhased, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO 3: Parallel validation with error accumulation (3 validators)
    //
    //   All three inputs are invalid. Each approach must collect ALL errors.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `scenario 3 - raw coroutines - error accumulation (sequential only)`() = runTest {
        // ── Raw Coroutines (10 lines) ─────────────────────────────────────
        // Raw coroutines CANNOT do parallel error accumulation.
        // This is the best you can do: sequential, nullable, manual collection.
        val errors = mutableListOf<FormError>()
        val name = validateNameV("A").let { r ->
            when (r) {
                is AppEither.Right -> r.value
                is AppEither.Left -> { errors.addAll(r.value); null }
            }
        }
        val email = validateEmailV("bad").let { r ->
            when (r) {
                is AppEither.Right -> r.value
                is AppEither.Left -> { errors.addAll(r.value); null }
            }
        }
        val age = validateAgeV(5).let { r ->
            when (r) {
                is AppEither.Right -> r.value
                is AppEither.Left -> { errors.addAll(r.value); null }
            }
        }

        // Sequential, nullable everywhere, no parallelism, no type safety
        assertEquals(3, errors.size)
        assertEquals(null, name)
        assertEquals(null, email)
        assertEquals(null, age)
    }

    @Test
    fun `scenario 3 - arrow - error accumulation with zipOrAccumulate`() = runTest {
        // ── Arrow zipOrAccumulate (7 lines) ───────────────────────────────
        val result: arrow.core.Either<ArrowNel<FormError>, Registration> = either {
            zipOrAccumulate(
                { validateNameArrow("A").bind() },
                { validateEmailArrow("bad").bind() },
                { validateAgeArrow(5).bind() },
            ) { name, email, age -> Registration(name, email, age) }
        }

        // Verify all 3 errors were accumulated
        val left = assertIs<arrow.core.Either.Left<ArrowNel<FormError>>>(result)
        assertEquals(3, left.value.size)
    }

    @Test
    fun `scenario 3 - this library - error accumulation with liftV+apV`() = runTest {
        // ── This Library (4 lines) ────────────────────────────────────────
        val result = Async {
            liftV3<FormError, ValidName, ValidEmail, ValidAge, Registration>(::Registration)
                .apV { validateNameV("A") }
                .apV { validateEmailV("bad") }
                .apV { validateAgeV(5) }
        }

        // Verify all 3 errors were accumulated
        assertIs<AppEither.Left<AppNel<FormError>>>(result)
        assertEquals(3, result.value.size)
        assertIs<FormError.InvalidName>(result.value[0])
        assertIs<FormError.InvalidEmail>(result.value[1])
        assertIs<FormError.InvalidAge>(result.value[2])
    }

    // ── Scenario 3 success path: verify all three produce the same result ─

    @Test
    fun `scenario 3 - all three agree on valid input`() = runTest {
        val expectedRegistration = Registration(
            name = ValidName("Alice"),
            email = ValidEmail("alice@example.com"),
            age = ValidAge(30),
        )

        // Raw coroutines
        val rawResult = run {
            val name = validateNameV("Alice")
            val email = validateEmailV("alice@example.com")
            val age = validateAgeV(30)
            // All succeed, assemble manually
            Registration(
                (name as AppEither.Right).value,
                (email as AppEither.Right).value,
                (age as AppEither.Right).value,
            )
        }

        // Arrow
        val arrowResult: arrow.core.Either<ArrowNel<FormError>, Registration> = either {
            zipOrAccumulate(
                { validateNameArrow("Alice").bind() },
                { validateEmailArrow("alice@example.com").bind() },
                { validateAgeArrow(30).bind() },
            ) { n, e, a -> Registration(n, e, a) }
        }

        // This library
        val libResult = Async {
            liftV3<FormError, ValidName, ValidEmail, ValidAge, Registration>(::Registration)
                .apV { validateNameV("Alice") }
                .apV { validateEmailV("alice@example.com") }
                .apV { validateAgeV(30) }
        }

        assertEquals(expectedRegistration, rawResult)
        assertIs<arrow.core.Either.Right<Registration>>(arrowResult)
        assertEquals(expectedRegistration, arrowResult.value)
        assertIs<AppEither.Right<Registration>>(libResult)
        assertEquals(expectedRegistration, libResult.value)
    }
}
