package kap.kotest

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArrowMatchersTest {

    @Test
    fun `shouldBeRight passes on matching value`() {
        val either: Either<String, Int> = Either.Right(42)
        either.shouldBeRight(42)
    }

    @Test
    fun `shouldBeRight fails on Left`() {
        val either: Either<String, Int> = Either.Left("error")
        assertFailsWith<AssertionError> {
            either.shouldBeRight(42)
        }
    }

    @Test
    fun `shouldBeRight returns value`() {
        val either: Either<String, Int> = Either.Right(42)
        val value = either.shouldBeRight()
        assert(value == 42)
    }

    @Test
    fun `shouldBeLeft passes on matching error`() {
        val either: Either<String, Int> = Either.Left("error")
        either.shouldBeLeft("error")
    }

    @Test
    fun `shouldBeLeft fails on Right`() {
        val either: Either<String, Int> = Either.Right(42)
        assertFailsWith<AssertionError> {
            either.shouldBeLeft()
        }
    }

    @Test
    fun `shouldHaveErrors passes on correct count`() {
        val result: Either<NonEmptyList<String>, Int> =
            Either.Left(nonEmptyListOf("err1", "err2", "err3"))
        result.shouldHaveErrors(3)
    }

    @Test
    fun `shouldHaveErrors fails on wrong count`() {
        val result: Either<NonEmptyList<String>, Int> =
            Either.Left(nonEmptyListOf("err1", "err2"))
        assertFailsWith<AssertionError> {
            result.shouldHaveErrors(3)
        }
    }

    @Test
    fun `shouldContainError passes when error present`() {
        val result: Either<NonEmptyList<String>, Int> =
            Either.Left(nonEmptyListOf("err1", "err2"))
        result.shouldContainError("err2")
    }

    @Test
    fun `shouldContainError fails when error absent`() {
        val result: Either<NonEmptyList<String>, Int> =
            Either.Left(nonEmptyListOf("err1", "err2"))
        assertFailsWith<AssertionError> {
            result.shouldContainError("err3")
        }
    }
}
