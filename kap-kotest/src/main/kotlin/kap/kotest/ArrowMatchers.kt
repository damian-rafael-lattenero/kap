package kap.kotest

// These matchers require kap-arrow and arrow-core on the classpath (compileOnly dependency).
// They are available when the user adds kap-arrow to their test dependencies.

import arrow.core.Either
import arrow.core.NonEmptyList

/**
 * Assert that an Either is Right with the expected value.
 */
fun <E, A> Either<E, A>.shouldBeRight(expected: A) {
    assert(this is Either.Right) {
        "Expected Right($expected) but got $this"
    }
    val value = (this as Either.Right).value
    assert(value == expected) {
        "Expected Right($expected) but got Right($value)"
    }
}

/**
 * Assert that an Either is Right and return the value.
 */
fun <E, A> Either<E, A>.shouldBeRight(): A {
    assert(this is Either.Right) {
        "Expected Right but got $this"
    }
    return (this as Either.Right).value
}

/**
 * Assert that an Either is Left and return the error.
 */
fun <E, A> Either<E, A>.shouldBeLeft(): E {
    assert(this is Either.Left) {
        "Expected Left but got $this"
    }
    return (this as Either.Left).value
}

/**
 * Assert that an Either is Left with the expected error.
 */
fun <E, A> Either<E, A>.shouldBeLeft(expected: E) {
    assert(this is Either.Left) {
        "Expected Left($expected) but got $this"
    }
    val value = (this as Either.Left).value
    assert(value == expected) {
        "Expected Left($expected) but got Left($value)"
    }
}

/**
 * Assert that a validated result (Either<NonEmptyList<E>, A>) contains exactly N errors.
 */
fun <E, A> Either<NonEmptyList<E>, A>.shouldHaveErrors(count: Int) {
    assert(this is Either.Left) {
        "Expected Left with $count errors but got Right"
    }
    val errors = (this as Either.Left).value
    assert(errors.size == count) {
        "Expected $count errors but got ${errors.size}: $errors"
    }
}

/**
 * Assert that a validated result contains a specific error.
 */
fun <E, A> Either<NonEmptyList<E>, A>.shouldContainError(error: E) {
    assert(this is Either.Left) {
        "Expected Left containing $error but got Right"
    }
    val errors = (this as Either.Left).value
    assert(error in errors.toList()) {
        "Expected errors to contain $error but got: $errors"
    }
}
