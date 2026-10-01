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

@KapTypeSafe
data class FixtWithGeneric(val items: List<String>, val counts: Map<String, Int>, val count: Int)

// ── Generic declarations — T/A/B are inferred from the `from` value at the
// call site (kap(::FixtGeneric).with { label from "x" }.then { amount from 2.0 })

@KapTypeSafe
data class FixtGeneric<T>(val label: String, val amount: T)

@KapTypeSafe
data class FixtMultiGeneric<A, B>(val first: A, val second: B, val note: String)

/** Generic function — same treatment: `kapFixtGenericFn<Double>().with { seed from 3.5 }...` */
@KapTypeSafe
fun <T> fixtGenericFn(seed: T, n: Int): kotlin.Pair<T, Int> = seed to n

// ── Collision fixtures — generated binders (E, Rest) are α-converted on
// collision with user type parameters instead of rejecting the declaration.

@KapTypeSafe
data class FixtParamE<E>(val payload: E, val note: String)

@KapTypeSafe
data class FixtParamRest<Rest>(val body: Rest, val count: Int)

// ── Full-signature collision between generics: identical canonical
// `(#0) -> FixtGenWrap<#0>` — both fall back to zero-arg class-named entries.

@KapTypeSafe
data class FixtGenWrap<T>(val inner: T)

@KapTypeSafe
fun <T> fixtGenWrapFn(inner: T): FixtGenWrap<T> = FixtGenWrap(inner)

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
