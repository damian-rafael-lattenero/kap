window.BENCHMARK_DATA = {
  "lastUpdate": 1794615870700,
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
          "id": "36187c55d8a07e28947dc8a2147f2aab06e3bc8c",
          "message": "scripts: pinned shebang for audit-fp (brew nu 0.117)",
          "timestamp": "2026-11-13T20:10:33-03:00",
          "tree_id": "69fdbf4c25e1983c0ddc7a0f201112fc2cbe054b",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/36187c55d8a07e28947dc8a2147f2aab06e3bc8c"
        },
        "date": 1794615869294,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00011572217245452215,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.0003246448939409704,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.536666516,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.50385750909091,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.482633462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.464274497999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.71107036800001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018462533093203354,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00015090779351253073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018781095845979672,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00015083313096944198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.00019704435460537922,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.0001624470415908318,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.00140688209121245,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.417433827272724,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0013969654700167228,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.289595923066486,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.261839712075982,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.84527037058822,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.82793059411765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00022750350763192903,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.303938092,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.341511405999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.37380340600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0013831724822273227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00009137331424747514,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.4057696530303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.8523146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.01672038767903341,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.42152243,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.269575475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.01666284181195265,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.04102335750489093,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.5924877525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.7634646714286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.000171487320551653,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0012014998201440488,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.351295972500004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.001735630132521198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.7502623428571,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.0001409470515125202,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.35425052727273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.005056529589376464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0005779985051657181,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.565772513636375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.57916292994652,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.007103024411489316,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 301.76430607142856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.34322416249999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.1441342,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00016065741245005125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.0024387722836211206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00011968299670131956,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00017389754395996302,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00014902169405366854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.42271451363636,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.004562676070519823,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.004858479197254508,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0012960740200640018,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0030100091134577863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.504606002500005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.335388415,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.0001915894745876889,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.52338404,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.338177582971507,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.263241438444147,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 121.00846530588235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.97052493529411,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.250948179375843,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0011429435608494693,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.0032247075038325914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.768442706060604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.00024069813475132703,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.31710028999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.04415870833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.0001272996999717841,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.0025827054238313435,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.003540560748157931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0011141483854231716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0023561354807712384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.41076839999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00008853405916666288,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.66830830714284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.47269177000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.2768416007915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.94606305882353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.277455262211667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.09825058750002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.8843169399999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.5739764057041,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.00034959288772241855,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.4453292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.000345089127495356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.2844136475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00021540813424718403,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.0810053877854946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.00036143914154134214,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.0003454407221966108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.002128982913943117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.337518062,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.40609456,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.616725255,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007356493749412799,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.24138252985075,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.218460386567166,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.00494049871436039,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.361542066666665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.424729083333336,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.697979564,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00011290320491189987,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.303909,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00011435770293853362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00011405119096656366,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00008544572150392943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.23263391,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00011110172368001672,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.67565075294117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.530183816,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.53897124166664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamBaselineBenchmark.rawFlowMapPerElementScope",
            "value": 38.102419769230764,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamBaselineBenchmark.rawFlowMapSuspendCall",
            "value": 9.294261717369507,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFilterBenchmark.kapStreamFilterBuffer64",
            "value": 85.67399321666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFilterBenchmark.kapStreamFilterSuspend",
            "value": 14.541136163721834,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFilterBenchmark.rawFlowFilter",
            "value": 4.860813435303214,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFilterBenchmark.rxJavaFilter",
            "value": 1.8012306827071565,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFlatMapBenchmark.kapStreamFlatMapBuffer64",
            "value": 417.661968,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFlatMapBenchmark.kapStreamFlatMapSuspend",
            "value": 313.950284825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFlatMapBenchmark.rawFlowFlatMap",
            "value": 310.02597572499997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamFlatMapBenchmark.rxJavaFlatMap",
            "value": 56.82871264444443,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapBuffer64",
            "value": 93.10253258181818,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapConflate",
            "value": 43.17924466345455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapDropLatest8",
            "value": 12.570606773749997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapDropOldest8",
            "value": 39.88891985276923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapLatest8",
            "value": 46.258754913636366,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.kapStreamMapSuspend",
            "value": 18.032025690102525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.rawFlowMap",
            "value": 4.8975281892783045,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.StreamMapBenchmark.rxJavaMap",
            "value": 2.05759505924284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}