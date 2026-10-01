package kap.kotest

import kap.*

/**
 * Assert that a Kap computation succeeds with the expected value.
 */
@Suppress("RedundantSuspendModifier") // detekt TR cannot see through inline lambdas; suspend is required
suspend inline fun <reified A> Kap<A>.shouldSucceedWith(expected: A) {
    val result = runCatching { this@shouldSucceedWith.evalGraph() }
    assert(result.isSuccess) {
        "Expected success with $expected but got failure: ${result.exceptionOrNull()}"
    }
    assert(result.getOrNull() == expected) {
        "Expected $expected but got ${result.getOrNull()}"
    }
}

/**
 * Assert that a Kap computation succeeds and return the value for further assertions.
 */
@Suppress("RedundantSuspendModifier") // detekt TR cannot see through inline lambdas; suspend is required
suspend inline fun <reified A> Kap<A>.shouldSucceed(): A {
    val result = runCatching { this@shouldSucceed.evalGraph() }
    assert(result.isSuccess) {
        "Expected success but got failure: ${result.exceptionOrNull()}"
    }
    return result.getOrThrow()
}

/**
 * Assert that a Kap computation fails with a specific exception type.
 */
@Suppress("RedundantSuspendModifier") // detekt TR cannot see through inline lambdas; suspend is required
suspend inline fun <reified E : Throwable> Kap<*>.shouldFailWith(): E {
    val result = runCatching { this@shouldFailWith.evalGraph() }
    assert(result.isFailure) {
        "Expected failure with ${E::class.simpleName} but got success: ${result.getOrNull()}"
    }
    val exception = checkNotNull(result.exceptionOrNull()) { "Expected failure but no exception present" }
    assert(exception is E) {
        "Expected ${E::class.simpleName} but got ${exception::class.simpleName}: ${exception.message}"
    }
    return exception as E
}

/**
 * Assert that a Kap computation fails with a message matching the given string.
 */
@Suppress("RedundantSuspendModifier") // detekt TR cannot see through inline lambdas; suspend is required
suspend fun Kap<*>.shouldFailWithMessage(expected: String) {
    val result = runCatching { this@shouldFailWithMessage.evalGraph() }
    assert(result.isFailure) {
        "Expected failure with message '$expected' but got success: ${result.getOrNull()}"
    }
    val message = result.exceptionOrNull()?.message
    assert(message == expected) {
        "Expected message '$expected' but got '$message'"
    }
}
