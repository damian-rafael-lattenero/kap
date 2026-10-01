import kap.Kap
import kap.evalGraph
import kap.of
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end usage of the generated GENERIC builders.
 *
 * Canonical form mirrors the non-generic sibling: `kap<Double>(::FixtGeneric)`
 * — the type argument pins the type variables at the entry, everything
 * downstream infers from there. The zero-arg `kapFixtGeneric<Double>()` form
 * is the always-unambiguous fallback for shape-colliding declarations.
 */
class GenericBuilderUsageTest {

    @Test
    fun `generic class - canonical form, T pinned by the type argument`() = runTest {
        val result = kap<Double>(::FixtGeneric)
            .with { label from "checkout" }
            .then { amount from 2.0 }
            .evalGraph()
        assertEquals(FixtGeneric("checkout", 2.0), result)
    }

    @Test
    fun `generic class - zero-arg fallback form also works`() = runTest {
        val result = kapFixtGeneric<Long>()
            .with { label from "count" }
            .then { amount from 42L }
            .evalGraph()
        assertEquals(FixtGeneric("count", 42L), result)
    }

    @Test
    fun `multi-generic class - A and B pinned at the entry`() = runTest {
        val result = kap<Int, String>(::FixtMultiGeneric)
            .with { first from 1 }
            .with { second from "two" }
            .then { note from "both pinned" }
            .evalGraph()
        assertEquals(FixtMultiGeneric(1, "two", "both pinned"), result)
    }

    @Test
    fun `generic function - canonical form`() = runTest {
        val result = kap<Double>(::fixtGenericFn)
            .with { seed from 3.5 }
            .then { n from 2 }
            .evalGraph()
        assertEquals(3.5 to 2, result)
    }

    @Test
    fun `generic class - parens Kap form composes with core combinators`() = runTest {
        val result = kap<Double>(::FixtGeneric)
            .with { label from "kap-form" }
            .then(FixtGenericKap.amount from Kap.of(9.9))
            .evalGraph()
        assertEquals(FixtGeneric("kap-form", 9.9), result)
    }

    @Test
    fun `type parameter named E - generated error binder is alpha-renamed`() = runTest {
        val result = kap<Int>(::FixtParamE)
            .with { payload from 7 }
            .then { note from "no collision" }
            .evalGraph()
        assertEquals(FixtParamE(7, "no collision"), result)
    }

    @Test
    fun `type parameter named Rest - generated rest binder is shadowed safely`() = runTest {
        val result = kap<String>(::FixtParamRest)
            .with { body from "rest" }
            .then { count from 1 }
            .evalGraph()
        assertEquals(FixtParamRest("rest", 1), result)
    }

    @Test
    fun `identical generic signatures fall back to the zero-arg entry`() = runTest {
        // FixtGenWrap<T>(v: T) and fixtGenWrapFn<T>(v: T): FixtGenWrap<T> share
        // the exact same canonical `kap(f)` signature — neither gets a plain
        // `kap`; the class-named zero-arg entries disambiguate.
        val viaClass = kapFixtGenWrap<Int>()
            .with { inner from 3 }
            .evalGraph()
        assertEquals(FixtGenWrap(3), viaClass)

        val viaFn = kapFixtGenWrapFn<Int>()
            .with { inner from 4 }
            .evalGraph()
        assertEquals(FixtGenWrap(4), viaFn)
    }
}
