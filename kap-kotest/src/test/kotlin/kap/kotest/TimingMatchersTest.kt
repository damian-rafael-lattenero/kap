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
        Kap { delay(100) }.executeGraph()
        currentTime.shouldBeMillis(100)
    }

    @Test
    fun `shouldBeMillis fails on mismatch`() = runTest {
        Kap { delay(100) }.executeGraph()
        assertFailsWith<AssertionError> {
            currentTime.shouldBeMillis(50)
        }
    }

    @Test
    fun `shouldBeAtMostMillis passes when under`() = runTest {
        Kap { delay(50) }.executeGraph()
        currentTime.shouldBeAtMostMillis(100)
    }

    @Test
    fun `shouldBeAtMostMillis fails when over`() = runTest {
        Kap { delay(200) }.executeGraph()
        assertFailsWith<AssertionError> {
            currentTime.shouldBeAtMostMillis(100)
        }
    }

    @Test
    fun `shouldProveParallel passes for parallel execution`() = runTest {
        Kap.of { a: Unit -> { b: Unit -> { c: Unit -> Triple(a, b, c) } } }
            .with(Kap { delay(50) })
            .with(Kap { delay(50) })
            .with(Kap { delay(50) })
            .executeGraph()
        currentTime.shouldProveParallel(taskCount = 3, taskDurationMs = 50)
    }

    @Test
    fun `shouldProveParallel fails for sequential execution`() = runTest {
        Kap { delay(50) }.andThen { Kap { delay(50) }.andThen { Kap { delay(50) } } }.executeGraph()
        assertFailsWith<AssertionError> {
            currentTime.shouldProveParallel(taskCount = 3, taskDurationMs = 50)
        }
    }
}
