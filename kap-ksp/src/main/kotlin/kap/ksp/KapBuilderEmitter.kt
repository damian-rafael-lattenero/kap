package kap.ksp

/**
 * Pure emission engine — turns a [BuilderSpec] into the FULL text of a
 * generated builder file. No KSP types involved: every function here is a
 * plain string builder, unit-testable without a compiler (`KapEmitterTest`).
 *
 * The processor (symbolic resolution) decides WHAT to generate — names,
 * entry-policy, clash handling — this engine decides only HOW it renders.
 */
internal data class ParamInfo(
    val name: String,
    val typeString: String,
    val isNullable: Boolean,
)

/** A type parameter of the annotated declaration, with rendered bounds. */
internal data class TypeParamInfo(val name: String, val bounds: String?)

/** Everything the emit functions need to know about one declaration. */
internal data class BuilderSpec(
    val baseName: String,
    val packageName: String = "",
    val params: List<ParamInfo>,
    val returnType: String,
    val typeParams: List<TypeParamInfo> = emptyList(),
    val prefix: String = "",
    val callable: String = "",
) {
    val fileBaseName: String get() = if (prefix.isEmpty()) baseName else "$prefix$baseName"

    /**
     * α-conversion: the validated builder's error binder is a GENERATED
     * binder, not user syntax — on collision with a user type parameter it
     * is silently renamed (`E` -> `E_`, `E__`, ...), never rejected.
     */
    fun errorBinder(): String {
        var name = "E"
        while (typeParams.any { it.name == name }) name += "_"
        return name
    }

    /** Same α-conversion for the generated `Rest` binder of non-last slots. */
    fun restBinder(): String {
        var name = "Rest"
        while (typeParams.any { it.name == name }) name += "_"
        return name
    }
}

internal fun List<TypeParamInfo>.names() = joinToString(", ") { it.name }

internal fun List<TypeParamInfo>.decl() =
    joinToString(", ") { if (it.bounds != null) "${it.name} : ${it.bounds}" else it.name }

internal fun List<TypeParamInfo>.inst() = if (isEmpty()) "" else "<${names()}>"

internal fun String.referencedParams(all: List<TypeParamInfo>): List<TypeParamInfo> =
    all.filter { Regex("\\b${Regex.escape(it.name)}\\b").containsMatchIn(this) }



/**
 * Façade over the two emission engines: [plainFile] renders the
 * `${T}KapBuilder.kt` source, [validatedFile] the `${T}KapBuilderValidated.kt`
 * source. Both are pure functions of a [BuilderSpec] — no KSP involved.
 */
internal object KapBuilderEmitter {

    /** Full text of `${spec.fileBaseName}KapBuilder.kt`. */
    fun plainFile(spec: BuilderSpec, entryFnName: String, kapExtensionProperty: Boolean): String =
        PlainEmitter().run {
            writeHeader(spec)
            writeOpaqueTypes(spec)
            // writeScopedBuilder emits the builder class + per-slot operators
            // + parens forms + andThen/evalGraph internally.
            writeScopedBuilder(spec)
            writeScopedEntry(spec, entryFnName)
            if (kapExtensionProperty) writeKapExtensionProperty(spec)
            text()
        }

    /** Full text of `${spec.fileBaseName}KapBuilderValidated.kt`. */
    fun validatedFile(spec: BuilderSpec, entryFnName: String): String =
        ValidatedEmitter().run {
            writeValidatedHeader(spec)
            writeValidatedFromOverloads(spec)
            // writeValidatedScopedBuilder emits the class + per-slot validated
            // operators + parens forms + evalGraph internally.
            writeValidatedScopedBuilder(spec)
            writeValidatedScopedEntry(spec, entryFnName)
            text()
        }
}

/** Emission of the plain (non-validated) builder file. */
private class PlainEmitter {
    private val sb = StringBuilder()
    private fun w(s: String) {
        sb.append(s)
    }

    fun text(): String = sb.toString()

    // ── Plain builder emission ─────────────────────────────────────────────

