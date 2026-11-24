# structural.nu — kap's scripts (audit-fp, bump-consumers-on-release,
# verify-claims): release and audit tooling, effects on git + gradle.
# Generic proof: parse + shebang resolves + no stale import.
# covers: /Users/lattenero/Projects/kap/scripts/*.nu
use std/assert
use /Users/lattenero/Scripts/lib/structural.nu *

let files: list<string> = (glob '/Users/lattenero/Projects/kap/scripts/*.nu')
assert greater or equal ($files | length) 2 "the glob must see the family"

let found: list<Finding> = (findings $files)
assert equal $found [] ($found | to json)

print $"kap scripts structural ok — ($files | length) scripts"
