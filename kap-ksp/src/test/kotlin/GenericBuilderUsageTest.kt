import kap.Kap
import kap.evalGraph
import kap.of
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end usage of the generated GENERIC builders.
 *
 * Kotlin cannot keep a type variable open through a chained call, so generic
 * declarations pin it at the entry point — which is `pure(curry C)` at the
 * chosen instantiation and therefore takes no argument:
 * `kapFixtGeneric<Double>()`. Everything downstream infers from there.
 */
class GenericBuilderUsageTest {

    @Test
    fun `generic class - T pinned at the entry, Double`() = runTest {
        val result = kapFixtGeneric<Double>()
            .with { label from "checkout" }
            .then { amount from 2.0 }
            .evalGraph()
        assertEquals(FixtGeneric("checkout", 2.0), result)
    }

    @Test
    fun `generic class - same builder, different instantiation`() = runTest {
        val result = kapFixtGeneric<Long>()
            .with { label from "count" }
            .then { amount from 42L }
            .evalGraph()
        assertEquals(FixtGeneric("count", 42L), result)
    }

    @Test
    fun `multi-generic class - A and B pinned at the entry`() = runTest {
        val result = kapFixtMultiGeneric<Int, String>()
            .with { first from 1 }
            .with { second from "two" }
            .then { note from "both pinned" }
            .evalGraph()
        assertEquals(FixtMultiGeneric(1, "two", "both pinned"), result)
    }

    @Test
    fun `generic function - T pinned at the entry`() = runTest {
        val result = kapFixtGenericFn<Double>()
            .with { seed from 3.5 }
            .then { n from 2 }
            .evalGraph()
        assertEquals(3.5 to 2, result)
    }

    @Test
    fun `generic class - parens Kap form composes with core combinators`() = runTest {
        val result = kapFixtGeneric<Double>()
            .with { label from "kap-form" }
            .then(FixtGenericKap.amount from Kap.of(9.9))
            .evalGraph()
        assertEquals(FixtGeneric("kap-form", 9.9), result)
    }

    @Test
    fun `type parameter named E - generated error binder is alpha-renamed`() = runTest {
        val result = kapFixtParamE<Int>()
            .with { payload from 7 }
            .then { note from "no collision" }
            .evalGraph()
        assertEquals(FixtParamE(7, "no collision"), result)
    }

    @Test
    fun `type parameter named Rest - generated rest binder is shadowed safely`() = runTest {
        val result = kapFixtParamRest<String>()
            .with { body from "rest" }
            .then { count from 1 }
            .evalGraph()
        assertEquals(FixtParamRest("rest", 1), result)
    }
}