    /** `val ((P) -> R).kap` — enables `(::myFn).kap` and `kap((::myFn)::kap)` forms. */
    internal fun writeKapExtensionProperty(spec: BuilderSpec) {
        val wrapperName = "${spec.baseName}Kap"
        val opaqueNames = spec.params.map { p ->
            val refs = p.typeString.referencedParams(spec.typeParams)
            "${spec.baseName}${p.name.replaceFirstChar { c -> c.uppercase() }}${refs.inst()}"
        }
        val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> ${spec.returnType}"
        val inputType = "(${spec.params.joinToString(", ") { it.typeString }}) -> ${spec.returnType}"
        val entryRefs = (spec.params.map { it.typeString } + spec.returnType)
            .flatMap { it.referencedParams(spec.typeParams) }
            .distinctBy { it.name }
        val tpDecl = if (entryRefs.isEmpty()) "" else "<${entryRefs.decl()}> "
        val opaqueParamNames = spec.params.indices.map { "p$it" }
        val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }

        w("\n/** Extension property — enables `(::myFn).kap` and `kap((::myFn)::kap)` forms. */\n")
        w("val $tpDecl($inputType).kap: $wrapperName<$curriedType>\n")
        w("    get() = $wrapperName(Kap.of(")
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            w("{ $name: $opaque -> ")
        }
        w("this($opaqueCallArgs)")
        w(" }".repeat(spec.params.size))
        w("))\n")
    }

    // ── Shared generation helpers ──────────────────────────────────

    internal fun writeHeader(spec: BuilderSpec) {
        val hasPackage = spec.packageName.isNotEmpty()
        val packageName = spec.packageName
        w("// AUTO-GENERATED by kap-ksp — do not edit\n")
        if (hasPackage) {
            w("package $packageName\n\n")
        }
        w("import kap.Kap\n")
        w("import kap.KapLike\n")
        w("import kap.of\n")
        w("import kap.with\n")
        w("import kap.then\n")
        w("import kap.thenValue\n")
        w("import kap.map\n")
        w("import kap.andThen\n")
        w("import kap.evalGraph\n")
        w("\n")
    }

    internal fun writeOpaqueTypes(
        spec: BuilderSpec,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val typeParams = spec.typeParams
        // Wrapper data classes — one per field, named uniquely by class+field.
        // Fields referencing declaration type parameters get their own copy
        // (`data class CheckoutTotal<T>(val value: T)`), so inference flows
        // from the `from` value through the whole chain.
        w("// ── Opaque wrappers — one per field ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val refs = param.typeString.referencedParams(typeParams)
            val tpDecl = if (refs.isEmpty()) "" else "<${refs.decl()}>"
            w("data class $wrapperName$tpDecl(val value: ${param.typeString})\n\n")
        }

        // Tag classes — one per field, top-level. Unique-named (class+field+Tag)
        // so no collisions across @KapTypeSafe data classes. Receivers for the
        // infix `from` extension functions below.
        w("// ── Tag classes (receivers for infix `from`) ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            w("class $tagClassName internal constructor()\n")
        }
        w("\n")

        // Infix `from` — two overloads per field (raw value + `Kap<T>` so
        // combinators like `Kap { ... }.timeout(...)` compose without
        // leaving the graph). Top-level — receivers are unique per class.
        w("// ── Infix `from` — wraps raw value or Kap<T> into the tagged wrapper ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            val refs = param.typeString.referencedParams(typeParams)
            val tpDecl = if (refs.isEmpty()) "" else "<${refs.names()}> "
            val inst = refs.inst()
            w(
                "infix fun $tpDecl$tagClassName.from(value: ${param.typeString}): " +
                    "$wrapperName$inst = $wrapperName(value)\n",
            )
            w(
                "infix fun $tpDecl$tagClassName.from(kap: Kap<${param.typeString}>): " +
                    "Kap<$wrapperName$inst> = kap.map(::$wrapperName)\n\n",
            )
        }

    }

    /**
     * Emits the scoped builder class + operators that make `kap(::T).with { field from value }`
     * IDE-friendly:
     *
     * 1. `class ${baseName}Kap<F>(internal val _kap: Kap<F>)` — holds the underlying
     *    Kap and owns the tag vals as members.
     * 2. Extensions `with` / `then` (raw-value and `Kap<A>` overloads), `andThen`,
     *    `evalGraph` — preserve the wrapper through chains so the lambda receiver
     *    always exposes the tag vals.
     *
     * The wrapper IS the IDE-completion source. No `import` or `with(...)` block
     * is needed at call sites.
     */
    internal fun writeScopedBuilder(
        spec: BuilderSpec,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}Kap"

        // ── Per-slot tag interfaces — each exposes ONLY the tag for its slot. ──
        // The slot-specific `.with`/`.then` overloads below use these as the
        // lambda receiver, so when the cursor is in `.with { ___ }` the IDE
        // sees exactly one member (`fieldName`) and suggests it directly. Type
        // any other field → compile error naming the expected tag.
        w("// ── Per-slot interfaces (lambda receivers for `.with` / `.then`) ──\n\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            w("interface $baseName${cap}Slot { val ${param.name}: $baseName${cap}Tag }\n")
        }
        w("\n")

        w("/** Scoped builder for @KapTypeSafe $baseName. Implements every slot interface\n")
        w(" *  so each field is reachable as a member. The per-slot `.with` overloads\n")
        w(" *  below narrow the lambda receiver to a single tag — the IDE shows only the\n")
        w(" *  field expected at the current curry position when the body is empty.\n")
        w(" *\n")
        w(" *  The wrapper deliberately does NOT delegate to `Kap<F>`. If it did, the\n")
        w(" *  imported `Kap.with(suspend () -> A)` would compete with the slot-specific\n")
        w(" *  `.with { field from … }` and K2's overload resolution sometimes picks the\n")
        w(" *  generic one (before typechecking the lambda body), causing the slot's tag\n")
        w(" *  reference to fail with `Unresolved reference`.\n")
        w(" *\n")
        w(" *  The wrapper implements `KapLike<F>`, so kap-core operators (.map /\n")
        w(" *  .recover / .timeout / .settled / .memoize / .timed / .andThen /\n")
        w(" *  .evalGraph) are available directly on partial wrappers as well. For raw\n")
        w(" *  `Kap<F>` (e.g. an external API parameter), use `.asKap`.\n")
        w(" */\n")
        val slotImpls = params.joinToString(", ") { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}Slot" }
        w("class $wrapperName<F>(@PublishedApi internal val _kap: Kap<F>) : KapLike<F>, $slotImpls {\n")
        w("    override val asKap: Kap<F> get() = _kap\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            w("    override val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        // Companion mirrors the tag vals so they're reachable from outside the
        // lambda receiver — e.g. `.with($wrapperName.field from Kap { ... })`.
        w("\n    companion object {\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            w("        val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        w("    }\n")
        w("}\n\n")

        // ── Per-slot `.with` and `.then` — narrowed lambda receiver per slot ──
        // Each overload only matches when F begins with that slot's wrapper type.
        // When the user writes `kap(::T).with { _ }`, only ONE overload applies
        // (the one for the head wrapper), and its lambda receiver is the slot
        // interface exposing the single relevant tag.
        //
        // Last-slot optimization: when F = (LastWrapper) -> ReturnType, the overload
        // returns `Kap<ReturnType>` directly — no `.asKap` needed to chain into
        // `andThen { kap(::X)... }` or to apply kap-core operators on the result.
        writePerSlotOperators(spec)

        writeParensOperators(spec)
        w("suspend fun <A> $wrapperName<A>.evalGraph(): A = _kap.evalGraph()\n\n")
    }

    /** Per-slot `.with`/`.then`/`.thenValue` — one triple per parameter. */
    /**
     * The per-slot operator family. One combinator shape, three evaluators:
     *  - `with`      = parallel apply (`withPair` semantics)
     *  - `then`      = sequential apply with phase barrier
     *  - `thenValue` = sequential apply without barrier (overlap allowed)
     *
     * `emitSlotOperator` emits one; the receiver/return shape is identical
     * except for the evaluator it delegates to.
     */
    private fun writePerSlotOperators(spec: BuilderSpec) {
        w("// ── Per-slot operators — IDE shows exactly the field expected at this position ──\n\n")
        for ((index, param) in spec.params.withIndex()) {
            val isLast = index == spec.params.size - 1
            for (op in listOf("with", "then", "thenValue")) {
                emitSlotOperator(spec, param, isLast, op)
            }
        }
    }

    private fun emitSlotOperator(
        spec: BuilderSpec,
        param: ParamInfo,
        isLast: Boolean,
        op: String,
    ) {
        val baseName = spec.baseName
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}Kap"
        val cap = param.name.replaceFirstChar { it.uppercase() }
        // Generalization rule (free-variable analysis): a generated declaration
        // quantifies exactly the type variables free in the positions it spans —
        // its own slot, plus the return type for the last slot, plus the
        // internal `Rest` binder for non-last slots.
        val slotRefs = param.typeString.referencedParams(typeParams)
        val binderRefs = if (isLast) {
            (slotRefs + returnType.referencedParams(typeParams)).distinctBy { it.name }
        } else {
            slotRefs
        }
        val wrapperType = "$baseName$cap${slotRefs.inst()}"
        val slotType = "${baseName}${cap}Slot"
        val rest = spec.restBinder()
        val tp = when {
            isLast && binderRefs.isEmpty() -> ""
            isLast -> "<${binderRefs.names()}> "
            else -> if (slotRefs.isEmpty()) "<$rest> " else "<${slotRefs.names()}, $rest> "
        }

        w("@kotlin.jvm.JvmName(\"${op}_${param.name}\")\n")
        if (isLast) {
            // Last slot: curry is fully applied → return Kap<ReturnType> directly.
            w("inline infix fun $tp$wrapperName<($wrapperType) -> $returnType>.$op(\n")
            w("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
            w("): Kap<$returnType> {\n")
            w("    val self = this\n")
            w("    return self._kap.$op(suspend { self.fa() })\n")
        } else {
            // Non-last slot: returns wrapper so the chain continues.
            w("inline infix fun $tp$wrapperName<($wrapperType) -> $rest>.$op(\n")
            w("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
            w("): $wrapperName<$rest> {\n")
            w("    val self = this\n")
            w("    return $wrapperName(self._kap.$op(suspend { self.fa() }))\n")
        }
        w("}\n\n")
    }

    /** Parens (Kap-argument) forms + `andThen`/`evalGraph`. */
    private fun writeParensOperators(spec: BuilderSpec) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}Kap"
        // ── Generic Kap<A> overloads (parens form) — for non-last slots. ──
        // Used when the value is already a Kap<A> built outside the lambda.
        // The last-slot specific overloads below take precedence when the
        // wrapper is at the final curry position.
        w("infix fun <A, B> $wrapperName<(A) -> B>.with(fa: Kap<A>): $wrapperName<B> =\n")
        w("    $wrapperName(_kap.with(fa))\n\n")

        w("infix fun <A, B> $wrapperName<(A) -> B>.then(fa: Kap<A>): $wrapperName<B> =\n")
        w("    $wrapperName(_kap.then(fa))\n\n")

        w("infix fun <A, B> $wrapperName<(A) -> B>.thenValue(fa: Kap<A>): $wrapperName<B> =\n")
        w("    $wrapperName(_kap.thenValue(fa))\n\n")

        // ── Last-slot parens form — more specific, returns Kap<ReturnType>. ──
        if (params.isNotEmpty()) {
            val lastCap = params.last().name.replaceFirstChar { it.uppercase() }
            val lastRefs = (params.last().typeString.referencedParams(typeParams) +
                returnType.referencedParams(typeParams)).distinctBy { it.name }
            val lastWrapperType = "$baseName$lastCap${params.last().typeString.referencedParams(typeParams).inst()}"
            val lastTp = if (lastRefs.isEmpty()) "" else "<${lastRefs.names()}> "
            val lastReceiver = "$wrapperName<($lastWrapperType) -> $returnType>"
            val lastKapType = "Kap<$lastWrapperType>"
            val lastReturn = "Kap<$returnType>"

            w("infix fun $lastTp$lastReceiver.with(fa: $lastKapType): $lastReturn =\n")
            w("    _kap.with(fa)\n\n")

            w("infix fun $lastTp$lastReceiver.then(fa: $lastKapType): $lastReturn =\n")
            w("    _kap.then(fa)\n\n")

            w("infix fun $lastTp$lastReceiver.thenValue(fa: $lastKapType): $lastReturn =\n")
            w("    _kap.thenValue(fa)\n\n")
        }

        w("inline infix fun <A, B> $wrapperName<A>.andThen(\n")
        w("    crossinline f: (A) -> Kap<B>,\n")
        w("): Kap<B> = _kap.andThen(f)\n\n")

        // `.asKap` is now a member of the class (via KapLike<F>), exposed here as
        // a reminder that it is the escape hatch to raw Kap<F> for external APIs.
    }

    /**
     * Emits a `kap(...)` entry point that returns the scoped `${baseName}Kap<curried>`.
     * `paramKind` controls whether the input is a function reference (`f: (P) -> R`)
     * or a marker object (`marker: M`) and the body that invokes it.
     */
    internal fun writeScopedEntry(
        spec: BuilderSpec,
        entryFnName: String,

    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}Kap"
        // Opaque names carry their referenced type parameters so the curried
        // spine stays generic: `(CheckoutUser) -> ... -> (CheckoutTotal<T>) -> Checkout2<T>`.
        val opaqueNames = params.map { p ->
            val refs = p.typeString.referencedParams(typeParams)
            "$baseName${p.name.replaceFirstChar { c -> c.uppercase() }}${refs.inst()}"
        }
        val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
        val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"
        val entryRefs = (params.map { it.typeString } + returnType)
            .flatMap { it.referencedParams(typeParams) }
            .distinctBy { it.name }
        val tpDecl = if (entryRefs.isEmpty()) "" else "<${entryRefs.decl()}> "

        fun emitEntry(name: String, takesCallable: Boolean) {
            val argList = if (takesCallable) "(f: $inputType)" else "()"
            val callee = if (takesCallable) "f" else spec.callable
            w("fun $tpDecl$name$argList: $wrapperName<$curriedType> =\n")
            w("    $wrapperName(Kap.of(")
            val opaqueParamNames = params.indices.map { "p$it" }
            opaqueParamNames.zip(opaqueNames).forEach { (pn, opaque) ->
                w("{ $pn: $opaque -> ")
            }
            val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "${it}.value" }
            w("$callee($opaqueCallArgs)")
            w(" }".repeat(params.size))
            w("))\n\n")
        }

        w("\n/** Official entry — `kap(::C)` plain, `kap<Double>(::C)` for generics. */\n")
        emitEntry(entryFnName, takesCallable = true)
        if (typeParams.isNotEmpty()) {
            // Generic fallback: pins the type variables with zero arguments —
            // guaranteed-unambiguous name for declarations whose `kap(f)` shape
            // collides with another declaration's.
            w("/** Generic zero-arg alternative — `kap$baseName<Double>()`. */\n")
            emitEntry("kap$baseName", takesCallable = false)
        }
    }
}

