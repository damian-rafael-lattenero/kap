@file:KapBridge(FixtBridged::class)

import kap.KapBridge
import kap.KapTypeSafe

// ── Golden-test fixtures — processed by kap-ksp itself (kspTest) ──────────
// The generated output is diffed against src/test/resources/golden/ by
// GoldenTest.kt. Every fixture here pins one code path of the processor.

/** Plain class bridged via the file-level annotation (third-party simulation). */
class FixtBridged(val label: String, val amount: Int)

@KapTypeSafe
data class FixtSimple(val only: String)

@KapTypeSafe
data class FixtWithNullable(val id: Long, val name: String?, val count: Int?)

// NOTE: type-parameterized classes (data class Foo<T>) are NOT yet supported
// by the processor — it emits unresolvable `T` references in top-level
// generated declarations. This fixture uses parameterized FIELD types, which
// are supported. Generic-class support is a known gap (golden-test finding).

@KapTypeSafe
data class FixtWithGeneric(val items: List<String>, val counts: Map<String, Int>, val count: Int)

@KapTypeSafe
data class FixtAllSameType(val a: String, val b: String, val c: String)

@KapTypeSafe
data class FixtFive(
    val user: String,
    val cart: String,
    val stock: Boolean,
    val shipping: Double,
    val tax: Double,
)

/** Unique function signature — gets the clean `kap(f: ...)` entry point. */
@KapTypeSafe
fun fixtUnique(a: Int, b: Boolean): String = "$a$b"

/** Collides with fixtBuildRight — both fall back to `kap{FunctionName}` entries. */
@KapTypeSafe
fun fixtBuildLeft(value: String): String = "l:$value"

/** Collides with fixtBuildLeft — both fall back to `kap{FunctionName}` entries. */
@KapTypeSafe
fun fixtBuildRight(value: String): String = "r:$value"

/** Prefix disambiguates generated file/tag names for shared parameter names. */
@KapTypeSafe(prefix = "Pfx")
fun fixtPfxOne(a: String): String = "one:$a"

/** Same parameter name as fixtPfxOne, different return type — prefix keeps types unique. */
@KapTypeSafe(prefix = "Pfx")
fun fixtPfxTwo(a: String): Int = a.length
