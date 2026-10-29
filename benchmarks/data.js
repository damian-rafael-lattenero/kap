window.BENCHMARK_DATA = {
  "lastUpdate": 1793291757135,
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
          "id": "2dd79749561bc9f30364523d5782767d39e454ed",
          "message": "4.2.0: Process — the supervised loop, SPEC-5.0 milestone 4.2 complete. Kap<A>.process(name, restart, heartbeatEvery, onRestart, onHeartbeat, timeSource) makes a lazy Process; startIn(scope) interprets it as a child of the given scope (structured concurrency, no daemon escape) and returns a ProcessHandle with stop() (idempotent, never a failure), await(), isAlive, restarts StateFlow and heartbeatAge(). The body completing normally finishes the process; a failure restarts per RestartPolicy (constant/exponential, maxRestarts folded INTO the policy so the convergence law is a property of the value — positive delays, finite cap, private constructor makes an unlawful policy inexpressible); exhaustion fails await() with ProcessExhaustedException. The heartbeat beats at its own period while alive — mid-body and during restart delays — the codified lesson of the bot's kill-loop incident (a beat that only fired at cycle end read a healthy poller as dead); stop()/scope death never restart, observers are transparent by law. Laws in LAWS.md enforced by ProcessTest (virtual time) + ProcessLawsTest (property-checked + bounded-restart: an instantly-failing body runs exactly maxRestarts+1 times). Tests +16 (1112 across 82 suites), apiCheck dump, README/module page/CHANGELOG/SPEC truth pass, verify-claims green.",
          "timestamp": "2026-10-29T01:32:30-03:00",
          "tree_id": "3d923fce2b437f113ac2ed4ef334c1f1383fe743",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/2dd79749561bc9f30364523d5782767d39e454ed"
        },
        "date": 1793252431502,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010119126720418062,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.00032474099790522944,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.41524287600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.260904169923112,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.28681136,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.261432734,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.41511929999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0010477365970653757,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.0001424312341368453,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0011201883336071192,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00014174396679131283,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.00015846637203110998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.0001490175392203962,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0009625957127510666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.205127303165987,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0009502347750577706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.14960152238806,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.159344337313435,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.71172404117647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.70765978235292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00016729042433510525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.240223044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.312969328,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.365356176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0007326883141790345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00009020259605829785,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.157121398507464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.4911649,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.01015539680924939,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.177907357500004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 180.70715578333335,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.010142051506824406,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.023536575530503277,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.294004224999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.45336057142853,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.0001558220709102249,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0008463298601193662,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.140819902500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0012560259386598575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.5550837857143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00013362060165001522,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.33469770800542,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.0030112920413176325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.000636254904983151,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.56640803039217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.559622258556146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.005738058994730058,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 301.4935171,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.2568048325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 180.97776715833334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.0001816309417006366,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.0015067872748950636,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00013845816834021026,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.0001844111240953932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00013163307323553784,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.315598174943467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.002727289649716175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.0034179288895510953,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0009117638489914275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0022048568151890207,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.4353821225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.283211877499994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.00018120047369953697,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.405168445,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.18151461641791,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.122675819402986,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.50857836470588,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.63173337647056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.174646129850743,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0008314086756886146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.0017486542165282306,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.51159093065953,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.0001612932982326046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.303728115,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.02119158333335,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00012634668396538745,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.0013725348711413473,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.0029433256682193966,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0008317550493634147,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0019029928327952525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.353682755,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.0000839734288207853,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.6482125357143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.440769795,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.213613225327908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.75904380588236,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.193877219108998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 250.8970760875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.5481525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.463546308823524,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.0002241148448367369,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.35455904499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.0002350422780232561,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.2485031925,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00023481126453559834,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.064329016338819,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0002352346491556017,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.0002337041583316794,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0013941833009036932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.23974465799999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.249530594999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.51012051000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.00042980873455640973,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.274217228946178,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.259627664744464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0028566106851287227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.385385798507464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.27633432105382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.528580312,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00009001769653252959,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.28470080750001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00008403191966959769,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00008976182291316338,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.0000856842861986732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.13931334600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009166463227241176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.58739812941175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.29437788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.347841675,
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
          "id": "d6b5d2c2af5a15cae8716a216d613d3ce2ce9a62",
          "message": "fix(ci): macOS publish job installs Corretto 21 (daemon JVM criteria) + Zulu 25 (compile toolchain) — since 4.1.0 ('toolchain to current') gradle/gradle-daemon-jvm.properties pins the Gradle daemon to a LOCAL Corretto 21 with no download URLs, but the publish job still installed Zulu 21, so the macOS runner failed with 'No defined toolchain download url for MAC_OS on aarch64' (v4.1.0/v4.2.0 release attempts, 2026-10-29). The ubuntu tests job already matched (corretto 21); now both jobs agree.",
          "timestamp": "2026-10-29T10:07:47-03:00",
          "tree_id": "2f5d44df82d70c70858ee32b03f89deb2c863a43",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/d6b5d2c2af5a15cae8716a216d613d3ce2ce9a62"
        },
        "date": 1793283374364,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00009352008818771735,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.0003237734209717116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.547700904,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.49074712121212,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.449846588,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.430859304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.65944432800002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018187734709970646,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.0001481489433458167,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018158782269148681,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00015067744087141413,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.00019121009192970325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.0001526724527103859,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0013910634897507286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.399665387878787,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0014478558393348506,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.319386856897324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.32993607878788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 121.0355871529412,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.99139356470589,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.0002508136134975465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.359585212,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.373107239999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.390812484,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.001309532057748042,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00011431411462414053,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.39430132727273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.8837266769231,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.016842799137338167,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.4098483625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.22449433333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.016674102432077155,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.04234430769644717,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.60426619,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.75845437857143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00020588784495951677,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.001197776968661289,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.3690571975,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0017300396079485063,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.67274532142855,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00012854542989621437,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.427950140909097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.004877290596739772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0005880466140423043,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.5188602735294,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.63729258244207,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.00677224369291522,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 301.8476108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.370848815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.15793272500005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00018606068598312164,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.0023427729431664984,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014600977784055776,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.0001859703518343373,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00013674905955141347,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.432828748484848,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.004455814681122456,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.0048252265954180345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.001311982822573899,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0030373228584049264,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.474209942499996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.3262117025,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.0001932776057103143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.49927046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.314068536137494,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.26679551264134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 121.00452452941174,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.8981174352941,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.26161944755766,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0011401829035241604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.003102724288662402,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.746345136363644,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.0002546313204382223,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.35439097249999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.0519031833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00012763239944544309,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.00246996693099799,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.0036064642364950043,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0011103055798539444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.002310675696842559,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.41927835499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00009875664559656303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.68008655714286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.47515779499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.24968934925373,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.88186294705883,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.184745368656717,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 250.73646061249997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.54400609999993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.42412094411763,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.00037274581859253486,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.39489951,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.00036188211836997344,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.2775941675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00021436924229420576,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.083241520052493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0003701705116753864,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.0003675599495477752,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0021333457643355055,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.321156544000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.3222082475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.549410105,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007188337214934151,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.251192140298503,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.223885692537316,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.004613454467343676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.341913844301217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.324586100836722,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.56337930800001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00009834041482039338,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.348193034999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.0001118122651755667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00011183825572481382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00011228514677198455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.28336086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009774386186221966,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.77682135882351,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.522651332,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.60694685000004,
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
          "id": "a677f9f7a8a7a0c9bbf510a65ce697c5b747dacf",
          "message": "fix(ci): deterministic JVM resolution in the macOS publish job — setup-java installs live in the runner toolcache, outside Gradle's auto-detection paths, and the daemon criteria (Corretto 21, no download URLs) still couldn't see the installed Corretto (second failed attempt, 2026-10-29). Now: JDK 25 first (captured to JDK25_HOME), Corretto 21 last (JAVA_HOME itself matches the daemon criteria), and both homes passed via ORG_GRADLE_JAVA_INSTALLATIONS_PATHS so no auto-detection is required.",
          "timestamp": "2026-10-29T10:15:06-03:00",
          "tree_id": "f07bc28d869c36f0d7d5948d55948575b7bda69a",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/a677f9f7a8a7a0c9bbf510a65ce697c5b747dacf"
        },
        "date": 1793283788592,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00008768287505942639,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.00024719109661143723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.50231743200001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.42428401515151,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.40841833,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.368381136,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.57628410400001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0014061186238219828,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.0001231699365136046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.001419800866187066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00012379474782419101,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.00015003391325755318,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00012608078733210732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0011019087945410117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.2831844393261,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0011031092197408858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.240973538805967,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.269229015739484,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.89438111764707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.8884340117647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.0001688718454575241,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.283146602,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.296425766,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.281912992,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0010047275677981628,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00009681251307531882,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.288239965445502,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.74879063846154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.011887624697970016,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.322390075,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.05606180833334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.011811291034914618,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.029535014500988954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.468903067499994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.62644728571428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00012540862824197895,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0008753809898439343,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.28851529999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0013328625690304757,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.62610301428572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00010729399655204433,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.41343591818181,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.0038081065396432306,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0004418681328911659,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.53655229340465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.55871024269162,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.005057920664238981,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 301.66836511428573,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.308496510000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.009648375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.0001256375542195834,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.0018031684701350554,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00010136995110427623,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00013627689902005738,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00010134667046989545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.382659812121215,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.0034575536345637673,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.0038273318609974337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0009630388304450761,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0023594305392528594,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.399880409999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.279042685,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.0001746169828371348,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.48321509499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.304288708457715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.217354476119404,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.86832114117647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.8473699882353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.212671183582085,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0008209209168926416,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.002375968820187496,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.62191497709447,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.0001944152016306221,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.317080505,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 180.93719985833334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00010307562055936108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.001960036010650091,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.002719316424935557,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0008468808482625035,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.001795950486327422,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.355305825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00007226556847802843,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.66516802857146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.432949965,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.22739010149254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.85842068823531,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.202142991044774,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 250.96231468749997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.57827901999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.45511404117648,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.000237591701797772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.3372853125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.00024723365227930514,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.2464989275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00017193502236664915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.070276092521452,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.00023680433978550975,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.00022530154846158503,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0017229701946844158,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.27327255600001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.308644279999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.501114935,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0005217333086252219,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.213593697014925,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.203171138805963,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0035664451434589844,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.314139015943017,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.329246506829485,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.578322944,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.0000924897358310824,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.27590709249999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.0000921783069625723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.0000923166135794518,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00009259491640178344,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.262105384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009222840717584716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.80253947647061,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.55379208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.617443525,
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
          "id": "6c6a02a4ca733aafae54a86402aee9658f0517c1",
          "message": "detekt 2.0.0-alpha.6 + single JDK 25 across the build — closes the dual-JVM era. detekt 1.23.8 → dev.detekt 2.0.0-alpha.6 runs its analysis in isolated workers on any JDK >= 17, so the Gradle daemon no longer needs JDK 21 (the 1.23 embedded-compiler limits: no JDK >= 24 runtime, targets stop at 21). gradle/gradle-daemon-jvm.properties deleted — the daemon now runs on the same Corretto 25 as the jvmToolchain(25) compile. ci.yml loses its 8 duplicated JDK-21 setup-java blocks; publish.yml collapses to one setup-java (the JDK25_HOME capture + ORG_GRADLE_JAVA_INSTALLATIONS_PATHS workaround from the 4.2.0 release is obsolete). build.gradle.kts: the daemon-version dance (DETEKT_MAX_DAEMON_JDK, skip-with-warning) and the custom detektCommonMainTR task are gone — detekt 2.0 generates type-resolved tasks per compilation (detektMain JVM / detektMainJvm KMP, which covers common sources through the jvm compilation), wired into detektAll; module baselines wired explicitly. Baselines regenerated for the 2.0 engine, one file per flavour now (detekt-baseline.xml plain + detekt-baseline-main.xml type-resolved): the 1.x single-file 415 re-accounts per flavour (same findings — LongParameterList's 41 arity-overload entries moved to the TR file — while rules that stopped firing dropped off); tolerated-until-Fase-1 policy unchanged, any NEW violation still fails. CONTRIBUTING: JDK 25 to build (artifacts still run on 21+). Verified locally on Corretto 25 daemon+toolchain: detektAll green; full test matrix (core/resilience jvmTest, arrow/kotest/ktor/ksp/benchmarks) + apiCheck green.",
          "timestamp": "2026-10-29T11:52:30-03:00",
          "tree_id": "692ee09d0949176cfeb144a09726862a63d3dfbb",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/6c6a02a4ca733aafae54a86402aee9658f0517c1"
        },
        "date": 1793289641951,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.000104345961957731,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.0003348506937578079,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.52284628800001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.454950451515156,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.448108314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.381338816,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.631451852,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.000998808496239538,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00014037133844743712,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.001061220683719721,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00014279326715079262,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.0001576739712678031,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00015195308032908147,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0009857502371190955,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.168182323880593,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.000959523070383725,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.148085516417904,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.18338435671642,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.84631841764708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.80580680588237,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00017250534087222518,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.258194438,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.218465546,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.273665278,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0007354268660710516,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.0000920974943381458,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.17844693731343,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.4879343,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.009879647690725486,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.203861784999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 180.683132175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.009989085161756499,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.023777163396344288,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.29806817749999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.4377545142857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00014879912796577315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.000885869944105059,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.191696227499996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.001252339452497996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.4053629357143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00013386506893887815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.313232229828134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.0031091875326768087,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0006612352463904864,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.67906423930482,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.656591864795004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.0058374981789808805,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 302.0245794285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.212088412499995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.01340540833334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00018061559337424863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.001495319481383504,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014511605793575457,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00018120571020843106,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.0001343886116416636,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.372976974242427,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.0028886338245246467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.0035218429251410075,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0009460658291761164,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.002246759706270126,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.48926537999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.335495525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.00018886588249503462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.50055803000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.314625663817274,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.254009874174585,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.95834348235296,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.89636428235296,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.17111196268657,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0008511054511576536,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.0017721115988239707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.71477659090908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.00016053518718734744,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.284797915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 180.98375235833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00013191432263606254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.0013854771527698011,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.0029694322175618315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0008310221651820817,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0018998911817852044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.333525245,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00008561886699501504,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.64415335714287,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.45226418500002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.203521802985072,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.809242,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.17345907014925,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.01576475000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.68023780000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.54176115944741,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.00023836310212883277,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.3846373425,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.00024071191893531744,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.274394615,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00024455739213039907,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.0661847054580464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.00023003657987134582,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.00023016405952379148,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0014379709938994738,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.34524570399999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.361193177500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.56927355999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0004531887411505928,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.24090223606965,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.14817124179104,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0028636921563479976,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.42854382376753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.411428440909088,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.700687756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00009379288455698282,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.3591909025,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00009307745584268537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00009212715201189551,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00009245329288567908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.334269426000006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009245995665043232,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.97635668235293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.667597428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.66988686666667,
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
          "id": "8ff355b1fc620585a5bf302ed4b8a639bc2f782e",
          "message": "fix(settings): pluginManagement falls back to Maven Central — the detekt 2.0 plugin's classpath transitively needs io.github.detekt.sarif4k:sarif4k:0.6.0, which the Gradle Plugin Portal only mirrors behind redirects; a portal CDN flake made the fresh-checkout Codegen CI job die with 'Could not find sarif4k:0.6.0' (run 40895852172, job Codegen · Up-to-date) while warm caches elsewhere masked it. Central is the artifact's canonical home; with both repositories declared, plugin resolution has no single point of failure. Verified with --refresh-dependencies (full network re-resolution) on the JDK 25 daemon.",
          "timestamp": "2026-10-29T12:27:12-03:00",
          "tree_id": "54761390a43bdf0c5e8ee8141ec1a3ea9e8ad514",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/8ff355b1fc620585a5bf302ed4b8a639bc2f782e"
        },
        "date": 1793291755190,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010400903417842723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.0003067262427972625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.37213244,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.365154006060607,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.325189668,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.307248224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.43970262799999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018531843419049058,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00015129001177454642,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018797412553286895,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00014541071563269596,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.00018046455008486703,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.00015132073389698835,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0014004057184977957,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.272969273360467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0014160632036934507,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.24845261322931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.239003438805973,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.83230198235294,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.88984190588235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.0002417948078815099,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.26450456599999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.256045544,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.263111101999996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0013604334944822276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00011415694262598998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.289285534938937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.77366724615385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.016598990276408054,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.41421227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 181.34494684166665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.016855584965040325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.0418683028109942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.6284857725,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.77184779285716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00021262630787891,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0011979084072980507,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.35158169,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0017282245843175024,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.7379753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.00012518987306080225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.3997096,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.005026050059602483,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.0005752973840549379,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.6975053083779,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.535211935294114,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.006993081089160547,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 302.1255774714286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.33759851000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 181.0467153416667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.00016795234641310298,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.00235376955240656,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00015410387302539514,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00018435928996591208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00013783896281539148,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.531240104545454,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.0045915435797730985,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.004751722672554388,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0013299097338087666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.003081934819515031,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.500966322500005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.373211637500006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.0002098906614805057,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.55258917500001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.37588073385346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.30175880576662,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 121.07394815882353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 121.0352114470588,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.288542399638175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.001132096180003086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.0032292443087884016,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.86860130303031,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.0002678089193349451,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.364225065,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 181.10563122500002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00013780929655448913,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.0025662037480101868,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.003494025176549393,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0011313216926614879,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0023048835610040654,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.45199930999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.00010072213765434626,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.75706297857144,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.48810874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.304252799592938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 121.01665659411765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.249279110854815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 251.1191209375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 412.04493623999986,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.603282559447415,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.00037970837187430634,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.4496533975,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.0003589512589148362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.339715405,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00021194141288552234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.0850699301743325,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0003732975546829875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.00037044256391490464,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.002109961538348045,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.426214188,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.378860902499994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.62644822,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007185465164661788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.3088408909317,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.291534376232477,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.004705224595656724,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.487514957575762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.47627668484849,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.609824996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.00011189932289237506,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.3787537275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00011203986781601232,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00008653113148715393,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.000100069643218625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.365344879999995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009765583846996876,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 121.06606696470585,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.78273199200001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.75567361666668,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}