#!/Users/lattenero/.local/bin/nu-nightly
# Verifies that the numeric claims in README.md, LAWS.md and docs/ match the
# actual repository contents. Runs as a CI job — fails the build on drift.
#
# Usage: ./scripts/verify-claims.nu
#
# Migrated from verify-claims.sh on 2026-11-02. Counts are pure pipelines;
# the expectation helpers return bool so the caller threads `failed`.

const LIB_MODULES: list<string> = [
    "kap-core"
    "kap-arrow"
    "kap-resilience"
    "kap-ktor"
    "kap-kotest"
    "kap-ksp"
    "benchmarks"
]

def count-lines-with [files: list<string>, needle: string]: nothing -> int {
    if ($files | is-empty) { return 0 }
    ($files
        | each {|f| open --raw $f | lines | where {|l| $l | str contains $needle } | length }
        | math sum)
}

def expect-contains [file: string, substring: string, desc: string]: nothing -> bool {
    if not (($file | path exists)) {
        print $"FAIL ($desc) — missing ($file)"
        return false
    }
    if (open --raw $file | str contains $substring) {
        print $"OK   ($desc) \(($file)\)"
        true
    } else {
        print $"FAIL ($desc) — expected '($substring)' in ($file)"
        false
    }
}

def expect-not-contains [file: string, substring: string, desc: string]: nothing -> bool {
    if not (($file | path exists)) {
        print $"OK   ($desc) \(($file) absent\)"
        return true
    }
    if (open --raw $file | str contains $substring) {
        print $"FAIL ($desc) — stale '($substring)' still in ($file)"
        false
    } else {
        print $"OK   ($desc) \(($file)\)"
        true
    }
}

def main []: nothing -> nothing {
    let repo = ($env.FILE_PWD | path dirname)
    cd $repo

    let kotlin_files = ($LIB_MODULES | each {|m| glob $"($m)/**/*.kt" } | flatten)
    let test_files = ($LIB_MODULES | each {|m| glob $"($m)/**/*Test.kt" } | flatten)
    let bench_files = (glob "benchmarks/**/*.kt")

    let test_count = (count-lines-with $kotlin_files "@Test")
    let suite_count = ($test_files | length)
    let bench_count = (count-lines-with $bench_files "@Benchmark")
    let floor = (((($test_count / 100) | math floor) | into int) * 100)

    print $"Reality: ($test_count) @Test · ($suite_count) suites · ($bench_count) @Benchmark"
    print ""

    mut failed = false

    # ── Exact counts ────────────────────────────────────────────────────
    if not (expect-contains "LAWS.md" $"**($test_count) tests across ($suite_count) suites" "LAWS.md test/suite count") {
        $failed = true
    }

    # README: exact benchmark count, floor for test count
    if not (expect-contains "README.md" $"($bench_count) JMH benchmarks" "README benchmark count") {
        $failed = true
    }
    if not (expect-contains "README.md" $"($floor)+ tests" "README test-count floor") {
        $failed = true
    }

    # Living docs must not carry stale benchmark counts
    for file in ["docs/comparison.md" "docs/index.md"] {
        if not (expect-contains $file $"**($bench_count) JMH benchmarks**" $"benchmark count in ($file)") {
            $failed = true
        }
    }

    # ── Stale-claim tripwires (any old number reappearing) ──────────────
    for file in ["README.md" "docs/comparison.md" "docs/index.md"] {
        if not (expect-not-contains $file "119 JMH" $"($file) stale 119") {
            $failed = true
        }
    }

    print ""
    if $failed {
        print "CLAIM DRIFT DETECTED — update the claims \(or the code\) and re-run."
        exit 1
    }
    print "All claims verified."
}
