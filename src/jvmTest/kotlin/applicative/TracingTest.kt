package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TracingTest {

    @Test
    fun `traced reports start and success with duration`() = runTest {
        val events = mutableListOf<String>()

        val result = Async {
            pure(42).traced(
                name = "compute",
                onStart = { events += "start:$it" },
                onSuccess = { name, _ -> events += "success:$name" },
                onError = { name, _, _ -> events += "error:$name" },
            )
        }

        assertEquals(42, result)
        assertEquals(listOf("start:compute", "success:compute"), events)
    }

    @Test
    fun `traced reports error on failure`() = runTest {
        val events = mutableListOf<String>()

        val result = runCatching {
            Async {
                Computation<String> { throw IllegalStateException("boom") }
                    .traced(
                        name = "failing",
                        onStart = { events += "start:$it" },
                        onSuccess = { name, _ -> events += "success:$name" },
                        onError = { name, _, e -> events += "error:$name:${e.message}" },
                    )
            }
        }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(listOf("start:failing", "error:failing:boom"), events)
    }

    @Test
    fun `traced re-throws CancellationException`() = runTest {
        val events = mutableListOf<String>()

        val result = runCatching {
            Async {
                Computation<String> { throw CancellationException("cancelled") }
                    .traced(
                        name = "cancelled-op",
                        onStart = { events += "start:$it" },
                        onSuccess = { name, _ -> events += "success:$name" },
                        onError = { name, _, _ -> events += "error:$name" },
                    )
            }
        }

        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
        assertEquals(listOf("start:cancelled-op", "error:cancelled-op"), events)
    }

    @Test
    fun `traced duration is non-negative`() = runTest {
        var recordedDuration: kotlin.time.Duration? = null

        Async {
            Computation { delay(10.milliseconds); "done" }.traced(
                name = "timed",
                onSuccess = { _, duration -> recordedDuration = duration },
            )
        }

        assertTrue(recordedDuration!! >= kotlin.time.Duration.ZERO)
    }

    @Test
    fun `ComputationTracer interface receives all events in order`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = ComputationTracer { events += it }

        val result = Async {
            pure(99).traced("op", tracer)
        }

        assertEquals(99, result)
        assertEquals(2, events.size)
        assertTrue(events[0] is TraceEvent.Started)
        assertEquals("op", events[0].name)
        assertTrue(events[1] is TraceEvent.Succeeded)
        assertEquals("op", events[1].name)
    }

    @Test
    fun `ComputationTracer reports failure event`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = ComputationTracer { events += it }

        val result = runCatching {
            Async {
                Computation<String> { throw RuntimeException("fail") }
                    .traced("broken", tracer)
            }
        }

        assertTrue(result.isFailure)
        assertEquals(2, events.size)
        assertTrue(events[0] is TraceEvent.Started)
        val failed = events[1] as TraceEvent.Failed
        assertEquals("broken", failed.name)
        assertEquals("fail", failed.error.message)
    }

    @Test
    fun `traced works with ap for parallel tracing`() = runTest {
        val started = mutableListOf<String>()
        val succeeded = mutableListOf<String>()

        val result = Async {
            lift2 { a: Int, b: Int -> a + b }
                .ap(pure(10).traced("left", onStart = { started += it }, onSuccess = { n, _ -> succeeded += n }))
                .ap(pure(20).traced("right", onStart = { started += it }, onSuccess = { n, _ -> succeeded += n }))
        }

        assertEquals(30, result)
        assertTrue("left" in started)
        assertTrue("right" in started)
        assertTrue("left" in succeeded)
        assertTrue("right" in succeeded)
    }
}
