window.BENCHMARK_DATA = {
  "lastUpdate": 1775252463023,
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
          "id": "da5aadf1ea4314a134e3ad31559317df55135cc0",
          "message": "docs: rewrite README based on analysis of 10 top open source READMEs\n\nStudied: Arrow, kotlinx.coroutines, kotlinx.serialization, Exposed,\nKoin, Retrofit, RxJava, Gson, Next.js, Express. Applied the winning\npattern: hook → code → install → features → scale.\n\nKey changes:\n- Cut from 318 to ~177 lines (45% reduction)\n- \"The bug nobody catches\" moved to first section (emotional hook)\n- Install moved from line 283 to line 55 (first 30%)\n- Added category one-liner: \"Compile-time safe coroutine orchestration\n  for Kotlin Multiplatform\"\n- Replaced link-heavy \"What only KAP can do\" with scannable bullet list\n- Removed andThen/UserContext complex example (belongs in docs)\n- Removed redundant Dashboard raw-coroutines comparison\n- Removed verbose Type-safe/Bridge/Function sections (docs material)\n- Added link to kap-starter for 30-second onboarding\n- Also updated kap-starter repo to 2.5.0 with @KapTypeSafe",
          "timestamp": "2026-04-03T17:32:51-03:00",
          "tree_id": "76b65852bdd2fdc709418db0a42767077e1bd84e",
          "url": "https://github.com/damian-rafael-lattenero/kap/commit/da5aadf1ea4314a134e3ad31559317df55135cc0"
        },
        "date": 1775252462470,
        "tool": "jmh",
        "benches": [
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_attempt_success",
            "value": 0.00010183589213082442,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_either_builder",
            "value": 0.00017076314593815378,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_phased_validation",
            "value": 80.378579236,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_raceEither",
            "value": 30.405084389393938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_fail",
            "value": 40.34900398999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.arrow_validation_all_pass",
            "value": 40.324019748000005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_andThenV_phased",
            "value": 80.46982496000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_failure",
            "value": 0.0018164740593637654,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_attempt_success",
            "value": 0.00013446982601230152,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_failure",
            "value": 0.0018942432832525537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_catching_success",
            "value": 0.00013934579560016022,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_fail",
            "value": 0.0001779908428132605,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_ensureV_pass",
            "value": 0.0001407565545419309,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_heterogeneous_overhead",
            "value": 0.0014824771596109293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_raceEither_latency",
            "value": 30.259634145341476,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_race_homogeneous_overhead",
            "value": 0.0015143714755839438,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_all_pass",
            "value": 30.215061037313433,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_10_half_fail",
            "value": 30.21886004328358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_half_fail",
            "value": 120.80118169999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_traverseV_bounded_20_c5_pass",
            "value": 120.76352229411764,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_validated_builder",
            "value": 0.00022484204561283707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_fail",
            "value": 40.23264210000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_withV_latency_all_pass",
            "value": 40.239019038,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.kap_zipV_mixed",
            "value": 40.247907377999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_failure",
            "value": 0.0014791131133146166,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_attempt_success",
            "value": 0.00009466719565839674,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.raw_raceEither",
            "value": 30.250685344776123,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ArrowBenchmark.sequential_validation_all_pass",
            "value": 160.64559300000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_combine3_overhead",
            "value": 0.027826972650363935,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_arity5",
            "value": 50.327006565,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_latency_multiPhase",
            "value": 180.98783841666668,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity3",
            "value": 0.027112171125176367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_overhead_arity9",
            "value": 0.03317997616149023,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.arrow_race_two",
            "value": 50.481923885,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_latency",
            "value": 150.59735597142858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_andThen_chain_overhead",
            "value": 0.00018924319672944246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine3_overhead",
            "value": 0.0013195293549344705,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_latency",
            "value": 50.246437625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_combine5_overhead",
            "value": 0.0018250187738825456,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_latency",
            "value": 150.5274179,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_computation_overhead",
            "value": 0.0001507887663739185,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_latency",
            "value": 30.425713484848483,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_firstSuccessOf_overhead",
            "value": 0.004556232439127485,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_filterKap_10",
            "value": 0.000645111783368285,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKapOrdered_c5_10",
            "value": 60.490384985472375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_c5_10",
            "value": 60.47560480588235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_overhead_10",
            "value": 0.007728164940905976,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_flow_mapKap_seq_10",
            "value": 301.6454339285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_arity5",
            "value": 50.24521492,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_latency_multiPhase",
            "value": 180.83340554999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_cold",
            "value": 0.0001754620325821403,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_failure_retry",
            "value": 0.002409596427574971,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoizeOnSuccess_warm",
            "value": 0.00014584504076360882,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_cold",
            "value": 0.00017583343923975977,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_memoize_warm",
            "value": 0.00014205987950110225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_latency",
            "value": 30.414162781818174,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_orElse_chain_overhead",
            "value": 0.0038917888747423664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity15",
            "value": 0.005149982022228624,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity3",
            "value": 0.0014281259983722073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_overhead_arity9",
            "value": 0.0032859373316835626,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_race_two",
            "value": 50.3308971025,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_failure_no_cancel",
            "value": 50.24090794000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_settled_success",
            "value": 0.00014502395991957797,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_timeout_with_default",
            "value": 100.37438944,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_half_fail",
            "value": 30.237517226865673,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_10_pass",
            "value": 30.206063117910453,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverseSettled_bounded_20_c5",
            "value": 120.77485914705883,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_bounded_20_c5",
            "value": 120.76588103529411,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.kap_traverse_unbounded_20",
            "value": 30.195999073134324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_combine3_overhead",
            "value": 0.0012321141329987915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_firstSuccessOf_5",
            "value": 0.003485687981458535,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_flatMapMerge_10",
            "value": 60.521299441176474,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_flow_map_overhead_10",
            "value": 0.0002422462851847603,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_arity5",
            "value": 50.229675529999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_latency_multiPhase",
            "value": 180.80204825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_memoize_cold",
            "value": 0.00012223334103828742,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_orElse_chain_3",
            "value": 0.002858680130144126,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity15",
            "value": 0.003810181514896374,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity3",
            "value": 0.0012517452915069601,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_overhead_arity9",
            "value": 0.0026195043910342375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_race_two",
            "value": 100.28917231,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_3",
            "value": 0.0000962169322204958,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_sequential_latency_3",
            "value": 150.5182909642857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_timeout_with_default",
            "value": 100.31635311500001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverseSettled_10",
            "value": 30.20040834626866,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_bounded_20_c5",
            "value": 120.75109367058823,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.raw_traverse_unbounded_20",
            "value": 30.18674692686567,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_arity5",
            "value": 250.88755444999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.CoreBenchmark.sequential_latency_multiPhase",
            "value": 411.44172788,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_latency",
            "value": 60.43237291764706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracketCase_overhead",
            "value": 0.0004289937131160132,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_latency",
            "value": 50.277154245000006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_bracket_overhead",
            "value": 0.0004171696256733786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_latency",
            "value": 50.217490972499995,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_closed_overhead",
            "value": 0.00018280751140933721,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_circuitBreaker_halfOpen_probe",
            "value": 2.086234676534486,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guaranteeCase_overhead",
            "value": 0.0004135510669580223,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_guarantee_overhead",
            "value": 0.00040099221863151336,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of3_overhead",
            "value": 0.0022644510049991914,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_2of5",
            "value": 40.274748286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_raceQuorum_3of5",
            "value": 50.273116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_latency",
            "value": 100.40471916999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_resource_zip_overhead",
            "value": 0.0007631779322872542,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_exponential",
            "value": 30.202225262686568,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_retry_schedule_times",
            "value": 30.196180514925373,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_schedule_fold",
            "value": 0.0035490495090905747,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_fallback_wins",
            "value": 30.30615549468566,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_primary_wins",
            "value": 30.295712305178647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.kap_timeoutRace_vs_timeout",
            "value": 80.48911818799999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracketCase_overhead",
            "value": 0.0000955524751052263,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_latency",
            "value": 50.24042963250001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_bracket_overhead",
            "value": 0.00010324900786278185,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_circuitBreaker_closed",
            "value": 0.00009814644556864714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_guarantee_overhead",
            "value": 0.00009299041962672878,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_raceQuorum_2of5",
            "value": 40.257563440000006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_resource_zip_overhead",
            "value": 0.00009551930357530162,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_retry_manual_3",
            "value": 120.82001957058824,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_fallback_wins",
            "value": 80.504671976,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          },
          {
            "name": "kap.benchmarks.ResilienceBenchmark.raw_timeoutRace_primary_wins",
            "value": 180.51913251666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 2\nthreads: 1"
          }
        ]
      }
    ]
  }
}