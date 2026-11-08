#!/usr/bin/env nu
# U0 of the FP/concurrency audit for kap itself (2026-11-08) — the same
# machinery the three consumer repos ran, adapted to kap's KMP layout:
# scope = all library modules' main sources (commonMain/jvmMain/...) +
# benchmarks; tests excluded (kap's laws live there and are property-tested).
# The expectation is documented: atomics inside kap are the IMPLEMENTATION of
# the primitives (SyncCell wraps AtomicReference) — U1 must classify
# implementation-internal synchronization as LAWFUL-BY-DESIGN, distinct from
# consumer-side hand-rolling.

const REPO: string = '/Users/lattenero/Projects/kap'

def scan-one [label: string, pattern: string]: nothing -> table<category: string, file: string, line: int, text: string> {
  let res: record = (do { rg -n --no-heading -e $pattern $REPO -g 'kap-*/src/main/**' -g 'kap-*/src/commonMain/**' -g 'kap-*/src/jvmMain/**' -g 'kap-*/src/nativeMain/**' -g 'kap-*/src/jsMain/**' -g 'benchmarks/src/**' } | complete)
  if $res.exit_code != 0 { return [] } # rg exit 1 = zero matches: honest empty
  $res.stdout | lines | each {|l|
    let parts: list<string> = ($l | split row ':')
    {
      category: $label
      file: ($parts | first | str replace ($REPO + '/') '')
      line: ($parts | get 1 | into int)
      text: ($parts | skip 2 | str join ':' | str trim | str substring 0..170)
    }
  }
}

def main []: nothing -> nothing {
  let forbidden: table = (scan-one 'forbidden' 'while\s*\((true|1)\)|\bdo\s*\{|Thread\.sleep|runBlocking|GlobalScope|\bThread\(')
  let handrolled: table = (scan-one 'handrolled' 'synchronized\s*\(|@Volatile|AtomicLong|AtomicInteger|AtomicBoolean|AtomicReference|ReentrantLock|java\.util\.concurrent')
  let impure: table = (scan-one 'impure' 'System\.currentTimeMillis|System\.nanoTime|Instant\.now\(|LocalDateTime\.now|LocalDate\.now|java\.time\.Clock|Random\(|java\.io\.[A-Z]|FileWriter|FileInputStream|FileOutputStream')
  let errors: table = (scan-one 'errors' 'catch\s*\(\s*\w+\s*:\s*\w*Exception|!!|\bthrow\b')
  let all: table = ($forbidden | append $handrolled | append $impure | append $errors)

  print '# Concurrency/FP audit — U0 mechanical inventory for kap (generated 2026-11-08)'
  print ''
  print 'Scope: all library modules main sources (KMP: commonMain/jvmMain/native/js) + benchmarks; tests excluded.'
  print 'EVERY atomics hit here is a PRIMITIVE IMPLEMENTATION suspect (SyncCell wraps AtomicReference) — U1 classifies implementation-internal sync as a distinct verdict: LAWFUL-BY-DESIGN.'
  print ''
  for cat in ['forbidden' 'handrolled' 'impure' 'errors'] {
    let n: int = ($all | where category == $cat | length)
    print $'- **($cat)**: ($n)'
  }
  print ''
  for cat in ['forbidden' 'handrolled' 'impure' 'errors'] {
    print ($'## ($cat)')
    print ''
    let rows: table = ($all | where category == $cat)
    if ($rows | is-empty) {
      print '(none)'
    } else {
      print '| file | line | text |'
      print '|---|---|---|'
      for r in $rows {
        print $"| `($r.file)` | ($r.line) | `($r.text | str replace '|' '\\|')` |"
      }
    }
    print ''
  }
}
