#!/usr/bin/env bash
# Verifies that the numeric claims in README.md, LAWS.md and docs/ match the
# actual repository contents. Runs as a CI job — fails the build on drift.
#
# Usage: ./scripts/verify-claims.sh
set -euo pipefail
cd "$(dirname "$0")/.."

LIB_MODULES=(kap-core kap-arrow kap-resilience kap-ktor kap-kotest kap-ksp benchmarks)

test_count=$(grep -rc "@Test" --include="*.kt" "${LIB_MODULES[@]}" | awk -F: '{s+=$2} END {print s}')
suite_count=$(find "${LIB_MODULES[@]}" -name "*Test.kt" | wc -l)
bench_count=$(grep -rc "@Benchmark" --include="*.kt" benchmarks | awk -F: '{s+=$2} END {print s}')

echo "Reality: ${test_count} @Test · ${suite_count} suites · ${bench_count} @Benchmark"
echo ""

fail=0

expect_contains() { # file, substring, description
    local file="$1" substring="$2" desc="$3"
    if grep -qF -- "$substring" "$file"; then
        echo "OK   $desc ($file)"
    else
        echo "FAIL $desc — expected '$substring' in $file"
        fail=1
    fi
}

expect_not_contains() { # file, substring, description
    local file="$1" substring="$2" desc="$3"
    if grep -qF -- "$substring" "$file"; then
        echo "FAIL $desc — stale '$substring' still in $file"
        fail=1
    else
        echo "OK   $desc ($file)"
    fi
}

# ── Exact counts ────────────────────────────────────────────────────────────
expect_contains "LAWS.md" \
    "**${test_count} tests across ${suite_count} suites" \
    "LAWS.md test/suite count"

# README: exact benchmark count, floor for test count
expect_contains "README.md" \
    "(${bench_count} JMH benchmarks)" \
    "README benchmark count"
expect_contains "README.md" \
    "$(( (test_count / 100) * 100 ))+ tests" \
    "README test-count floor"

# Living docs must not carry stale benchmark counts
for f in docs/comparison.md docs/index.md; do
    expect_contains "$f" "**${bench_count} JMH benchmarks**" "benchmark count in $f"
done

# ── Stale-claim tripwires (any old number reappearing) ─────────────────────
expect_not_contains "README.md" "119 JMH" "README stale 119"
expect_not_contains "docs/comparison.md" "119 JMH" "comparison.md stale 119"
expect_not_contains "docs/index.md" "119 JMH" "index.md stale 119"

echo ""
if [ "$fail" -ne 0 ]; then
    echo "CLAIM DRIFT DETECTED — update the claims (or the code) and re-run."
    exit 1
fi
echo "All claims verified."
