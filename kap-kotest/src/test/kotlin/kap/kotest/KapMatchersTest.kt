package kap.kotest

import kap.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KapMatchersTest {

    @Test
    fun `shouldSucceedWith passes on matching value`() = runTest {
        Kap { 42 }.shouldSucceedWith(42)
    }

    @Test
    fun `shouldSucceedWith fails on mismatch`() = runTest {
        assertFailsWith<AssertionError> {
            Kap { 42 }.shouldSucceedWith(99)
        }
    }

    @Test
    fun `shouldSucceedWith fails on error`() = runTest {
        assertFailsWith<AssertionError> {
            Kap<Int> { throw RuntimeException("boom") }.shouldSucceedWith(42)
        }
    }

    @Test
    fun `shouldSucceed returns the value`() = runTest {
        val result = Kap { "hello" }.shouldSucceed()
        assert(result == "hello")
    }

    @Test
    fun `shouldFailWith passes on matching exception`() = runTest {
        val ex = Kap<Int> { throw IllegalArgumentException("bad") }
            .shouldFailWith<IllegalArgumentException>()
        assert(ex.message == "bad")
    }

    @Test
    fun `shouldFailWith fails on wrong exception type`() = runTest {
        assertFailsWith<AssertionError> {
            Kap<Int> { throw RuntimeException("boom") }
                .shouldFailWith<IllegalArgumentException>()
        }
    }

    @Test
    fun `shouldFailWith fails on success`() = runTest {
        assertFailsWith<AssertionError> {
            Kap { 42 }.shouldFailWith<RuntimeException>()
        }
    }

    @Test
    fun `shouldFailWithMessage passes on matching message`() = runTest {
        Kap<Int> { throw RuntimeException("boom") }.shouldFailWithMessage("boom")
    }

    @Test
    fun `shouldFailWithMessage fails on wrong message`() = runTest {
        assertFailsWith<AssertionError> {
            Kap<Int> { throw RuntimeException("boom") }.shouldFailWithMessage("bang")
        }
    }
}
