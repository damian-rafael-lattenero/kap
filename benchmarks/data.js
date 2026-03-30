window.BENCHMARK_DATA = {
  "lastUpdate": 1774833011263,
  "repoUrl": "https://github.com/damian-rafael-lattenero/kap",
  "entries": {
    "KAP JMH Benchmarks": [
      {
        "commit": {
          "author": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Rafael Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "committer": {
            "email": "damianlattenero@gmail.com",
            "name": "Damian Rafael Lattenero",
            "username": "damian-rafael-lattenero"
          },
          "distinct": true,
          "id": "33f3fea48d7976c09ef08ee101719664502cb2b3",
          "message": "docs: restructure README + KDocs for adoption (conversion optimization)\n\nREADME rewritten for 10-second understanding:\n- New one-liner: \"Stop wiring async calls manually\"\n- ONE hero example (Raw → KAP) instead of triple comparison\n- ASCII execution timeline diagram\n- Three concepts table: .with, .then, .andThen\n- Removed: Arrow comparison, @KapTypeSafe, lazy model, andThen example\n- Links reduced to 3: Get Started, Documentation, Cookbook\n\nkap-core.md restructured into 3 learning levels:\n- Level 1 (Learn First): .with, .then, .andThen\n- Level 2 (Common Patterns): composition, settled, collections, error handling, racing\n- Level 3 (Advanced): construction, thenValue, flow, memoize, interop, observability\n- Added \"Use this when\" micro-sections\n- Removed empty \"Arrow has no equivalent\" tabs (noise)\n- Added API Reference Map table at bottom\n\nLanding page aligned with new vocabulary.\n\nContext: 3,532 clones / 15 stars = 2.5% conversion.\nProblem was communication, not content.",
          "timestamp": "2026-03-29T21:01:40-03:00",
          "tree_id": "31d61251519e58fabdf97feefb4a463cd0350a79",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/33f3fea48d7976c09ef08ee101719664502cb2b3"
        },
        "date": 1774833009826,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010603342504377017,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.00016367876612798164,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.547742528,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.525410237878788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.47968822599999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.427774447999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.71115974,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018305499473717276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00013803694552297845,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018769560467703944,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00014173169720344014,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.0001799655651156396,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00014689303950851358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0014841743193854996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.414921153030303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.001494615709576425,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.329060544911805,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.328448056761648,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 121.10840755294119,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 121.08961419411764,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00023750171329335918,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.389261649999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.408811869999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.42424468,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0014990676357183987,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00010131929757696747,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.415994813636367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.9936396769231,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.028057150279266092,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.471395537499994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.4456871666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.027771786215305138,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.034407180398792175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.6511348325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.82134831428573,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00020012451764719988,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0013439677571908423,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.404085037499996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.00181216882280772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.85430896428574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.0001470502180375345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.54287629545455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.004078951211090591,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0006503544650869578,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.72303667575758,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.69809299696969,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.00777777699877813,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 302.3250584428572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.422748967500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.24558280000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00018418956437003478,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.002387403796365517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014653375306519352,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00017943293113727073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00014330441038221196,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.522351601515158,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.0038229419138018105,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.005224168404950286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0014497203414022524,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.003344429344962212,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.52730742499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.413849205000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.0001362019161066362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.56270823499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.375402472026234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.285460804364543,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 121.01359284117648,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 121.01949820588234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.28239749595206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.001250260002784103,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.003482825724537441,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.787155303030296,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.00023166123806980538,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.36392016999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.13400019166667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00012600902260502433,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.002839175246444845,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.003923847944261428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0012617987358886276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0026361116146017454,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.43377888500001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.0000998048647719228,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.7883971285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.483561795,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.28248954864315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.97265594705884,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.250949609430126,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.15561572500002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.9546374600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.59835008582888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.0004329705638277894,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.4429856625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.000423079893239999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.331503235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00018279708275367114,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.088007966983874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0004275467016992246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.0004252606908363381,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.002199173329495669,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.38195623200001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.387684650000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.62276206499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007663360158988037,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.30199816386251,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.29749134864315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0035380583057639968,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.445604293939397,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.382433331818184,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.72536355999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00009584031264667125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.37891717749999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00009956524023617259,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00010122918392486044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00009545247566775461,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.348472562,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009944417736288328,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.99560084705884,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.695591988,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.71605941666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}