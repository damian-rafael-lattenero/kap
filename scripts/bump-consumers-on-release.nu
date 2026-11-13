#!/Users/lattenero/.local/bin/nu-nightly
# bump-consumers-on-release.nu — the kap release tail, fully reactive.
#
# Waits for kap CI on master to go green, creates the GitHub release (the
# publish workflow's trigger), waits for that workflow to succeed, then bumps
# the consumer catalogs to the released version and pushes them.
#
# Reactivity law (~/Projects/nushell-kb/notes/reactive-jobs.md #1): the
# waiting lives in `gh run watch` running INSIDE a job — the thread blocks on
# the child, never spins — and the main thread sleeps in `job recv --timeout`,
# where the timeout IS the failure signal. There is no sleep anywhere.
#
# Usage:
#   nu scripts/bump-consumers-on-release.nu                 # 5.2.1 → 5.3.0
#   nu scripts/bump-consumers-on-release.nu --to 5.4.0 --from 5.3.0
#
# Every phase prints one honest line; a refused repo is named, never skipped
# silently. Re-running is safe: finished stages pass through.

const DEFAULT_FROM: string = "5.2.1"
const DEFAULT_TO: string = "5.3.0"
const DEFAULT_REPOS: string = "zcode-bot-kotlin,agent-brain,whatsdigest-kt"
const GH_BUDGET: duration = 2hr
const REPO_ROOT: string = "/Users/lattenero/Projects"

def log [phase: string, msg: string]: nothing -> nothing {
    print $"[(date now | format date '%H:%M:%S')] ($phase) :: ($msg)"
}

# Block until the gh watch inside the job resolves; the budget is the failure
# signal. Returns true when the watched run succeeded, false when it failed.
def watch-run [run_id: int, what: string]: nothing -> bool {
    let id: int = job spawn --description $what {
        let result: record = (gh run watch $run_id --exit-status | complete)
        {ok: ($result.exit_code == 0)} | job send 0
    }
    let msg: any = try { job recv --timeout $GH_BUDGET } catch {
        job kill $id
        error make {msg: $"($what): no verdict within ($GH_BUDGET) — treating as a failure"}
    }
    $msg.ok
}

# Latest CI run on master: returns {id, conclusion} (conclusion null while running).
def latest-ci-run []: nothing -> record {
    let rows: list<record> = (gh run list --workflow CI --branch master --limit 1 --json databaseId,conclusion | from json)
    {id: ($rows | first | get databaseId), conclusion: ($rows | first | get conclusion)}
}

def ensure-release [tag: string]: nothing -> nothing {
    let existing = (gh release view $tag --json tagName --jq '.tagName' | complete)
    if ($existing.exit_code == 0) {
        log 'release' $"already published — the publish workflow was triggered before"
        return
    }
    log 'release' $"creating ($tag) — this triggers the publish workflow"
    gh release create $tag --generate-notes --title $tag | complete | ignore
}

# Publish workflow run for the release tag (runs triggered by a release carry
# the tag as their headBranch).
def latest-publish-run [tag: string]: nothing -> record {
    let rows: list<record> = (gh run list --workflow publish.yml --limit 10 --json databaseId,conclusion,headBranch | from json)
    let rows: list<record> = ($rows | where headBranch == $tag)
    if ($rows | is-empty) {
        error make {msg: $"no publish workflow run for ($tag) — was the release actually published?"}
    }
    {id: ($rows | first | get databaseId), conclusion: ($rows | first | get conclusion)}
}