/** Emission of the validated builder file (kapV family). */
private class ValidatedEmitter {
    private val sb = StringBuilder()
    private fun w(s: String) {
        sb.append(s)
    }

    fun text(): String = sb.toString()

    internal fun writeValidatedHeader(spec: BuilderSpec) {
        val hasPackage = spec.packageName.isNotEmpty()
        val packageName = spec.packageName
        w("// AUTO-GENERATED by kap-ksp — do not edit\n")
        if (hasPackage) {
            w("package $packageName\n\n")
        }
        w("import arrow.core.Either\n")
        w("import arrow.core.NonEmptyList\n")
        w("import kap.Kap\n")
        w("import kap.KapLike\n")
        w("import kap.of\n")
        w("import kap.withV\n")
        w("import kap.thenV\n")
        w("import kap.thenValueV\n")
        w("import kap.evalGraph\n")
        w("\n")
    }

    internal fun writeValidatedFromOverloads(
        spec: BuilderSpec,
    ) {
        val e = spec.errorBinder()
        w("// ── Validated infix `from` — maps Either<Nel<$e>, FieldType> into tagged wrapper ──\n\n")
        for (param in spec.params) {
            val wrapperName = "${spec.baseName}${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            val refs = param.typeString.referencedParams(spec.typeParams)
            val tpDecl = if (refs.isEmpty()) "<$e>" else "<$e, ${refs.names()}>"
            val inst = refs.inst()
            w(
                "infix fun $tpDecl $tagClassName.from(value: Either<NonEmptyList<$e>, ${param.typeString}>): " +
                    "Either<NonEmptyList<$e>, $wrapperName$inst> =\n",
            )
            w("    value.map(::$wrapperName)\n\n")
        }
    }

