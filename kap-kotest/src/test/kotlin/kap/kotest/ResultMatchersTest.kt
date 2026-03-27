package kap.kotest

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ResultMatchersTest {

    @Test
    fun `shouldBeSuccess passes on matching value`() {
        Result.success(42).shouldBeSuccess(42)
    }

    @Test
    fun `shouldBeSuccess fails on mismatch`() {
        assertFailsWith<AssertionError> {
            Result.success(42).shouldBeSuccess(99)
        }
    }

    @Test
    fun `shouldBeSuccess returns value`() {
        val value = Result.success("hello").shouldBeSuccess()
        assert(value == "hello")
    }

    @Test
    fun `shouldBeFailure passes on matching exception type`() {
        val ex = Result.failure<Int>(IllegalArgumentException("bad"))
            .shouldBeFailure<IllegalArgumentException>()
        assert(ex.message == "bad")
    }

    @Test
    fun `shouldBeFailure fails on wrong type`() {
        assertFailsWith<AssertionError> {
            Result.failure<Int>(RuntimeException("boom"))
                .shouldBeFailure<IllegalArgumentException>()
        }
    }

    @Test
    fun `shouldBeFailureWithMessage passes on match`() {
        Result.failure<Int>(RuntimeException("boom"))
            .shouldBeFailureWithMessage("boom")
    }

    @Test
    fun `shouldBeFailureWithMessage fails on mismatch`() {
        assertFailsWith<AssertionError> {
            Result.failure<Int>(RuntimeException("boom"))
                .shouldBeFailureWithMessage("bang")
        }
    }
}
