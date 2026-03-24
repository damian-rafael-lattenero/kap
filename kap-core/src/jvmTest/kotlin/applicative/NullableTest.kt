package applicative

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
        val result = Async {
            kap { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(null)
        }
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .withOrNull(comp) where comp: Computation<A>?
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withOrNull with nullable Computation - non-null executes`() = runTest {
        val comp: Computation<String>? = Computation.of("yes")

        val result = Async {
            kap { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(comp)
        }
        assertEquals("fixed|yes", result)
    }

    @Test
    fun `withOrNull with nullable Computation - null passes null`() = runTest {
        val comp: Computation<String>? = null

        val result = Async {
            kap { a: String, b: String? -> "$a|${b ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(comp)
        }
        assertEquals("fixed|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mixed chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mixed chain with nullable and non-null`() = runTest {
        val present: Computation<String>? = Computation.of("yes")
        val absent: Computation<String>? = null

        val result = Async {
            kap { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(present)
                .withOrNull(absent)
        }

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `chain with literal null and nullable variable`() = runTest {
        val present: Computation<String>? = Computation.of("yes")

        val result = Async {
            kap { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(present)
                .withOrNull(null)
        }

        assertEquals("fixed|yes|nil", result)
    }

    @Test
    fun `all null parameters`() = runTest {
        val result = Async {
            kap { a: String, b: String?, c: String? -> "$a|${b ?: "nil"}|${c ?: "nil"}" }
                .with { "fixed" }
                .withOrNull(null)
                .withOrNull(null)
        }

        assertEquals("fixed|nil|nil", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // withOrNull with real parallelism + followedBy + flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `withOrNull runs non-null in parallel - barrier proof`() = runTest {
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

        // Would deadlock if withOrNull ran sequentially
        val result = Async {
            kap { a: String?, b: String?, c: String -> "${a ?: "nil"}|${b ?: "nil"}|$c" }
                .withOrNull(compA)
                .withOrNull(compB)
                .with { "C" }
        }

        assertEquals("A|B|C", result)
    }

    @Test
    fun `withOrNull integrates with followedBy and flatMap`() = runTest {
        val optionalDiscount: Computation<Discount>? = Computation.of(Discount("SUMMER20", 20))
        val noInsurance: Computation<InsurancePlan>? = null

        data class BookingDetails(
            val user: UserProfile,
            val cart: CartSummary,
            val discount: Discount?,
            val insurance: InsurancePlan?,
            val total: OrderTotal,
        )

        val result = Async {
            kap(::BookingDetails)
                .with { UserProfile("Alice", 42) }
                .with { CartSummary(3) }
                .withOrNull(optionalDiscount)
                .withOrNull(noInsurance)
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