def bump-repo [repo: string, from_v: string, to_v: string]: nothing -> string {
    let dir = $"($REPO_ROOT)/($repo)"
    let cat = $"($dir)/gradle/libs.versions.toml"
    if not ($cat | path exists) {
        return $"($repo) :: REFUSED — no version catalog at ($cat)"
    }
    let dirty = (git -C $dir status --porcelain -- gradle/libs.versions.toml | complete | get stdout | str trim)
    if ($dirty | is-not-empty) {
        return $"($repo) :: REFUSED — the catalog has uncommitted changes"
    }
    let pin = (open --raw $cat | lines | where {|line| $line =~ '^kap = '})
    if ($pin | is-empty) {
        return $"($repo) :: REFUSED — no 'kap =' pin in the catalog"
    }
    if ($pin | first | str contains $"\"($to_v)\"") {
        return $"($repo) :: ALREADY at ($to_v)"
    }
    let branch = (git -C $dir branch --show-current | complete | get stdout | str trim)
    if $branch != 'master' {
        return $"($repo) :: REFUSED — on branch '($branch)', not master"
    }
    let pulled = (git -C $dir pull --ff-only origin master | complete)
    if $pulled.exit_code != 0 {
        return $"($repo) :: REFUSED — pull --ff-only failed: ($pulled.stderr | str trim)"
    }
    let pattern = $"^kap = \"($from_v)\""
    sed -i '' $pattern $"kap = \"($to_v)\"" $cat
    let after = (open --raw $cat | lines | where {|line| $line =~ '^kap = '} | first)
    if not ($after | str contains $to_v) {
        return $"($repo) :: FAILED — the pin did not land: ($after)"
    }
    git -C $dir add gradle/libs.versions.toml
    let committed = (git -C $dir commit -m $"build(deps): kap ($from_v) → ($to_v)" | complete)
    if $committed.exit_code != 0 {
        return $"($repo) :: FAILED — commit: ($committed.stderr | str trim)"
    }
    let pushed = (git -C $dir push | complete)
    if $pushed.exit_code != 0 {
        return $"($repo) :: FAILED — push: ($pushed.stderr | str trim)"
    }
    $"($repo) :: BUMPED ($from_v) → ($to_v) (committed + pushed)"
}

def main [
    --to: string = $DEFAULT_TO          # the released kap version
    --from: string = $DEFAULT_FROM      # the pin the consumers currently carry
    --repos: string = $DEFAULT_REPOS    # comma-separated consumer repos
]: nothing -> nothing {
    let repo_list: list<string> = ($repos | split row ',' | str trim)

    # ── Phase 1: kap CI on master ────────────────────────────────────────
    let ci = (latest-ci-run)
    if $ci.conclusion == 'success' {
        log 'ci' $"run ($ci.id) already green"
    } else {
        log 'ci' $"watching run ($ci.id) — conclusion: ($ci.conclusion | default 'running')"
        let ok = (watch-run $ci.id 'ci')
        if not $ok {
            error make {msg: $"CI run ($ci.id) FAILED — not tagging a red master"}
        }
        log 'ci' $"run ($ci.id) green"
    }

    # ── Phase 2: the GitHub release (the publish workflow's trigger) ─────
    let tag = $"v($to)"
    ensure-release $tag

    # ── Phase 3: the publish workflow ────────────────────────────────────
    let pub = (latest-publish-run $tag)
    if $pub.conclusion == 'success' {
        log 'publish' $"run ($pub.id) already green"
    } else {
        log 'publish' $"watching run ($pub.id) — conclusion: ($pub.conclusion | default 'running')"
        let ok = (watch-run $pub.id 'publish')
        if not $ok {
            error make {msg: $"publish run ($pub.id) FAILED — the consumers stay on ($from)"}
        }
        log 'publish' $"run ($pub.id) green — ($to) is uploading to Central"
    }

    # ── Phase 4: Central metadata — a report, not a gate. The publish
    #    step already validated the upload; repo1 sync can lag behind it.
    let meta = (curl -s $"https://repo1.maven.org/maven2/io/github/damian-rafael-lattenero/kap-core/maven-metadata.xml" | complete)
    let visible = ($meta.exit_code == 0 and ($meta.stdout | str contains $to))
    if $visible {
        log 'central' $"metadata already shows ($to)"
    } else {
        log 'central' $"metadata not showing ($to) yet — sync lag; the publish step validated the upload"
    }

    # ── Phase 5: the consumers ───────────────────────────────────────────
    let results: list<string> = ($repo_list | each {|repo| bump-repo $repo $from $to })
    $results | each {|line| log 'consumers' $line } | ignore

    let failed = ($results | where {|line| ($line | str contains 'REFUSED') or ($line | str contains 'FAILED') })
    if ($failed | is-not-empty) {
        error make {msg: $"($failed | length) consumer(s) did not bump — see the lines above"}
    }
    log 'done' $"all consumers on kap ($to)"
}
