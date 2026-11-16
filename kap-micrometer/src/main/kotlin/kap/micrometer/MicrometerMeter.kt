package kap.micrometer

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kap.Meter
import kap.Tags
import java.util.function.Supplier
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * The [Meter] SPI over a Micrometer [MeterRegistry] — the thin JVM adapter
 * between KAP and any Micrometer backend (Prometheus, OTLP, CloudWatch, ...).
 *
 * Names pass through **verbatim** in Micrometer's dot form: a Prometheus
 * registry renders `kap.turns.started` as `kap_turns_started_total` and the
 * timers as `*_seconds_count`/`*_sum`/`*_max`. Tags stay dimensions of the
 * same metric: `kap.turns.sealed` with `outcome=completed` and
 * `outcome=failed` is one timer name with two tag sets, never two names.
 *
 * ```
 * val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
 * val meter: Meter = MicrometerMeter(registry)
 *
 * meter.increment("kap.poller.cycles")
 * meter.increment("kap.turns.sealed", Tags.of("outcome" to "failed"))
 * meter.time("kap.brain.request") { brain.ask(prompt) }
 * meter.gauge("kap.outbox.depth", Tags.of("lane" to "main")) { outbox.depth().toDouble() }
 * ```
 *
 * ## Contract notes
 *
 * - Counters and timers are looked up per sample; Micrometer deduplicates
 *   meters by `name + tags`, so repeated lookups never create duplicates.
 * - Gauges register their supplier with a **strong reference**: Micrometer
 *   holds gauge state weakly, and an unreferenced supplier reads `NaN` after
 *   a GC (the documented pitfall). Register each gauge once at wiring time —
 *   the supplier is read on every scrape, never cached.
 * - This adapter follows the [Meter] contract and does not throw. Wrap it with
 *   [Meter.guarded] when the registry is external, and the instrumentation
 *   failures are reported instead of reaching the measured code.
 */
class MicrometerMeter(private val registry: MeterRegistry) : Meter {

    override fun increment(name: String, tags: Tags) {
        registry.counter(name, tags.toMicrometerTags().asIterable()).increment()
    }

    override fun record(name: String, tags: Tags, duration: Duration) {
        registry.timer(name, tags.toMicrometerTags().asIterable()).record(duration.toJavaDuration())
    }

    override fun gauge(name: String, tags: Tags, supplier: () -> Double) {
        Gauge.builder(name, Supplier<Number> { supplier() })
            .tags(tags.toMicrometerTags().asIterable())
            .strongReference(true)
            .register(registry)
    }
}

private val NO_TAGS: Array<Tag> = emptyArray()

/** One small array per tagged sample — the registry's own lookup key; untagged samples share [NO_TAGS]. */
private fun Tags.toMicrometerTags(): Array<Tag> {
    if (isEmpty) return NO_TAGS
    var index = 0
    val result = arrayOfNulls<Tag>(size)
    forEach { key, value ->
        result[index] = Tag.of(key, value)
        index += 1
    }
    return result.requireNoNulls()
}
