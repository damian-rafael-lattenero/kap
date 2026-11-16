package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The [Meter] laws:
 *
 * 1. **The no-op is byte-for-byte inert.** [Meter.Noop] runs the block exactly
 *    once, propagates the block's exception instance untouched and allocates
 *    nothing on any path.
 * 2. **Tags never mutate the metric name identity.** The name reaches the
 *    implementation verbatim; tag combinations are dimensions of the same
 *    metric, and a [Tags] value never changes after construction.
 * 3. **A throwing meter never breaks the measured code.** [Meter.guarded]
 *    catches every instrumentation failure, reports it (never swallows it into
 *    silence) and leaves the block's value and exception untouched.
 * 4. **Cancellation records exactly once and propagates.** A timed suspending
 *    block records in `finally`; the caller's `CancellationException` is still
 *    the outcome.
 *
 * The allocation law is measured on the JVM with `ThreadMXBean`, which reports
 * the exact bytes allocated by the current thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeterLawsTest {

    // ── 1. The no-op is inert ───────────────────────────────────────────

    @Test
    fun `noop runs the block exactly once and returns its value`() {
        var runs = 0

        val value = Meter.Noop.time("op", Tags.Empty) {
            runs += 1
            42
        }

        assertEquals(42, value)
        assertEquals(1, runs)
    }

    @Test
    fun `noop propagates the block exception instance untouched`() {
        val boom = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            Meter.Noop.time<Unit>("op") { throw boom }
        }

        assertSame(boom, thrown)
    }

    @Test
    fun `noop suspending time runs the block bare`() = runTest {
        var runs = 0

        val value = Meter.Noop.timeSuspending("op") {
            runs += 1
            delay(5.seconds)
            "done"
        }

        assertEquals("done", value)
        assertEquals(1, runs)
    }

    @Test
    fun `noop allocates nothing on any path`() {
        val block: () -> Int = { 7 }
        val supplier: () -> Double = { 1.0 }
        val sample = 1.milliseconds

        // Two full warm-up runs compile every path before the measured window.
        exerciseNoop(200_000, block, supplier, sample)
        exerciseNoop(200_000, block, supplier, sample)

        val before = allocatedBytes()
        exerciseNoop(1_000_000, block, supplier, sample)
        val allocated = allocatedBytes() - before

        assertEquals(0L, allocated, "Meter.Noop allocated $allocated bytes; the OFF path must be inert")
    }

    private fun exerciseNoop(iterations: Int, block: () -> Int, supplier: () -> Double, sample: Duration) {
        repeat(iterations) {
            Meter.Noop.increment("kap.counter", Tags.Empty)
            Meter.Noop.record("kap.timer", Tags.Empty, sample)
            Meter.Noop.gauge("kap.gauge", Tags.Empty, supplier)
            Meter.Noop.time("kap.timed", Tags.Empty, block)
        }
    }

    // ── 2. Name identity and tag immutability ───────────────────────────

    @Test
    fun `tags never mutate the metric name identity`() {
        val meter = RecordingMeter()

        meter.increment("http.requests", Tags.of("route" to "/a"))
        meter.increment("http.requests", Tags.of("route" to "/b"))
        meter.increment("http.requests", Tags.Empty)

        assertEquals(listOf("http.requests", "http.requests", "http.requests"), meter.increments.map { it.name })
        assertEquals(Tags.of("route" to "/a"), meter.increments[0].tags)
        assertEquals(Tags.of("route" to "/b"), meter.increments[1].tags)
        assertEquals(Tags.Empty, meter.increments[2].tags)
    }

    @Test
    fun `a Tags value never changes after construction`() {
        val source = arrayOf("lane" to "main")
        val tags = Tags.of(*source)

        source[0] = "lane" to "firehose"

        assertEquals(mapOf("lane" to "main"), tags.asMap())
        assertEquals(1, tags.size)
    }

    @Test
    fun `Tags rejects duplicate keys and preserves equality`() {
        assertFailsWith<IllegalArgumentException> {
            Tags.of("lane" to "main", "lane" to "firehose")
        }

        assertEquals(Tags.of("a" to "1", "b" to "2"), Tags.of("a" to "1", "b" to "2"))
        assertEquals(Tags.of("a" to "1", "b" to "2").hashCode(), Tags.of("a" to "1", "b" to "2").hashCode())
        assertTrue(Tags.Empty.isEmpty)
    }

    // ── 3. A throwing implementation never breaks the measured code ─────

    @Test
    fun `a throwing meter cannot break increment record or gauge registration`() {
        val delegate = ThrowingMeter()
        val failures = mutableListOf<MeterFailure>()
        val meter = Meter.guarded(delegate, failures::add)

        meter.increment("counter")
        meter.record("timer", duration = 1.milliseconds)
        meter.gauge("gauge") { 1.0 }

        assertEquals(
            listOf(MeterOperation.INCREMENT, MeterOperation.RECORD, MeterOperation.GAUGE),
            failures.map { it.operation },
        )
        assertEquals(listOf("counter", "timer", "gauge"), failures.map { it.name })
        failures.forEach { assertSame(delegate.error, it.error) }
    }

    @Test
    fun `a throwing meter cannot break a timed block`() {
        val delegate = ThrowingMeter()
        val failures = mutableListOf<MeterFailure>()
        val meter = Meter.guarded(delegate, failures::add)

        val value = meter.time("op") { 42 }

        assertEquals(42, value)
        assertEquals(1, failures.size)
        assertEquals(MeterOperation.RECORD, failures.single().operation)
    }

    @Test
    fun `a timed block exception wins over the instrumentation failure`() {
        val delegate = ThrowingMeter()
        val failures = mutableListOf<MeterFailure>()
        val meter = Meter.guarded(delegate, failures::add)
        val boom = IllegalArgumentException("measured code failed")

        val thrown = assertFailsWith<IllegalArgumentException> {
            meter.time<Unit>("op") { throw boom }
        }

        assertSame(boom, thrown)
        assertEquals(1, failures.size, "the record failure is reported, not silently discarded")
    }

    @Test
    fun `a throwing gauge supplier reads NaN and is reported at scrape time`() {
        val delegate = CapturingMeter()
        val failures = mutableListOf<MeterFailure>()
        val meter = Meter.guarded(delegate, failures::add)
        meter.gauge("outbox.depth") { error("scrape failed") }

        val read = delegate.supplier!!.invoke()

        assertTrue(read.isNaN(), "a broken gauge reads as NaN, never a fabricated zero")
        assertEquals(MeterOperation.GAUGE_READ, failures.single().operation)
        assertEquals("outbox.depth", failures.single().name)
    }

    @Test
    fun `a gauge supplier is read at scrape time and never cached`() {
        val delegate = CapturingMeter()
        val failures = mutableListOf<MeterFailure>()
        val meter = Meter.guarded(delegate, failures::add)
        var depth = 1.0
        meter.gauge("outbox.depth", Tags.of("lane" to "main")) { depth }

        assertEquals(1.0, delegate.supplier!!.invoke())
        depth = 7.0
        assertEquals(7.0, delegate.supplier!!.invoke())
        assertEquals(Tags.of("lane" to "main"), delegate.registeredTags)
    }

    @Test
    fun `a throwing failure reporter cannot break the measured code`() {
        val meter = Meter.guarded(ThrowingMeter()) { error("reporter failed") }

        meter.increment("counter")
        assertEquals(42, meter.time("op") { 42 })
    }

    // ── 4. Cancellation records exactly once ────────────────────────────

    @Test
    fun `cancelling a timed suspend block records once and propagates`() = runTest {
        val meter = RecordingMeter()
        val job = launch {
            meter.timeSuspending("op") { delay(10.seconds) }
        }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, meter.records.size, "the cancelled block must record exactly one sample")
        assertTrue(meter.records.single().duration > Duration.ZERO)
    }

    @Test
    fun `time records exactly once on success and on failure`() {
        val meter = RecordingMeter()

        assertEquals(42, meter.time("success") { 42 })
        assertFailsWith<IllegalStateException> { meter.time<Unit>("failure") { error("boom") } }

        assertEquals(listOf("success", "failure"), meter.records.map { it.name })
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private class Sample(val name: String, val tags: Tags)

    private class Recorded(val name: String, val tags: Tags, val duration: Duration)

    private class RecordingMeter : Meter {
        val increments = mutableListOf<Sample>()
        val records = mutableListOf<Recorded>()

        override fun increment(name: String, tags: Tags) {
            increments += Sample(name, tags)
        }

        override fun record(name: String, tags: Tags, duration: Duration) {
            records += Recorded(name, tags, duration)
        }

        override fun gauge(name: String, tags: Tags, supplier: () -> Double) = Unit
    }

    private class ThrowingMeter : Meter {
        val error = IllegalStateException("meter down")

        override fun increment(name: String, tags: Tags) {
            throw error
        }

        override fun record(name: String, tags: Tags, duration: Duration) {
            throw error
        }

        override fun gauge(name: String, tags: Tags, supplier: () -> Double) {
            throw error
        }
    }

    private class CapturingMeter : Meter {
        var supplier: (() -> Double)? = null
        var registeredTags: Tags = Tags.Empty

        override fun increment(name: String, tags: Tags) = Unit

        override fun record(name: String, tags: Tags, duration: Duration) = Unit

        override fun gauge(name: String, tags: Tags, supplier: () -> Double) {
            this.supplier = supplier
            registeredTags = tags
        }
    }

    private val allocationBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun allocatedBytes(): Long =
        allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
}
