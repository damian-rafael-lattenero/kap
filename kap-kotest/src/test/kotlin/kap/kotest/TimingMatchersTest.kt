package kap.kotest

import kap.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimingMatchersTest {

    @Test
    fun `shouldBeMillis passes on exact match`() = runTest {
        Async { Kap { delay(100) } }
        currentTime.shouldBeMillis(100)
    }

    @Test
    fun `shouldBeMillis fails on mismatch`() = runTest {
        Async { Kap { delay(100) } }
        assertFailsWith<AssertionError> {
            currentTime.shouldBeMillis(50)
        }
    }

    @Test
    fun `shouldBeAtMostMillis passes when under`() = runTest {
        Async { Kap { delay(50) } }
        currentTime.shouldBeAtMostMillis(100)
    }

    @Test
    fun `shouldBeAtMostMillis fails when over`() = runTest {
        Async { Kap { delay(200) } }
        assertFailsWith<AssertionError> {
            currentTime.shouldBeAtMostMillis(100)
        }
    }

    @Test
    fun `shouldProveParallel passes for parallel execution`() = runTest {
        Async {
            kap { a: Unit, b: Unit, c: Unit -> Triple(a, b, c) }
                .with(Kap { delay(50) })
                .with(Kap { delay(50) })
                .with(Kap { delay(50) })
        }
        currentTime.shouldProveParallel(taskCount = 3, taskDurationMs = 50)
    }

    @Test
    fun `shouldProveParallel fails for sequential execution`() = runTest {
        Async { Kap { delay(50) }.andThen { Kap { delay(50) }.andThen { Kap { delay(50) } } } }
        assertFailsWith<AssertionError> {
            currentTime.shouldProveParallel(taskCount = 3, taskDurationMs = 50)
        }
    }
}
