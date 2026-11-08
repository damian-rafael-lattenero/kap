# Concurrency/FP audit — U0 mechanical inventory for kap (generated 2026-11-08)

Scope: all library modules main sources (KMP: commonMain/jvmMain/native/js) + benchmarks; tests excluded.
EVERY atomics hit here is a PRIMITIVE IMPLEMENTATION suspect (SyncCell wraps AtomicReference) — U1 classifies implementation-internal sync as a distinct verdict: LAWFUL-BY-DESIGN.

- **forbidden**: 153
- **handrolled**: 12
- **impure**: 2
- **errors**: 104

## forbidden

| file | line | text |
|---|---|---|
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 50 | `while (true) {` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 110 | `while (true) {` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 162 | `while (true) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 213 | `while (true) {` |
| `kap-detekt-rules/src/main/kotlin/kap/detekt/ManualWaitLoop.kt` | 14 | `* Flags hand-rolled wait loops: a `while (true)` whose body waits with` |
| `kap-detekt-rules/src/main/kotlin/kap/detekt/ManualWaitLoop.kt` | 15 | `* `kotlinx.coroutines.delay(...)` or `Thread.sleep(...)`.` |
| `kap-detekt-rules/src/main/kotlin/kap/detekt/ManualWaitLoop.kt` | 22 | `* while (true) {` |
| `kap-detekt-rules/src/main/kotlin/kap/detekt/ManualWaitLoop.kt` | 37 | `* function is not reported. `Thread.sleep` must be called on `Thread` or` |
| `kap-detekt-rules/src/main/kotlin/kap/detekt/ManualWaitLoop.kt` | 42 | `"Manual wait loop: a `while (true)` body waits with delay()/Thread.sleep(). " +` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 15 | `import kotlinx.coroutines.runBlocking` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 90 | `fun rawFlowMapSuspendCall(): Long = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 95 | `fun rawFlowMapPerElementScope(): Long = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 111 | `fun rawFlowMap(): Long = runBlocking { StreamSources.flow().map { it + 1 }.count().toLong() }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 117 | `fun kapStreamMapSuspend(): Long = runBlocking { kapMap(Backpressure.Suspend) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 120 | `fun kapStreamMapBuffer64(): Long = runBlocking { kapMap(Backpressure.Buffer(StreamSources.BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 123 | `fun kapStreamMapConflate(): Long = runBlocking { kapMap(Backpressure.Conflate) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 126 | `fun kapStreamMapDropOldest8(): Long = runBlocking { kapMap(Backpressure.DropOldest(StreamSources.SMALL_BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 129 | `fun kapStreamMapDropLatest8(): Long = runBlocking { kapMap(Backpressure.DropLatest(StreamSources.SMALL_BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 132 | `fun kapStreamMapLatest8(): Long = runBlocking { kapMap(Backpressure.Latest(StreamSources.SMALL_BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 151 | `fun rawFlowFilter(): Long = runBlocking { StreamSources.flow().filter { it % 2 == 0 }.count().toLong() }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 158 | `fun kapStreamFilterSuspend(): Long = runBlocking { kapFilter(Backpressure.Suspend) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 161 | `fun kapStreamFilterBuffer64(): Long = runBlocking { kapFilter(Backpressure.Buffer(StreamSources.BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 180 | `fun rawFlowFlatMap(): Long = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 191 | `fun kapStreamFlatMapSuspend(): Long = runBlocking { kapFlatMap(Backpressure.Suspend) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 194 | `fun kapStreamFlatMapBuffer64(): Long = runBlocking { kapFlatMap(Backpressure.Buffer(StreamSources.BUFFER)) }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 14 | `import kotlinx.coroutines.runBlocking` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 50 | `@Benchmark fun raw_overhead_arity3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 57 | `@Benchmark fun kap_overhead_arity3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 62 | `@Benchmark fun arrow_overhead_arity3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 66 | `@Benchmark fun raw_overhead_arity9(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 72 | `@Benchmark fun kap_overhead_arity9(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 80 | `@Benchmark fun arrow_overhead_arity9(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 94 | `@Benchmark fun raw_latency_arity5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 105 | `@Benchmark fun kap_latency_arity5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 114 | `@Benchmark fun arrow_latency_arity5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 122 | `@Benchmark fun sequential_latency_arity5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 133 | `@Benchmark fun raw_latency_multiPhase(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 151 | `@Benchmark fun kap_latency_multiPhase(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 166 | `@Benchmark fun arrow_latency_multiPhase(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 180 | `@Benchmark fun sequential_latency_multiPhase(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 192 | `@Benchmark fun raw_combine3_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 199 | `@Benchmark fun kap_combine3_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 203 | `@Benchmark fun arrow_combine3_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 207 | `@Benchmark fun kap_combine5_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 213 | `@Benchmark fun kap_combine5_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 225 | `@Benchmark fun raw_overhead_arity15(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 231 | `@Benchmark fun kap_overhead_arity15(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 246 | `@Benchmark fun raw_traverse_unbounded_20(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 253 | `@Benchmark fun kap_traverse_unbounded_20(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 259 | `@Benchmark fun raw_traverse_bounded_20_c5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 268 | `@Benchmark fun kap_traverse_bounded_20_c5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 278 | `@Benchmark fun raw_race_two(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 287 | `@Benchmark fun kap_race_two(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 294 | `@Benchmark fun arrow_race_two(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 305 | `@Benchmark fun raw_timeout_with_default(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 309 | `@Benchmark fun kap_timeout_with_default(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 318 | `@Benchmark fun raw_memoize_cold(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 330 | `@Benchmark fun kap_memoize_cold(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 335 | `@Benchmark fun kap_memoize_warm(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 341 | `runBlocking { m.evalGraph() }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 349 | `@Benchmark fun kap_memoizeOnSuccess_cold(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 354 | `@Benchmark fun kap_memoizeOnSuccess_warm(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 360 | `runBlocking { m.evalGraph() }` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 364 | `@Benchmark fun kap_memoizeOnSuccess_failure_retry(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 379 | `@Benchmark fun raw_sequential_3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 384 | `@Benchmark fun kap_computation_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 393 | `@Benchmark fun kap_computation_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 402 | `@Benchmark fun raw_sequential_latency_3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 413 | `@Benchmark fun kap_andThen_chain_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 421 | `@Benchmark fun kap_andThen_chain_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 433 | `@Benchmark fun raw_orElse_chain_3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 440 | `@Benchmark fun kap_orElse_chain_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 446 | `@Benchmark fun kap_orElse_chain_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 456 | `@Benchmark fun raw_firstSuccessOf_5(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 469 | `@Benchmark fun kap_firstSuccessOf_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 476 | `@Benchmark fun kap_firstSuccessOf_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 490 | `@Benchmark fun raw_traverseSettled_10(): List<Result<String>> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 497 | `@Benchmark fun kap_traverseSettled_10_pass(): List<Result<String>> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 503 | `@Benchmark fun kap_traverseSettled_10_half_fail(): List<Result<String>> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 513 | `@Benchmark fun kap_traverseSettled_bounded_20_c5(): List<Result<String>> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 523 | `@Benchmark fun kap_settled_success(bh: Blackhole) = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 527 | `@Benchmark fun kap_settled_failure_no_cancel(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 540 | `@Benchmark fun raw_flow_flatMapMerge_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 547 | `@Benchmark fun kap_flow_mapKap_seq_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 553 | `@Benchmark fun kap_flow_mapKap_c5_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 559 | `@Benchmark fun kap_flow_mapKapOrdered_c5_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 565 | `@Benchmark fun raw_flow_map_overhead_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 571 | `@Benchmark fun kap_flow_mapKap_overhead_10(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 577 | `@Benchmark fun kap_flow_filterKap_10(): List<Int> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 9 | `import kotlinx.coroutines.runBlocking` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 44 | `@Benchmark fun raw_retry_manual_3(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 61 | `@Benchmark fun kap_retry_schedule_times(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 67 | `@Benchmark fun kap_retry_schedule_exponential(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 81 | `@Benchmark fun kap_schedule_fold(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 96 | `@Benchmark fun raw_bracket_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 105 | `@Benchmark fun kap_bracket_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 113 | `@Benchmark fun raw_bracket_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 127 | `@Benchmark fun kap_bracket_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 144 | `@Benchmark fun raw_bracketCase_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 157 | `@Benchmark fun kap_bracketCase_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 170 | `@Benchmark fun kap_bracketCase_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 187 | `@Benchmark fun raw_guarantee_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 195 | `@Benchmark fun kap_guarantee_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 199 | `@Benchmark fun kap_guaranteeCase_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 213 | `@Benchmark fun raw_resource_zip_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 222 | `@Benchmark fun kap_resource_zip_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 229 | `@Benchmark fun kap_resource_zip_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 244 | `@Benchmark fun raw_circuitBreaker_closed(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 258 | `@Benchmark fun kap_circuitBreaker_closed_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 262 | `@Benchmark fun kap_circuitBreaker_closed_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 266 | `@Benchmark fun kap_circuitBreaker_halfOpen_probe(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 282 | `@Benchmark fun raw_timeoutRace_primary_wins(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 291 | `@Benchmark fun kap_timeoutRace_primary_wins(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 296 | `@Benchmark fun raw_timeoutRace_fallback_wins(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 308 | `@Benchmark fun kap_timeoutRace_fallback_wins(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 313 | `@Benchmark fun kap_timeoutRace_vs_timeout(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 322 | `@Benchmark fun raw_raceQuorum_2of5(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 339 | `@Benchmark fun kap_raceQuorum_2of5(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 350 | `@Benchmark fun kap_raceQuorum_3of5(): List<String> = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 361 | `@Benchmark fun kap_raceQuorum_2of3_overhead(): List<String> = runBlocking {` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 85 | `while (true) {` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 101 | `while (true) {` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 115 | `while (true) {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 14 | `import kotlinx.coroutines.runBlocking` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 65 | `@Benchmark fun kap_withV_latency_all_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 77 | `@Benchmark fun arrow_validation_all_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 96 | `@Benchmark fun sequential_validation_all_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 110 | `@Benchmark fun kap_withV_latency_all_fail(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 122 | `@Benchmark fun arrow_validation_all_fail(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 145 | `@Benchmark fun kap_zipV_mixed(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 162 | `@Benchmark fun kap_traverseV_10_all_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 172 | `@Benchmark fun kap_traverseV_10_half_fail(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 186 | `@Benchmark fun kap_traverseV_bounded_20_c5_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 196 | `@Benchmark fun kap_traverseV_bounded_20_c5_half_fail(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 210 | `@Benchmark fun raw_raceEither(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 223 | `@Benchmark fun arrow_raceEither(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 230 | `@Benchmark fun kap_raceEither_latency(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 241 | `@Benchmark fun kap_race_homogeneous_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 248 | `@Benchmark fun kap_raceEither_heterogeneous_overhead(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 263 | `@Benchmark fun kap_ensureV_pass(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 271 | `@Benchmark fun kap_ensureV_fail(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 283 | `@Benchmark fun raw_attempt_success(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 295 | `@Benchmark fun raw_attempt_failure(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 307 | `@Benchmark fun arrow_attempt_success(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 315 | `@Benchmark fun kap_attempt_success(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 323 | `@Benchmark fun kap_attempt_failure(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 335 | `@Benchmark fun kap_catching_success(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 343 | `@Benchmark fun kap_catching_failure(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 355 | `@Benchmark fun arrow_either_builder(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 369 | `@Benchmark fun kap_validated_builder(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 386 | `@Benchmark fun arrow_phased_validation(): String = runBlocking {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 400 | `@Benchmark fun kap_andThenV_phased(): String = runBlocking {` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 263 | `@Test fun `circuitBreaker - kap - half-open recovery`() = kotlinx.coroutines.runBlocking {` |

## handrolled

| file | line | text |
|---|---|---|
| `kap-kotest/src/main/kotlin/kap/kotest/LifecycleMatchers.kt` | 3 | `import java.util.concurrent.CopyOnWriteArrayList` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/StreamBenchmark.kt` | 25 | `import java.util.concurrent.TimeUnit` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 20 | `import java.util.concurrent.TimeUnit` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 14 | `import java.util.concurrent.TimeUnit` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 15 | `import java.util.concurrent.atomic.AtomicInteger` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 245 | `val counter = AtomicInteger(0)` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt` | 18 | `import java.util.concurrent.TimeUnit` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 577 | `// Both `cached` and `cachedError` are @Volatile, guaranteeing` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 3 | `import kotlin.concurrent.atomics.AtomicReference` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 15 | `* as a lock-free atomic register ([AtomicReference] + compare-and-set), so it` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 61 | `private val state = AtomicReference(initial)` |
| `kap-core/src/commonMain/kotlin/kap/SyncCell.kt` | 94 | `* — the read-then-write pair in one step (`AtomicReference.getAndUpdate`).` |

## impure

| file | line | text |
|---|---|---|
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 430 | `val start = System.nanoTime()` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 432 | `events += "fetchUser: ${(System.nanoTime() - start) / 1_000_000}ms"` |

## errors

| file | line | text |
|---|---|---|
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 175 | `name = param.name!!.asString(),` |
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 273 | `name = param.name!!.asString(),` |
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 323 | `name = param.name!!.asString(),` |
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 456 | `Variance.INVARIANT -> renderType(arg.type!!.resolve())` |
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 457 | `Variance.COVARIANT -> "out ${renderType(arg.type!!.resolve())}"` |
| `kap-ksp/src/main/kotlin/kap/ksp/KapTypeSafeProcessor.kt` | 458 | `Variance.CONTRAVARIANT -> "in ${renderType(arg.type!!.resolve())}"` |
| `kap-resilience/src/commonMain/kotlin/kap/RaceQuorum.kt` | 76 | `if (error is CancellationException) throw error` |
| `kap-resilience/src/commonMain/kotlin/kap/RaceQuorum.kt` | 81 | `throw primary` |
| `kap-arrow/src/main/kotlin/kap/Validated.kt` | 132 | `if (e is CancellationException) throw e` |
| `kap-arrow/src/main/kotlin/kap/Validated.kt` | 254 | `if (e is CancellationException) throw e` |
| `kap-arrow/src/main/kotlin/kap/Validated.kt` | 276 | `is Either.Left -> throw ValidationException(it.value)` |
| `kap-arrow/src/main/kotlin/kap/Validated.kt` | 323 | `throw ValidatedShortCircuit()` |
| `kap-resilience/src/commonMain/kotlin/kap/Supervisor.kt` | 171 | `} catch (ce: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Supervisor.kt` | 266 | `} catch (e: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Supervisor.kt` | 267 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/Supervisor.kt` | 344 | `onFailure = { throw it },` |
| `kap-ktor/src/main/kotlin/kap/ktor/KapPlugin.kt` | 86 | `?: throw IllegalArgumentException(` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 153 | `if (!tryAcquire()) throw CircuitBreakerOpenException()` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 157 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 160 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 256 | `throw CircuitBreakerOpenException()` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 264 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/CircuitBreaker.kt` | 267 | `throw e` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 42 | `if (e is CancellationException) throw e` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 83 | `throw errA` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 97 | `throw errB` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 100 | `} catch (e: CancellationException) {` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 101 | `throw e` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 161 | `} catch (e: CancellationException) {` |
| `kap-arrow/src/main/kotlin/kap/ArrowExtensions.kt` | 162 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 54 | `if (e is CancellationException) throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 66 | `throw lastError` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 89 | `// ── retryOrElse: fallback instead of throw on exhaustion ────────────` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 114 | `if (e is CancellationException) throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 167 | `if (e is CancellationException) throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 179 | `throw lastError` |
| `kap-resilience/src/commonMain/kotlin/kap/ResilienceCombinators.kt` | 213 | `throw CancellationException("repeat cancelled between iterations")` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 324 | `cached!!` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 466 | `result!!` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 507 | `if (i % 2 == 0) throw RuntimeException("fail-$i")` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt` | 530 | `.with(Kap<String> { throw RuntimeException("down") }.settled())` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 104 | `} catch (e: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 106 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 109 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 143 | `} catch (e: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 145 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/Bracket.kt` | 148 | `throw e` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 53 | `} catch (_: Exception) {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 151 | `throw e` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 252 | `} catch (e: Exception) {` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 254 | `throw e` |
| `benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt` | 301 | `catch (_: Exception) { networkCall("fallback", 30) }` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 169 | `} catch (ce: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 218 | `} catch (e: CancellationException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 219 | `throw e` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 249 | `} catch (_: RuntimeException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 258 | `} catch (_: RuntimeException) {` |
| `kap-resilience/src/commonMain/kotlin/kap/Process.kt` | 321 | `onFailure = { throw it },` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 41 | `if (attempts < 3) throw RuntimeException("flaky")` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 44 | `} catch (_: RuntimeException) {` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 57 | `if (attempts < 3) throw RuntimeException("flaky")` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 67 | `if (attempts < 3) throw RuntimeException("flaky")` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 126 | `outcome = "failed:${e.message}"; throw e` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 230 | `if (isOpen) throw IllegalStateException("circuit open")` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 235 | `} catch (e: Exception) {` |
| `benchmarks/src/test/kotlin/kap/ResilienceComparisonTest.kt` | 238 | `throw e` |
| `benchmarks/src/test/kotlin/kap/ArrowComparisonTest.kt` | 204 | `private suspend fun riskyCall(): String = throw IllegalArgumentException("bad input")` |
| `benchmarks/src/test/kotlin/kap/ArrowComparisonTest.kt` | 209 | `} catch (e: Exception) {` |
| `benchmarks/src/test/kotlin/kap/ArrowComparisonTest.kt` | 309 | `// OR-THROW — unwrap validated or throw` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 40 | `private suspend fun fetchUserFailing(): UserProfile { throw RuntimeException("network error") }` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 44 | `if (attemptCount < 3) throw RuntimeException("flaky attempt $attemptCount")` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 348 | `val result = try { fetchUserFailing() } catch (_: RuntimeException) { UserProfile("Fallback", 0) }` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 377 | `try { attempts++; result = if (attempts < 3) throw RuntimeException("fail") else "success"; return@repeat }` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 378 | `catch (_: RuntimeException) { delay(10.milliseconds) }` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 490 | `if (attempts < 3) throw RuntimeException("flaky")` |
| `benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt` | 494 | `} catch (_: RuntimeException) { delay(10.milliseconds) }` |
| `kap-core/src/commonMain/kotlin/kap/Race.kt` | 48 | `throw firstError` |
| `kap-core/src/commonMain/kotlin/kap/Race.kt` | 50 | `} catch (e: CancellationException) {` |
| `kap-core/src/commonMain/kotlin/kap/Race.kt` | 51 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Race.kt` | 88 | `if (error is CancellationException) throw error` |
| `kap-core/src/commonMain/kotlin/kap/Race.kt` | 94 | `throw primary` |
| `kap-core/src/commonMain/kotlin/kap/Tracing.kt` | 38 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Interop.kt` | 91 | `} catch (e: CancellationException) {` |
| `kap-core/src/commonMain/kotlin/kap/Interop.kt` | 92 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Collections.kt` | 126 | `* val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!! }` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 82 | `if (e is CancellationException) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 94 | `if (e is CancellationException) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 137 | `if (e is CancellationException) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 138 | `if (!shouldRetry(e)) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 147 | `throw checkNotNull(lastException) {` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 172 | `if (predicate(a)) a else throw error()` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 192 | `extract(a) ?: throw error()` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 217 | `if (e is CancellationException) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 248 | `if (e is CancellationException) throw e` |
| `kap-core/src/commonMain/kotlin/kap/Combinators.kt` | 254 | `throw primary` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 53 | `fun failed(error: Throwable): Kap<Nothing> = Kap { throw error }` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 114 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 590 | `cachedError?.let { throw it }` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 596 | `cachedError?.let { throw it }` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 601 | `} catch (e: CancellationException) {` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 603 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 607 | `throw e` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 652 | `throw e  // failure NOT cached — next caller retries` |
| `kap-core/src/commonMain/kotlin/kap/Kap.kt` | 778 | `if (e is CancellationException) throw e` |

