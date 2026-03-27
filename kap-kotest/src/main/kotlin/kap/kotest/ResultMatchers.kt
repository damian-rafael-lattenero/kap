package kap.kotest

/**
 * Assert that a Result is a success with the expected value.
 */
fun <A> Result<A>.shouldBeSuccess(expected: A) {
    assert(isSuccess) {
        "Expected success with $expected but got failure: ${exceptionOrNull()}"
    }
    assert(getOrNull() == expected) {
        "Expected $expected but got ${getOrNull()}"
    }
}

/**
 * Assert that a Result is a success and return the value.
 */
fun <A> Result<A>.shouldBeSuccess(): A {
    assert(isSuccess) {
        "Expected success but got failure: ${exceptionOrNull()}"
    }
    return getOrThrow()
}

/**
 * Assert that a Result is a failure with a specific exception type.
 */
inline fun <reified E : Throwable> Result<*>.shouldBeFailure(): E {
    assert(isFailure) {
        "Expected failure with ${E::class.simpleName} but got success: ${getOrNull()}"
    }
    val exception = exceptionOrNull()!!
    assert(exception is E) {
        "Expected ${E::class.simpleName} but got ${exception::class.simpleName}: ${exception.message}"
    }
    return exception as E
}

/**
 * Assert that a Result is a failure with a message matching the given string.
 */
fun Result<*>.shouldBeFailureWithMessage(expected: String) {
    assert(isFailure) {
        "Expected failure but got success: ${getOrNull()}"
    }
    val message = exceptionOrNull()?.message
    assert(message == expected) {
        "Expected failure message '$expected' but got '$message'"
    }
}
