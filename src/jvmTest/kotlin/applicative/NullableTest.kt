package applicative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NullableTest {

    // ════════════════════════════════════════════════════════════════════════
    // .apOrNull(null) — literal null
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `apOrNull with null literal passes null to function`() = runTest {
        val result = Async {
            lift2 { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(null)
        }
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .apOrNull(comp) where comp: Computation<A>?
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `apOrNull with nullable Computation - non-null executes`() = runTest {
        val comp: Computation<String>? = pure("yes")

        val result = Async {
            lift2 { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(comp)
        }
        assertEquals("fixed|yes", result)
    }

    @Test
    fun `apOrNull with nullable Computation - null passes null`() = runTest {
        val comp: Computation<String>? = null

        val result = Async {
            lift2 { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(comp)
        }
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mixed chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mixed chain with nullable and non-null`() = runTest {
        val present: Computation<String>? = pure("yes")
        val absent: Computation<String>? = null

        val result = Async {
            lift3 { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(present)
                .apOrNull(absent)
        }

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `chain with literal null and nullable variable`() = runTest {
        val present: Computation<String>? = pure("yes")

        val result = Async {
            lift3 { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(present)
                .apOrNull(null)
        }

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `all null parameters`() = runTest {
        val result = Async {
            lift3 { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .ap { "fixed" }
                .apOrNull(null)
                .apOrNull(null)
        }

        assertEquals("fixed|nil|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // apOrNull with real parallelism + followedBy + flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `apOrNull runs non-null in parallel - barrier proof`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val compA: Computation<String>? = Computation {
            latchA.complete(Unit)
            latchB.await()
            "A"
        }
        val compB: Computation<String>? = Computation {
            latchB.complete(Unit)
            latchA.await()
            "B"
        }

        // Would deadlock if apOrNull ran sequentially
        val result = Async {
            lift3 { a: String?, b: String?, c: String -> "${a ?: "nil"}|${b ?: "nil"}|$c" }
                .apOrNull(compA)
                .apOrNull(compB)
                .ap { "C" }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `apOrNull integrates with followedBy and flatMap`() = runTest {
        val optionalDiscount: Computation<Discount>? = pure(Discount("SUMMER20", 20))
        val noInsurance: Computation<InsurancePlan>? = null

        data class BookingDetails(
            val user: UserProfile,
            val cart: CartSummary,
            val discount: Discount?,
            val insurance: InsurancePlan?,
            val total: OrderTotal,
        )

        val result = Async {
            lift5(::BookingDetails)
                .ap { UserProfile("Alice", 42) }
                .ap { CartSummary(3) }
                .apOrNull(optionalDiscount)
                .apOrNull(noInsurance)
                .followedBy { OrderTotal(42.0) }
        }

        assertEquals(BookingDetails(
            user = UserProfile("Alice", 42),
            cart = CartSummary(3),
            discount = Discount("SUMMER20", 20),
            insurance = null,
            total = OrderTotal(42.0),
        ), result)
    }
}
