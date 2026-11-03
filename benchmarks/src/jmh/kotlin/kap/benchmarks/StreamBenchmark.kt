package kap.benchmarks

import io.reactivex.rxjava3.core.Observable
import kap.Backpressure
import kap.Kap
import kap.KapStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * JMH benchmarks for **kap-stream** (SPEC-5.0 milestone 5.0): map, filter and
 * flatMap at n = 1,000,000 elements, against two baselines:
 *
 * - `rawFlow*`: plain kotlinx flows — the runtime KAP rents.
 * - `rxJava*`: RxJava 3 — the other "RxJava, but in a Kotlin DSL" data point.
 *
 * Every KapStream benchmark collects through `asFlow().count()`, exactly like
 * the raw-flow baseline, so the numbers isolate the pipeline cost (the Kap
 * per element, the channel boundary) and not the sink.
 *
 * The lossy strategies benchmark throughput under a producer that never
 * waits: part of their work disappears (dropped elements are never counted
 * downstream), so read their numbers as "cost of a fast producer under this
 * policy", not as a per-element overhead. The suspend/buffer rows are the
 * apples-to-apples overhead comparison: the 5.0 target is within 2x raw Flow
 * for the suspend default.
 *
 * Measured 2026-11-03 (Apple M1, 8 cores, 16 GB, macOS 27.0.1, Corretto
 * 25.0.5, 1 fork, 2 warmup + 3 measurement iterations x 1 s): the suspend
 * default is 1.02x raw Flow for flatMap (241 ms vs 236 ms) and 1.28x for the
 * exact buffer; map is 3.0x (14.3 ms vs 4.7 ms) and filter 2.4x (11.2 ms vs
 * 4.6 ms), because a trivial per-element body is dominated by the Kap
 * allocation per element. The honest comparator for a stage (a suspend call
 * per element) is `rawFlowMapSuspendCall` at 10.3 ms, against which the
 * suspend map stage is 1.39x. A coroutineScope per element (the graph
 * Isolation law, `rawFlowMapPerElementScope` at 37.8 ms) is exactly what the
 * stage-level scope avoids; the exact `buffer` boundary is the expensive
 * strategy here (67 ms) because every element crosses a channel handoff.
 *
 * Not wired into CI: the CI benchmark job tracks the existing suite; these
 * run on demand (`-Pjmh.includes='.*Stream.*'`).
 */
internal object StreamSources {
    const val SIZE: Int = 1_000_000

    /** A generous exact buffer: enough slack to decouple without stalling. */
    const val BUFFER: Int = 64

    /** A small buffer, where drops are expected under load. */
    const val SMALL_BUFFER: Int = 8

    fun flow(): Flow<Int> = flow {
        for (index in 0 until SIZE) emit(index)
    }
}

/**
 * The two baselines that explain the KapStream overhead: a raw flow whose map
 * calls a **suspend function** per element (what a stream stage is), and a raw
 * flow whose map allocates a **coroutineScope** per element (what the graph
 * Isolation law costs when applied per element). Together they separate "the
 * Kap per element" from "the isolation scope per element".
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class StreamBaselineBenchmark {

    @Benchmark
    fun rawFlowMapSuspendCall(): Long = runBlocking {
        StreamSources.flow().map { value -> addOne(value) }.count().toLong()
    }

    @Benchmark
    fun rawFlowMapPerElementScope(): Long = runBlocking {
        StreamSources.flow().map { value -> coroutineScope { value + 1 } }.count().toLong()
    }

    private suspend fun addOne(value: Int): Int = value + 1
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class StreamMapBenchmark {

    @Benchmark
    fun rawFlowMap(): Long = runBlocking { StreamSources.flow().map { it + 1 }.count().toLong() }

    @Benchmark
    fun rxJavaMap(): Long = Observable.range(0, StreamSources.SIZE).map { it + 1 }.count().blockingGet()

    @Benchmark
    fun kapStreamMapSuspend(): Long = runBlocking { kapMap(Backpressure.Suspend) }

    @Benchmark
    fun kapStreamMapBuffer64(): Long = runBlocking { kapMap(Backpressure.Buffer(StreamSources.BUFFER)) }

    @Benchmark
    fun kapStreamMapConflate(): Long = runBlocking { kapMap(Backpressure.Conflate) }

    @Benchmark
    fun kapStreamMapDropOldest8(): Long = runBlocking { kapMap(Backpressure.DropOldest(StreamSources.SMALL_BUFFER)) }

    @Benchmark
    fun kapStreamMapDropLatest8(): Long = runBlocking { kapMap(Backpressure.DropLatest(StreamSources.SMALL_BUFFER)) }

    @Benchmark
    fun kapStreamMapLatest8(): Long = runBlocking { kapMap(Backpressure.Latest(StreamSources.SMALL_BUFFER)) }

    private suspend fun kapMap(backpressure: Backpressure): Long =
        KapStream.from(StreamSources.flow())
            .mapKap(backpressure = backpressure) { value -> Kap.of(value + 1) }
            .asFlow()
            .count()
            .toLong()
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class StreamFilterBenchmark {

    @Benchmark
    fun rawFlowFilter(): Long = runBlocking { StreamSources.flow().filter { it % 2 == 0 }.count().toLong() }

    @Benchmark
    fun rxJavaFilter(): Long =
        Observable.range(0, StreamSources.SIZE).filter { it % 2 == 0 }.count().blockingGet()

    @Benchmark
    fun kapStreamFilterSuspend(): Long = runBlocking { kapFilter(Backpressure.Suspend) }

    @Benchmark
    fun kapStreamFilterBuffer64(): Long = runBlocking { kapFilter(Backpressure.Buffer(StreamSources.BUFFER)) }

    private suspend fun kapFilter(backpressure: Backpressure): Long =
        KapStream.from(StreamSources.flow())
            .filterKap(backpressure = backpressure) { value -> Kap.of(value % 2 == 0) }
            .asFlow()
            .count()
            .toLong()
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class StreamFlatMapBenchmark {

    @Benchmark
    fun rawFlowFlatMap(): Long = runBlocking {
        StreamSources.flow().flatMapMerge(CONCURRENCY) { flowOf(it, it) }.count().toLong()
    }

    @Benchmark
    fun rxJavaFlatMap(): Long = Observable.range(0, StreamSources.SIZE)
        .flatMap({ Observable.just(it, it) }, CONCURRENCY)
        .count()
        .blockingGet()

    @Benchmark
    fun kapStreamFlatMapSuspend(): Long = runBlocking { kapFlatMap(Backpressure.Suspend) }

    @Benchmark
    fun kapStreamFlatMapBuffer64(): Long = runBlocking { kapFlatMap(Backpressure.Buffer(StreamSources.BUFFER)) }

    private suspend fun kapFlatMap(backpressure: Backpressure): Long =
        KapStream.from(StreamSources.flow())
            .flatMapKap(concurrency = CONCURRENCY, backpressure = backpressure) { value ->
                KapStream.from(flowOf(value, value))
            }
            .asFlow()
            .count()
            .toLong()

    private companion object {
        const val CONCURRENCY = 16
    }
}