    internal fun writeValidatedScopedBuilder(
        spec: BuilderSpec,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"

        w("/** Validated scoped builder for @KapTypeSafe $baseName. Uses the same per-slot\n")
        w(" *  tag interfaces as ${baseName}Kap — each `.withV { field from validateField() }`\n")
        w(" *  narrows the receiver to one slot, accumulating errors via Arrow's applicative.\n")
        w(" */\n")
        val slotImpls = params.joinToString(", ") { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}Slot" }
        val e = spec.errorBinder()
        w(
            "class $wrapperName<$e, F>(@PublishedApi internal val _kap: Kap<Either<NonEmptyList<$e>, F>>) : " +
                "KapLike<Either<NonEmptyList<$e>, F>>, $slotImpls {\n",
        )
        w("    override val asKap: Kap<Either<NonEmptyList<$e>, F>> get() = _kap\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            w("    override val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        w("\n    companion object {\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            w("        val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        w("    }\n")
        w("}\n\n")

        writeValidatedPerSlotOperators(spec)

        writeValidatedParensOperators(spec)
        w(
            "suspend fun <$e, A> $wrapperName<$e, A>.evalGraph(): " +
                "Either<NonEmptyList<$e>, A> = _kap.evalGraph()\n\n",
        )
    }

    /**
     * Validated per-slot operator family — same combinator shape as the plain
     * family, but over `F<A> = Kap<Either<NonEmptyList<E>, A>>` and delegating
     * to the `V` evaluators. The error binder `E` is α-renamed on collision
     * with a user type parameter (generated binders are never user syntax).
     */
    private fun writeValidatedPerSlotOperators(spec: BuilderSpec) {
        w("// ── Per-slot .withV / .thenV / .thenValueV operators ──\n\n")
        for ((index, param) in spec.params.withIndex()) {
            val isLast = index == spec.params.size - 1
            for (op in listOf("withV", "thenV", "thenValueV")) {
                emitValidatedSlotOperator(spec, param, isLast, op)
            }
        }
    }

    private fun emitValidatedSlotOperator(
        spec: BuilderSpec,
        param: ParamInfo,
        isLast: Boolean,
        op: String,
    ) {
        val baseName = spec.baseName
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"
        val e = spec.errorBinder()
        val either = "Either<NonEmptyList<$e>, "
        val cap = param.name.replaceFirstChar { it.uppercase() }
        val slotRefs = param.typeString.referencedParams(typeParams)
        val binderRefs = if (isLast) {
            (slotRefs + returnType.referencedParams(typeParams)).distinctBy { it.name }
        } else {
            slotRefs
        }
        val wrapperType = "$baseName$cap${slotRefs.inst()}"
        val slotType = "${baseName}${cap}Slot"
        val rest = spec.restBinder()
        val tp = when {
            isLast && binderRefs.isEmpty() -> "<$e> "
            isLast -> "<$e, ${binderRefs.names()}> "
            else -> if (slotRefs.isEmpty()) "<$e, $rest> " else "<$e, ${slotRefs.names()}, $rest> "
        }

        w("@kotlin.jvm.JvmName(\"${op}_${param.name}\")\n")
        if (isLast) {
            w("inline infix fun $tp$wrapperName<$e, ($wrapperType) -> $returnType>.$op(\n")
            w("    crossinline fa: suspend $slotType.() -> $either$wrapperType>,\n")
            w("): Kap<$either$returnType>> {\n")
            w("    val self = this\n")
            w("    return self._kap.$op(Kap { self.fa() })\n")
        } else {
            w("inline infix fun $tp$wrapperName<$e, ($wrapperType) -> $rest>.$op(\n")
            w("    crossinline fa: suspend $slotType.() -> $either$wrapperType>,\n")
            w("): $wrapperName<$e, $rest> {\n")
            w("    val self = this\n")
            w("    return $wrapperName(self._kap.$op(Kap { self.fa() }))\n")
        }
        w("}\n\n")
    }

    /** Parens (Kap-argument) validated forms + `evalGraph`. */
    private fun writeValidatedParensOperators(spec: BuilderSpec) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"
        val e = spec.errorBinder()

        // Generic Kap<Either<Nel<E>, A>> overloads (parens form)
        val validatedKapType = "Kap<Either<NonEmptyList<$e>, A>>"
        val genericValidatedReceiver = "$wrapperName<$e, (A) -> B>"
        w(
            "infix fun <$e, A, B> $genericValidatedReceiver.withV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        w("    $wrapperName(_kap.withV(fa))\n\n")

        w(
            "infix fun <$e, A, B> $genericValidatedReceiver.thenV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        w("    $wrapperName(_kap.thenV(fa))\n\n")

        w(
            "infix fun <$e, A, B> $genericValidatedReceiver.thenValueV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        w("    $wrapperName(_kap.thenValueV(fa))\n\n")

        // Last-slot parens form
        if (params.isNotEmpty()) {
            val lastCap = params.last().name.replaceFirstChar { it.uppercase() }
            val lastSlotRefs = params.last().typeString.referencedParams(typeParams)
            val lastAllRefs = (lastSlotRefs +
                returnType.referencedParams(typeParams)).distinctBy { it.name }
            val lastWrapperType = "$baseName$lastCap${lastSlotRefs.inst()}"
            val lastTp = if (lastAllRefs.isEmpty()) "<$e> " else "<$e, ${lastAllRefs.names()}> "
            val lastValidatedReceiver = "$wrapperName<$e, ($lastWrapperType) -> $returnType>"
            val lastValidatedKapType = "Kap<Either<NonEmptyList<$e>, $lastWrapperType>>"
            val lastValidatedReturn = "Kap<Either<NonEmptyList<$e>, $returnType>>"

            for (op in listOf("withV", "thenV", "thenValueV")) {
                w(
                    "infix fun $lastTp$lastValidatedReceiver.$op(fa: $lastValidatedKapType): " +
                        "$lastValidatedReturn =\n",
                )
                w("    _kap.$op(fa)\n\n")
            }
        }

    }

    internal fun writeValidatedScopedEntry(
        spec: BuilderSpec,
        entryFnName: String,

    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"
        val e = spec.errorBinder()
        val opaqueNames = params.map { p ->
            val refs = p.typeString.referencedParams(typeParams)
            "$baseName${p.name.replaceFirstChar { c -> c.uppercase() }}${refs.inst()}"
        }
        val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
        val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"
        val entryRefs = (params.map { it.typeString } + returnType)
            .flatMap { it.referencedParams(typeParams) }
            .distinctBy { it.name }
        val tpDecl = if (entryRefs.isEmpty()) "<$e> " else "<$e, ${entryRefs.decl()}> "

        fun emitValidatedEntry(name: String, takesCallable: Boolean) {
            val argList = if (takesCallable) "(f: $inputType)" else "()"
            val callee = if (takesCallable) "f" else spec.callable
            w("fun $tpDecl$name$argList: $wrapperName<$e, $curriedType> {\n")
            w("    val fn: $curriedType = ")
            val opaqueParamNames = params.indices.map { "p$it" }
            opaqueParamNames.zip(opaqueNames).forEach { (pn, opaque) ->
                w("{ $pn: $opaque -> ")
            }
            val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "${it}.value" }
            w("$callee($opaqueCallArgs)")
            w(" }".repeat(params.size))
            w("\n")
            w(
                "    val kap: Kap<Either<NonEmptyList<$e>, $curriedType>> = Kap.of(Either.Right(fn))\n",
            )
            w("    return $wrapperName(kap)\n")
            w("}\n\n")
        }

        w("\n/** Validated entry — `kapV(::C)` / `kapV<Double>(::C)` for generics. */\n")
        emitValidatedEntry(entryFnName, takesCallable = true)
        if (typeParams.isNotEmpty()) {
            w("/** Generic zero-arg alternative — `kapV$baseName<Double>()`. */\n")
            emitValidatedEntry("kapV$baseName", takesCallable = false)
        }
    }
}
