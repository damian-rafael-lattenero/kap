package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NullableTest {

    // ════════════════════════════════════════════════════════════════════════
    // .withOrNull(null) — literal null
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withOrNull with null literal passes null to function`() = runTest {
        val result = Kap.of { a: String -> { b: String? -> "$a|${b ?: "nil"}" } }
                .with { "fixed" }
                .withOrNull(null).evalGraph()
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .withOrNull(comp) where comp: Kap<A>?
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withOrNull with nullable Kap - non-null executes`() = runTest {
        val comp: Kap<String>? = Kap.of("yes")

        val result = Kap.of { a: String -> { b: String? -> "$a|${b ?: "nil"}" } }
                .with { "fixed" }
                .withOrNull(comp).evalGraph()
        assertEquals("fixed|yes", result)
    }

    @Test
    fun `withOrNull with nullable Kap - null passes null`() = runTest {
        val comp: Kap<String>? = null

        val result = Kap.of { a: String -> { b: String? -> "$a|${b ?: "nil"}" } }
                .with { "fixed" }
                .withOrNull(comp).evalGraph()
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mixed chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mixed chain with nullable and non-null`() = runTest {
        val present: Kap<String>? = Kap.of("yes")
        val absent: Kap<String>? = null

        val result = Kap.of { a: String -> { b: String? -> { c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" } } }
                .with { "fixed" }
                .withOrNull(present)
                .withOrNull(absent).evalGraph()

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `chain with literal null and nullable variable`() = runTest {
        val present: Kap<String>? = Kap.of("yes")

        val result = Kap.of { a: String -> { b: String? -> { c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" } } }
                .with { "fixed" }
                .withOrNull(present)
                .withOrNull(null).evalGraph()

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `all null parameters`() = runTest {
        val result = Kap.of { a: String -> { b: String? -> { c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" } } }
                .with { "fixed" }
                .withOrNull(null)
                .withOrNull(null).evalGraph()

        assertEquals("fixed|nil|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // withOrNull with real parallelism + then + andThen
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withOrNull runs non-null in parallel - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val compA: Kap<String>? = Kap {
            latchA.complete(Unit)
            latchB.await()
            "A"
        }
        val compB: Kap<String>? = Kap {
            latchB.complete(Unit)
            latchA.await()
            "B"
        }

        // Would deadlock if withOrNull ran sequentially
        val result = Kap.of { a: String? -> { b: String? -> { c: String -> "${a ?: "nil"}|${b ?: "nil"}|$c" } } }
                .withOrNull(compA)
                .withOrNull(compB)
                .with { "C" }.evalGraph()

        assertEquals("A|B|C", result)
    }

    @Test
    fun `withOrNull integrates with then and andThen`() = runTest {
        val optionalDiscount: Kap<Discount>? = Kap.of(Discount("SUMMER20", 20))
        val noInsurance: Kap<InsurancePlan>? = null

        data class BookingDetails(
            val user: UserProfile,
            val cart: CartSummary,
            val discount: Discount?,
            val insurance: InsurancePlan?,
            val total: OrderTotal,
        )

        val result = Kap.of { user: UserProfile -> { cart: CartSummary -> { discount: Discount? -> { insurance: InsurancePlan? -> { total: OrderTotal -> BookingDetails(user, cart, discount, insurance, total) } } } } }
                .with { UserProfile("Alice", 42) }
                .with { CartSummary(3) }
                .withOrNull(optionalDiscount)
                .withOrNull(noInsurance)
                .then { OrderTotal(42.0) }.evalGraph()

        assertEquals(BookingDetails(
            user = UserProfile("Alice", 42),
            cart = CartSummary(3),
            discount = Discount("SUMMER20", 20),
            insurance = null,
            total = OrderTotal(42.0),
        ), result)
    }
}
