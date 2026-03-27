package kap.kotest

import kotlinx.coroutines.test.TestScope

/**
 * Assert that the current virtual time matches the expected milliseconds.
 * Useful for proving parallel execution in virtual-time tests.
 *
 * ```kotlin
 * runTest {
 *     Async { traverse(items, concurrency = 5) { Kap { delay(50) } } }
 *     currentTime.shouldBeMillis(50, "5 items should run in 50ms, not 250ms")
 * }
 * ```
 */
fun Long.shouldBeMillis(expected: Long, description: String? = null) {
    assert(this == expected) {
        val desc = description?.let { " ($it)" } ?: ""
        "Expected virtual time ${expected}ms but was ${this}ms$desc"
    }
}

/**
 * Assert that the current virtual time is at most the expected milliseconds.
 * Useful for proving parallelism without exact time matching.
 *
 * ```kotlin
 * runTest {
 *     Async { kap(::Pair).with { delay(50); "a" }.with { delay(60); "b" } }
 *     currentTime.shouldBeAtMostMillis(60, "two parallel calls should be max(50,60)=60ms")
 * }
 * ```
 */
fun Long.shouldBeAtMostMillis(expected: Long, description: String? = null) {
    assert(this <= expected) {
        val desc = description?.let { " ($it)" } ?: ""
        "Expected virtual time at most ${expected}ms but was ${this}ms$desc"
    }
}

/**
 * Assert that virtual time proves parallel execution.
 * Given N tasks of the same duration, parallel time should equal single-task time.
 *
 * ```kotlin
 * runTest {
 *     Async { items.traverse { Kap { delay(50); it } } }
 *     currentTime.shouldProveParallel(taskCount = 5, taskDurationMs = 50)
 * }
 * ```
 */
fun Long.shouldProveParallel(taskCount: Int, taskDurationMs: Long) {
    val sequential = taskCount * taskDurationMs
    assert(this == taskDurationMs) {
        "Expected parallel time ${taskDurationMs}ms (proving $taskCount tasks ran in parallel) " +
                "but was ${this}ms (sequential would be ${sequential}ms)"
    }
}
