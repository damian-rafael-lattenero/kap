window.BENCHMARK_DATA = {
  "lastUpdate": 1790861113967,
  "repoUrl": "https://github.com/damian-rafael-lattenero/kap",
  "entries": {
    "KAP JMH Benchmarks": [
      {
        "commit": {
          "author": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "committer": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "distinct": true,
          "id": "b943ff389678dbc5cc0f67c386b42a5b2d2b1a66",
          "message": "chore(fase-0): purge private npm mirror, clean ABI leaks, add detekt + claim verification\n\n- build(js/wasm): replace furycloud-locked yarn.lock with public registry\n  (root .npmrc); 0 private-registry references remain, JS/WASM/Native compile\n- bench: parameterize jmh includes (-Pjmh.includes), add root 'benchmarks'\n  alias, document smoke run in CONTRIBUTING\n- api(resilience): drop 8 unnecessary @PublishedApi from CircuitBreaker —\n  mutex/state/setters no longer leak into the ABI\n- api(core/arrow): add withPair/thenPair/asPhaseBarrier phase primitives;\n  PhaseBarrier is now internal; withV/thenV rewritten on top of them;\n  ValidatedScope state removed from ABI\n- tooling: detekt on 8 modules with committed baselines (existing findings\n  tolerated, new violations fail) + ben-manes versions plugin; CI jobs for\n  both, wired into ci-gate\n- test: convert RacePropertyTest/CancellationPropertyTest from fixed\n  examples to real Kotest property tests (generated delays/N/positions)\n- docs: real numbers everywhere — 1027 tests / 71 suites / 122 JMH\n  benchmarks (was 906/61/119); scripts/verify-claims.sh + CI 'claims' job\n  fail on any drift between marketing numbers and repo reality",
          "timestamp": "2026-10-01T08:19:38-03:00",
          "tree_id": "f83bd74bf062edaec00626bfebe7ac56e730a76d",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/b943ff389678dbc5cc0f67c386b42a5b2d2b1a66"
        },
        "date": 1790857707152,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010577021861273013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.0001575485238602735,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.54878097599999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.46663327272727,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.43196325999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.38644182200001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.669283616,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018417037245035419,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00014419205383659213,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0019054540022430723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.0001478567254331041,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.0001926797228016413,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00014007601874589562,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.001535449825668281,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.323446625395746,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.001577306475174194,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.28831811978743,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.332229904862054,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.99829037647058,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.92764437647058,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00022774205481085368,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.302356632000006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.319130116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.290229066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0015093036699090764,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.0000982540021566691,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.32896872177748,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.8909322692308,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.028491737813914186,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.469245235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.47055363333328,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.028069878291839677,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.0367576268770031,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.68289894499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.8691876357143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00018684273417063645,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0013887929367698457,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.41189555999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0018422825103607842,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.8467875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.0001456990216592347,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.606466406060605,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.00409707715654872,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0006423396672428919,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.77403806060606,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.74792679696971,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.00778919410823786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 302.64138094285715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.4169507525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.34806580000003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00018577034632494117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.002443863819973072,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014954646995729858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00018346505217820856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00014073704364447024,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.569726831818183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.003938608725329979,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.005242914004348239,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0014467986037159453,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0033797329506508956,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.510892315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.389470442500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.00013528879785065765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.54218897499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.351040004590686,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.27691686777476,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.99412906470589,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.98976689411765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.29105674753505,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0012594050078136364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.0035143333711143863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.819579269696966,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.00023803161786944464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.375805832500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.19643351666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.000125803415178732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.0028731622417862155,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.0038467718073381197,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.00128290611376054,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.002640872893831059,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.45504314000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00009599653907476265,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.83943743571427,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.49834494499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.290339294346456,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 121.03012445882355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.266887787810948,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.1309587125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.88808552,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.56833557450981,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.0004328566205783914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.400919585000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.00042155350339952407,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.316037385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00018202514360361296,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.0872274913697533,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0004156204789635972,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.00040035079386697823,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0022908357849592,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.370703006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.374029009999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.62037230000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007734147892526886,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.31815271230213,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.311800356354592,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.003616743349948178,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.464521586363638,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.397005265151517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.703013108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00010137238719073427,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.3558146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00009674180211476464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00010534854051314675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00009490707771185228,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.344079066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009964005563309371,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 121.04027311764708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.708815616,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.73488100833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "committer": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "distinct": true,
          "id": "45a8c8599288e6a4a511719e2c58087f272118de",
          "message": "feat(fase-1): golden tests for KSP, real property laws, DSL homogeneity\n\nGolden tests (kap-ksp now tests itself):\n- self-processing via kspTest(project(':kap-ksp')) over Fixtures.kt\n- 21 committed golden snapshots covering: nullable fields, parameterized\n  field types, same-type fields, 5-arity, signature-collision fallback\n  (kap{Fn} naming), prefix disambiguation, @KapBridge, and both the plain\n  and Validated code paths (arrow on the test classpath)\n- GoldenTest diffs file set + content; any generator change must be\n  intentional and committed together — drift fails the build\n- Known gap documented: type-parameterized classes (data class Foo<T>)\n  emit unresolvable T references; first real find of the golden suite\n- kap-ksp added to the CI test matrix\n\nReal property-based laws:\n- ApplicativeLawsTest: all laws now use functions derived from arbitrary\n  constants (f(x)=x+k etc.) instead of two fixed examples\n- new FailurePathLawsTest: map/andThen/with over failure, settled-map\n  commutation, recover/orElse absorption; assert type+message since\n  kotlinx-coroutines copies exceptions across coroutine boundaries\n- new PhasePropertyTest: with launches at t=0, then gates subsequent\n  launches, gated siblings overlap, parallel chain takes max(d1,d2)\n- new ValidatedLawsTest (kap-arrow): applicative identity/homomorphism/\n  interchange/composition, NEL accumulation order, thenV/andThenV\n  short-circuit, monad laws, traverseV collection, builder consistency\n\nDSL homogeneity (closes S3 gap #1/#2):\n- infix on andThen (Kap, KapLike), andThenV, and every generated wrapper\n  operator — infix no longer breaks when crossing DSL layers\n- generator emits thenValue/thenValueV per slot + parens forms, so the\n  overlap (no-barrier) semantics is expressible in @KapTypeSafe chains\n- error-policy table (withV accumulates / thenV short-circuits / andThenV\n  binds) documented in Validated.kt KDoc and docs/modules/kap-arrow.md\n\nNumbers: 1053 tests / 74 suites / 122 benchmarks, claims verified",
          "timestamp": "2026-10-01T09:16:24-03:00",
          "tree_id": "06e51b4b0554665caba63256449a41023e4cc37a",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/45a8c8599288e6a4a511719e2c58087f272118de"
        },
        "date": 1790861111898,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010306683683045548,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.000175944423867491,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.501288896,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.473426481818183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.436189492,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.412161086000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.63222301600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.001816238785876097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00013755537257263768,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018826728641323938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.0001385581627535529,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.0001805690560850959,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00014861314644002988,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0015221940343068858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.4091553560606,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0015647303878145971,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.274973242786064,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.27821310624152,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.99143545882353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.94694412352942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.000221297432894375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.323269249999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.347059036,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.35387786000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.001472676963075637,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00009307721951467556,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.33965939043419,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.85660073076923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.028029361538171482,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.46343094250001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.4238134083333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.026925339811929266,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.036430315095813924,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.632988762500005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.8157428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00018879602908254478,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.001356625860748604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.394410952499996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0018414132861465857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.83347885,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00014552571506687182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.509110228787875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.004056122318115047,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0006526716837641893,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.69395083021391,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.653159697058825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.007760946293054867,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 302.30379901428574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.388462495,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.19530882499998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00017997249406282744,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.0024111252018601823,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014293555581744699,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00017366395145082785,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00015139803526990873,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.520247927272727,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.003930832336190524,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.0052295807455319575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0014407046220756819,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.003399615510940353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.45607675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.35587182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.00013563334739469124,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.54547202,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.324904474151964,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.28269592797377,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.97791576470588,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.97704881764705,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.25361884477612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0012974341732835868,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.003502695424581523,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.761964866666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.00023315907879993135,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.36020074,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.14950561666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00012200353111733234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.002910328308598648,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.00410687668983114,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0012832575076813493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.002675309575073915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.45176277,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00009596360064312945,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.82577993571428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.50002645500001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.28613695551786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 121.0097155,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.27612594966078,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.25304554999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 412.16432490000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.6719681637255,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.00046155633688550686,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.4533258575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.0004315667443059856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.31916882,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00017508533716841082,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.088010695755101,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.00040183889507589763,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.0004074638911361284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0023026937305356604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.39101945,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.379507454999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.632978865,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007618898932921037,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.29156420854817,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.297834112505655,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0036120910359574974,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.4626575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.440768180303035,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.722264992,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00010149431206510149,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.373391545000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00010019663668414556,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00010116164938463084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.0000983633005164356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.367881676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009548723834780387,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 121.12524487058825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.78053705199999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.74029805833337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}