package kap.micrometer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kap.Meter
import kap.Tags
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The adapter laws, proven against real Micrometer registries:
 *
 * 1. **Names are identities.** The dot-form name reaches the registry verbatim;
 *    tag combinations are dimensions of the same name, not new names.
 * 2. **Counters and timers aggregate.** Counts, sums and maxima match exactly.
 * 3. **Gauges are read at scrape time.** A gauge reports the supplier's current
 *    value, never a value cached at registration.
 * 4. **The SPI's timed paths record exactly once** through the adapter.
 * 5. **Prometheus rendering is friendly.** `a.b.c` becomes `a_b_c_total` /
 *    `a_b_c_seconds_*` and tags render in the scrape body.
 */
class MicrometerMeterTest {

    private val registry = SimpleMeterRegistry()
    private val meter = MicrometerMeter(registry)

    @Test
    fun `counter counts by name and keeps tags as dimensions`() {
        meter.increment("kap.requests", Tags.of("route" to "/a"))
        meter.increment("kap.requests", Tags.of("route" to "/a"))
        meter.increment("kap.requests", Tags.of("route" to "/b"))
        meter.increment("kap.requests")

        assertEquals(2.0, registry.find("kap.requests").tag("route", "/a").counter()!!.count())
        assertEquals(1.0, registry.find("kap.requests").tag("route", "/b").counter()!!.count())
        assertEquals(3, registry.meters.filter { it.id.name == "kap.requests" }.size)
        assertTrue(
            registry.meters.filter { it.id.name == "kap.requests" }.any { it.id.tags.isEmpty() },
            "the untagged sample is its own meter under the SAME name",
        )
    }

    @Test
    fun `timer aggregates count sum and max`() {
        meter.record("kap.brain.request", Tags.of("outcome" to "ok"), 250.milliseconds)
        meter.record("kap.brain.request", Tags.of("outcome" to "ok"), 50.milliseconds)

        val timer = registry.find("kap.brain.request").tag("outcome", "ok").timer()!!

        assertEquals(2L, timer.count())
        assertEquals(300.0, timer.totalTime(TimeUnit.MILLISECONDS), 0.001)
        assertEquals(250.0, timer.max(TimeUnit.MILLISECONDS), 0.001)
    }

    @Test
    fun `gauge reads its supplier at scrape time`() {
        var depth = 3
        meter.gauge("kap.outbox.depth", Tags.of("lane" to "main")) { depth.toDouble() }

        val gauge = registry.find("kap.outbox.depth").tag("lane", "main").gauge()!!

        assertEquals(3.0, gauge.value())
        depth = 9
        assertEquals(9.0, gauge.value(), "a gauge must report the current value, never a cached one")
    }

    @Test
    fun `the SPI timed paths record once through the adapter`() = runTest {
        assertEquals(42, meter.time("kap.sync") { 42 })
        assertEquals(43, meter.timeSuspending("kap.suspend") { 43 })

        assertEquals(1L, registry.find("kap.sync").timer()!!.count())
        assertEquals(1L, registry.find("kap.suspend").timer()!!.count())
    }

    @Test
    fun `Prometheus renders dot names and tag dimensions`() {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusMeter = MicrometerMeter(prometheus)

        prometheusMeter.increment("kap.turns.started")
        prometheusMeter.increment("kap.turns.sealed", Tags.of("outcome" to "failed"))
        prometheusMeter.record("kap.brain.request", Tags.Empty, 10.milliseconds)

        val scraped = prometheus.scrape()

        assertTrue(scraped.contains("kap_turns_started_total 1.0"), scraped)
        assertTrue(scraped.contains("kap_turns_sealed_total{outcome=\"failed\"} 1.0"), scraped)
        assertTrue(scraped.contains("kap_brain_request_seconds_count 1"), scraped)
    }

    @Test
    fun `Meter Noop is a drop-in replacement that leaves the registry untouched`() {
        val off: Meter = Meter.Noop

        off.increment("kap.requests")
        off.record("kap.brain.request", duration = 10.milliseconds)
        off.gauge("kap.outbox.depth") { 1.0 }

        assertTrue(registry.meters.isEmpty())
    }
}
