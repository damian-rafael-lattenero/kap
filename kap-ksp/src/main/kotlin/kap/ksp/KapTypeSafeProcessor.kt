package kap.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

class KapTypeSafeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /**
     * Tracks `(input-param-types, return-type)` signatures across all @KapTypeSafe
     * declarations in this round. The new typed entry `fun kap(f: (...) -> R)` is
     * emitted *only* for functions whose signature is unique — otherwise multiple
     * top-level `kap` overloads with identical signatures collide. Functions with
     * non-unique signatures fall back to the `kap{FunctionName}(f: ...)` form.
     */
    private val signatureCounts = mutableMapOf<String, Int>()

    private fun signatureKey(params: List<String>, returnType: String): String =
        "(${params.joinToString(",")})->$returnType"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val unprocessed = mutableListOf<KSAnnotated>()
        signatureCounts.clear()

        val kapArrowPresent = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("arrow.core.Either")
        ) != null

        // Pre-pass: count signatures across classes + functions + bridges so the
        // function generator can decide whether `kap(f: ...)` would collide.
        resolver.getSymbolsWithAnnotation("kap.KapTypeSafe").forEach { symbol ->
            if (!symbol.validate()) return@forEach
            recordSignature(symbol)
        }
        resolver.getSymbolsWithAnnotation("kap.KapBridge").forEach { symbol ->
            if (!symbol.validate()) return@forEach
            if (symbol is KSFile) recordBridgeSignatures(symbol)
        }

        // Process @KapTypeSafe
        resolver.getSymbolsWithAnnotation("kap.KapTypeSafe").forEach { symbol ->
            if (!symbol.validate()) {
                unprocessed.add(symbol)
                return@forEach
            }
            when (symbol) {
                is KSClassDeclaration -> {
                    if (symbol.classKind == ClassKind.CLASS) {
                        generateForClass(symbol, kapArrowPresent)
                    } else {
                        logger.error("@KapTypeSafe can only be applied to classes or functions", symbol)
                    }
                }
                is KSFunctionDeclaration -> generateForFunction(symbol, kapArrowPresent)
                else -> logger.error("@KapTypeSafe can only be applied to classes or functions", symbol)
            }
        }

        // Process @KapBridge
        resolver.getSymbolsWithAnnotation("kap.KapBridge").forEach { symbol ->
            if (!symbol.validate()) {
                unprocessed.add(symbol)
                return@forEach
            }
            when (symbol) {
                is KSFile -> processBridgeAnnotations(symbol, resolver)
                else -> logger.error("@KapBridge can only be applied at file level", symbol)
            }
        }

        return unprocessed
    }

    private fun recordSignature(symbol: KSAnnotated) {
        when (symbol) {
            is KSClassDeclaration -> {
                if (symbol.classKind != ClassKind.CLASS) return
                val ctor = symbol.primaryConstructor ?: return
                val paramTypes = ctor.parameters.map { renderType(it.type.resolve()) }
                val returnType = renderType(symbol.asStarProjectedType())
                signatureCounts.merge(signatureKey(paramTypes, returnType), 1, Int::plus)
            }
            is KSFunctionDeclaration -> {
                val paramTypes = symbol.parameters.map { renderType(it.type.resolve()) }
                val returnType = symbol.returnType?.resolve()?.let { renderType(it) } ?: "kotlin.Unit"
                signatureCounts.merge(signatureKey(paramTypes, returnType), 1, Int::plus)
            }
        }
    }

    private fun recordBridgeSignatures(file: KSFile) {
        file.annotations.filter { it.shortName.asString() == "KapBridge" }.forEach { annotation ->
            val targetArg = annotation.arguments.firstOrNull { it.name?.asString() == "target" }
            val targetType = targetArg?.value as? KSType ?: return@forEach
            val classDecl = targetType.declaration as? KSClassDeclaration ?: return@forEach
            val ctor = classDecl.primaryConstructor ?: return@forEach
            val paramTypes = ctor.parameters.map { renderType(it.type.resolve()) }
            val returnType = renderType(classDecl.asStarProjectedType())
            signatureCounts.merge(signatureKey(paramTypes, returnType), 1, Int::plus)
        }
    }

    // ── @KapBridge processing ──────────────────────────────────────

    private fun processBridgeAnnotations(file: KSFile, resolver: Resolver) {
        file.annotations
            .filter { it.shortName.asString() == "KapBridge" }
            .forEach { annotation ->
                val targetArg = annotation.arguments.firstOrNull { it.name?.asString() == "target" }
                val targetType = targetArg?.value as? KSType ?: run {
                    logger.error("@KapBridge requires a target class", annotation)
                    return@forEach
                }
                val classDecl = targetType.declaration as? KSClassDeclaration ?: run {
                    logger.error("@KapBridge target must be a class", annotation)
                    return@forEach
                }
                val constructor = classDecl.primaryConstructor ?: run {
                    logger.error("@KapBridge target must have a primary constructor", classDecl)
                    return@forEach
                }

                val className = classDecl.simpleName.asString()
                val packageName = classDecl.packageName.asString()

                val params = constructor.parameters.map { param ->
                    val resolved = param.type.resolve()
                    ParamInfo(
                        name = param.name!!.asString(),
                        typeString = renderType(resolved),
                        isNullable = resolved.isMarkedNullable,
                    )
                }

                if (params.isEmpty()) {
                    logger.error("@KapBridge target must have at least one parameter", classDecl)
                    return@forEach
                }

                val returnType = if (packageName.isEmpty()) className else "$packageName.$className"
                val genPackage = file.packageName.asString().ifEmpty { packageName }

                // @KapBridge generates kap(f: (...) -> ClassName) — same as own classes
                generateForConstructor(
                    containingFile = file,
                    packageName = genPackage,
                    spec = BuilderSpec(className, params, returnType),
                )
            }
    }

    // ── @KapTypeSafe processing ────────────────────────────────────

    private fun extractPrefix(annotated: KSAnnotated): String {
        val annotation = annotated.annotations.first {
            it.shortName.asString() == "KapTypeSafe"
        }
        val prefixArg = annotation.arguments.firstOrNull { it.name?.asString() == "prefix" }
        return (prefixArg?.value as? String) ?: ""
    }

    private data class ParamInfo(
        val name: String,
        val typeString: String,
        val isNullable: Boolean,
    )

    /** A type parameter of the annotated declaration, with rendered bounds. */
    private data class TypeParamInfo(val name: String, val bounds: String?)

    /** Everything the emit functions need to know about one declaration. */
    private data class BuilderSpec(
        val baseName: String,
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

    private fun List<TypeParamInfo>.names() = joinToString(", ") { it.name }

    private fun List<TypeParamInfo>.decl() =
        joinToString(", ") { if (it.bounds != null) "${it.name} : ${it.bounds}" else it.name }

    private fun List<TypeParamInfo>.inst() = if (isEmpty()) "" else "<${names()}>"

    private fun String.referencedParams(all: List<TypeParamInfo>): List<TypeParamInfo> =
        all.filter { Regex("\\b${Regex.escape(it.name)}\\b").containsMatchIn(this) }

    private fun collectTypeParams(decl: KSDeclaration): List<TypeParamInfo> {
        val typeParams = when (decl) {
            is KSClassDeclaration -> decl.typeParameters
            is KSFunctionDeclaration -> decl.typeParameters
            else -> emptyList()
        }
        return typeParams.map { tp ->
            if (tp.isReified) {
                logger.error(
                    "@KapTypeSafe does not support reified type parameters (${tp.name.asString()})", tp,
                )
            }
            val bounds = tp.bounds
                .filterNotNull()
                .map { renderType(it.resolve()) }
                // Drop the implicit `Any?` upper bound — noise in generated code.
                .filter { it != "kotlin.Any?" && it != "Any?" && it != "kotlin.Any" && it != "Any" }
                .joinToString(" & ")
                .ifEmpty { null }
            TypeParamInfo(tp.name.asString(), bounds)
        }
    }

    private fun generateForClass(classDecl: KSClassDeclaration, kapArrowPresent: Boolean = false) {
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()
        val prefix = extractPrefix(classDecl)
        val constructor = classDecl.primaryConstructor ?: run {
            logger.error("@KapTypeSafe requires a primary constructor", classDecl)
            return
        }

        val params = constructor.parameters.map { param ->
            val resolved = param.type.resolve()
            ParamInfo(
                name = param.name!!.asString(),
                typeString = renderType(resolved),
                isNullable = resolved.isMarkedNullable,
            )
        }

        if (params.isEmpty()) {
            logger.error("@KapTypeSafe requires at least one parameter", classDecl)
            return
        }

        val typeParams = collectTypeParams(classDecl)

        // Generic classes render their own type parameters in the return type:
        // `Checkout2<T>` — this flows into every generated receiver/entry.
        val classFqn = if (packageName.isEmpty()) className else "$packageName.$className"
        val returnType = classFqn + typeParams.inst()

        // Classes use kap(::ClassName) — function reference, unique by return type
        generateForConstructor(
            containingFile = classDecl.containingFile!!,
            packageName = packageName,
            spec = BuilderSpec(className, params, returnType, typeParams, prefix, classFqn),
            kapArrowPresent = kapArrowPresent,
        )
    }

    private fun generateForFunction(funcDecl: KSFunctionDeclaration, kapArrowPresent: Boolean = false) {
        val funcName = funcDecl.simpleName.asString()
        val packageName = funcDecl.packageName.asString()
        val prefix = extractPrefix(funcDecl)

        val params = funcDecl.parameters.map { param ->
            val resolved = param.type.resolve()
            ParamInfo(
                name = param.name!!.asString(),
                typeString = renderType(resolved),
                isNullable = resolved.isMarkedNullable,
            )
        }

        if (params.isEmpty()) {
            logger.error("@KapTypeSafe requires at least one parameter", funcDecl)
            return
        }

        if (funcDecl.parameters.any { it.isVararg }) {
            logger.error("@KapTypeSafe does not support vararg parameters", funcDecl)
            return
        }

        val typeParams = collectTypeParams(funcDecl)

        val returnTypeRef = funcDecl.returnType?.resolve()
        val returnType = returnTypeRef?.let { renderType(it) } ?: "kotlin.Unit"

        val baseName = funcName.replaceFirstChar { it.uppercase() }
        val functionCall = if (packageName.isEmpty()) funcName else "$packageName.$funcName"
        val paramTypes = params.map { it.typeString }
        val signatureIsUnique = signatureCounts[signatureKey(paramTypes, returnType)] == 1

        generateForMarkerObject(
            containingFile = funcDecl.containingFile!!,
            packageName = packageName,
            spec = BuilderSpec(baseName, params, returnType, typeParams, prefix, functionCall),
            signatureIsUnique = signatureIsUnique,
            kapArrowPresent = kapArrowPresent,
        )
    }

    // ── Type rendering ─────────────────────────────────────────────

    private fun renderType(type: KSType): String {
        val decl = type.declaration
        // Type parameters render as their simple name — KSP's qualifiedName
        // ("Outer.T") is not a valid reference at the generation site.
        if (decl is KSTypeParameter) {
            return decl.name.asString() + (if (type.isMarkedNullable) "?" else "")
        }
        val base = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
        val args = if (type.arguments.isNotEmpty()) {
            type.arguments.joinToString(", ", "<", ">") { arg ->
                when (arg.variance) {
                    Variance.STAR -> "*"
                    Variance.INVARIANT -> renderType(arg.type!!.resolve())
                    Variance.COVARIANT -> "out ${renderType(arg.type!!.resolve())}"
                    Variance.CONTRAVARIANT -> "in ${renderType(arg.type!!.resolve())}"
                }
            }
        } else ""
        val nullable = if (type.isMarkedNullable) "?" else ""
        return "$base$args$nullable"
    }

    // ── Code generation: constructor-based (classes + bridges) ──────

    /**
     * Generates `kap(f: (P1, P2, ...) -> R): ${baseName}Kap<curried>` — the scoped
     * wrapper API. Used for @KapTypeSafe classes and @KapBridge.
     */
    private fun generateForConstructor(
        containingFile: KSFile,
        packageName: String,
        spec: BuilderSpec,
        kapArrowPresent: Boolean = false,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val typeParams = spec.typeParams
        val hasPackage = packageName.isNotEmpty()
        val fileBaseName = spec.fileBaseName

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${fileBaseName}KapBuilder"
        )

        // Generic entries always use the suffixed name — `kap<T>(...)` overloads
        // from different generic declarations collide in K2 overload resolution.
        val entryFnName = if (typeParams.isEmpty()) "kap" else "kap$baseName"
        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)
            writeOpaqueTypes(writer, spec)
            writeScopedBuilder(writer, spec)
            writeScopedEntry(writer, spec, entryFnName, "f")
        }

        if (kapArrowPresent) {
            val validatedFile = codeGenerator.createNewFile(
                Dependencies(true, containingFile),
                packageName,
                "${fileBaseName}KapBuilderValidated"
            )
            OutputStreamWriter(validatedFile).use { writer ->
                writeValidatedHeader(writer, hasPackage, packageName)
                writeValidatedFromOverloads(writer, spec)
                writeValidatedScopedBuilder(writer, spec)
                val validatedEntryFnName = if (typeParams.isEmpty()) "kapV" else "kapV$baseName"
                writeValidatedScopedEntry(writer, spec, validatedEntryFnName, "f")
            }
        }
    }

    // ── Code generation: function-based ────────────────────────────

    /**
     * Generates the scoped wrapper entry for @KapTypeSafe functions. When the
     * function's (params, return) signature is unique, emits `fun kap(f)`;
     * otherwise emits `fun kap${baseName}(f)` to avoid identical-signature
     * overload collisions on the plain `kap` name.
     */
    private fun generateForMarkerObject(
        containingFile: KSFile,
        packageName: String,
        spec: BuilderSpec,
        signatureIsUnique: Boolean = true,
        kapArrowPresent: Boolean = false,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val hasPackage = packageName.isNotEmpty()
        val fileBaseName = spec.fileBaseName

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${fileBaseName}KapBuilder"
        )

        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)
            writeOpaqueTypes(writer, spec)
            writeScopedBuilder(writer, spec)

            val entryFnName = when {
                typeParams.isNotEmpty() -> "kap$baseName"
                signatureIsUnique -> "kap"
                else -> "kap$baseName"
            }
            writeScopedEntry(writer, spec, entryFnName, "f")

            // Extension property: `(::myFn).kap` and `kap((::myFn)::kap)` — also returns the wrapper.
            if (signatureIsUnique) {
                val wrapperName = "${baseName}Kap"
                val opaqueNames = params.mapIndexed { i, p ->
                    val refs = p.typeString.referencedParams(typeParams)
                    "$baseName${p.name.replaceFirstChar { c -> c.uppercase() }}${refs.inst()}"
                }
                val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
                val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"
                val entryRefs = (params.map { it.typeString } + returnType)
                    .flatMap { it.referencedParams(typeParams) }
                    .distinctBy { it.name }
                val tpDecl = if (entryRefs.isEmpty()) "" else "<${entryRefs.decl()}> "
                val opaqueParamNames = params.indices.map { "p$it" }
                val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }

                writeKapExtensionProperty(writer, spec)
            }
        }

        if (kapArrowPresent) {
            val validatedFile = codeGenerator.createNewFile(
                Dependencies(true, containingFile),
                packageName,
                "${fileBaseName}KapBuilderValidated"
            )
            OutputStreamWriter(validatedFile).use { writer ->
                writeValidatedHeader(writer, hasPackage, packageName)
                writeValidatedFromOverloads(writer, spec)
                writeValidatedScopedBuilder(writer, spec)
                val validatedEntryFnName = when {
                    typeParams.isNotEmpty() -> "kapV$baseName"
                    signatureIsUnique -> "kapV"
                    else -> "kapV$baseName"
                }
                writeValidatedScopedEntry(writer, spec, validatedEntryFnName, "f")
            }
        }
    }

    /** `val ((P) -> R).kap` — enables `(::myFn).kap` and `kap((::myFn)::kap)` forms. */
    private fun writeKapExtensionProperty(writer: OutputStreamWriter, spec: BuilderSpec) {
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

        writer.write("\n/** Extension property — enables `(::myFn).kap` and `kap((::myFn)::kap)` forms. */\n")
        writer.write("val $tpDecl($inputType).kap: $wrapperName<$curriedType>\n")
        writer.write("    get() = $wrapperName(Kap.of(")
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            writer.write("{ $name: $opaque -> ")
        }
        writer.write("this($opaqueCallArgs)")
        writer.write(" }".repeat(spec.params.size))
        writer.write("))\n")
    }

    // ── Shared generation helpers ──────────────────────────────────

    private fun writeHeader(
        writer: OutputStreamWriter,
        hasPackage: Boolean,
        packageName: String,
        params: List<ParamInfo>,
    ) {
        writer.write("// AUTO-GENERATED by kap-ksp — do not edit\n")
        if (hasPackage) {
            writer.write("package $packageName\n\n")
        }
        writer.write("import kap.Kap\n")
        writer.write("import kap.KapLike\n")
        writer.write("import kap.of\n")
        writer.write("import kap.with\n")
        writer.write("import kap.then\n")
        writer.write("import kap.thenValue\n")
        writer.write("import kap.map\n")
        writer.write("import kap.andThen\n")
        writer.write("import kap.evalGraph\n")
        writer.write("\n")
    }

    private fun writeValidatedHeader(
        writer: OutputStreamWriter,
        hasPackage: Boolean,
        packageName: String,
    ) {
        writer.write("// AUTO-GENERATED by kap-ksp — do not edit\n")
        if (hasPackage) {
            writer.write("package $packageName\n\n")
        }
        writer.write("import arrow.core.Either\n")
        writer.write("import arrow.core.NonEmptyList\n")
        writer.write("import kap.Kap\n")
        writer.write("import kap.KapLike\n")
        writer.write("import kap.of\n")
        writer.write("import kap.withV\n")
        writer.write("import kap.thenV\n")
        writer.write("import kap.thenValueV\n")
        writer.write("import kap.evalGraph\n")
        writer.write("\n")
    }

    private fun writeValidatedFromOverloads(
        writer: OutputStreamWriter,
        spec: BuilderSpec,
    ) {
        val e = spec.errorBinder()
        writer.write("// ── Validated infix `from` — maps Either<Nel<$e>, FieldType> into tagged wrapper ──\n\n")
        for (param in spec.params) {
            val wrapperName = "${spec.baseName}${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            val refs = param.typeString.referencedParams(spec.typeParams)
            val tpDecl = if (refs.isEmpty()) "<$e>" else "<$e, ${refs.names()}>"
            val inst = refs.inst()
            writer.write(
                "infix fun $tpDecl $tagClassName.from(value: Either<NonEmptyList<$e>, ${param.typeString}>): " +
                    "Either<NonEmptyList<$e>, $wrapperName$inst> =\n",
            )
            writer.write("    value.map(::$wrapperName)\n\n")
        }
    }

    private fun writeValidatedScopedBuilder(
        writer: OutputStreamWriter,
        spec: BuilderSpec,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"

        writer.write("/** Validated scoped builder for @KapTypeSafe $baseName. Uses the same per-slot\n")
        writer.write(" *  tag interfaces as ${baseName}Kap — each `.withV { field from validateField() }`\n")
        writer.write(" *  narrows the receiver to one slot, accumulating errors via Arrow's applicative.\n")
        writer.write(" */\n")
        val slotImpls = params.joinToString(", ") { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}Slot" }
        val e = spec.errorBinder()
        writer.write(
            "class $wrapperName<$e, F>(@PublishedApi internal val _kap: Kap<Either<NonEmptyList<$e>, F>>) : " +
                "KapLike<Either<NonEmptyList<$e>, F>>, $slotImpls {\n",
        )
        writer.write("    override val asKap: Kap<Either<NonEmptyList<$e>, F>> get() = _kap\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            writer.write("    override val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        writer.write("\n    companion object {\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            writer.write("        val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        writer.write("    }\n")
        writer.write("}\n\n")

        writeValidatedPerSlotOperators(writer, spec)

        writeValidatedParensOperators(writer, spec)
        writer.write(
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
    private fun writeValidatedPerSlotOperators(writer: OutputStreamWriter, spec: BuilderSpec) {
        writer.write("// ── Per-slot .withV / .thenV / .thenValueV operators ──\n\n")
        for ((index, param) in spec.params.withIndex()) {
            val isLast = index == spec.params.size - 1
            for (op in listOf("withV", "thenV", "thenValueV")) {
                emitValidatedSlotOperator(writer, spec, param, isLast, op)
            }
        }
    }

    private fun emitValidatedSlotOperator(
        writer: OutputStreamWriter,
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

        writer.write("@kotlin.jvm.JvmName(\"${op}_${param.name}\")\n")
        if (isLast) {
            writer.write("inline infix fun $tp$wrapperName<$e, ($wrapperType) -> $returnType>.$op(\n")
            writer.write("    crossinline fa: suspend $slotType.() -> $either$wrapperType>,\n")
            writer.write("): Kap<$either$returnType>> {\n")
            writer.write("    val self = this\n")
            writer.write("    return self._kap.$op(Kap { self.fa() })\n")
        } else {
            writer.write("inline infix fun $tp$wrapperName<$e, ($wrapperType) -> $rest>.$op(\n")
            writer.write("    crossinline fa: suspend $slotType.() -> $either$wrapperType>,\n")
            writer.write("): $wrapperName<$e, $rest> {\n")
            writer.write("    val self = this\n")
            writer.write("    return $wrapperName(self._kap.$op(Kap { self.fa() }))\n")
        }
        writer.write("}\n\n")
    }

    /** Parens (Kap-argument) validated forms + `evalGraph`. */
    private fun writeValidatedParensOperators(writer: OutputStreamWriter, spec: BuilderSpec) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}ValidatedKap"
        val e = spec.errorBinder()

        // Generic Kap<Either<Nel<E>, A>> overloads (parens form)
        val validatedKapType = "Kap<Either<NonEmptyList<$e>, A>>"
        val genericValidatedReceiver = "$wrapperName<$e, (A) -> B>"
        writer.write(
            "infix fun <$e, A, B> $genericValidatedReceiver.withV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        writer.write("    $wrapperName(_kap.withV(fa))\n\n")

        writer.write(
            "infix fun <$e, A, B> $genericValidatedReceiver.thenV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        writer.write("    $wrapperName(_kap.thenV(fa))\n\n")

        writer.write(
            "infix fun <$e, A, B> $genericValidatedReceiver.thenValueV(fa: $validatedKapType): $wrapperName<$e, B> =\n",
        )
        writer.write("    $wrapperName(_kap.thenValueV(fa))\n\n")

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
                writer.write(
                    "infix fun $lastTp$lastValidatedReceiver.$op(fa: $lastValidatedKapType): " +
                        "$lastValidatedReturn =\n",
                )
                writer.write("    _kap.$op(fa)\n\n")
            }
        }

    }

    private fun writeValidatedScopedEntry(
        writer: OutputStreamWriter,
        spec: BuilderSpec,
        entryFnName: String,
        callableExpression: String,
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

        writer.write("\n/** Validated entry — returns $wrapperName so `.withV { field from validate() }` works without imports. */\n")
        val argList2 = if (typeParams.isEmpty()) "(f: $inputType)" else "()"
        writer.write("fun $tpDecl$entryFnName$argList2: $wrapperName<$e, $curriedType> {\n")
        writer.write("    val fn: $curriedType = ")
        val opaqueParamNames = params.indices.map { "p$it" }
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            writer.write("{ $name: $opaque -> ")
        }
        val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }
        val callee2 = if (typeParams.isEmpty()) callableExpression else spec.callable
        writer.write("$callee2($opaqueCallArgs)")
        writer.write(" }".repeat(params.size))
        writer.write("\n")
        writer.write("    val kap: Kap<Either<NonEmptyList<$e>, $curriedType>> = Kap.of(Either.Right(fn))\n")
        writer.write("    return $wrapperName(kap)\n")
        writer.write("}\n")
    }

    private fun writeOpaqueTypes(
        writer: OutputStreamWriter,
        spec: BuilderSpec,
    ) {
        val baseName = spec.baseName
        val params = spec.params
        val typeParams = spec.typeParams
        // Wrapper data classes — one per field, named uniquely by class+field.
        // Fields referencing declaration type parameters get their own copy
        // (`data class CheckoutTotal<T>(val value: T)`), so inference flows
        // from the `from` value through the whole chain.
        writer.write("// ── Opaque wrappers — one per field ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val refs = param.typeString.referencedParams(typeParams)
            val tpDecl = if (refs.isEmpty()) "" else "<${refs.decl()}>"
            writer.write("data class $wrapperName$tpDecl(val value: ${param.typeString})\n\n")
        }

        // Tag classes — one per field, top-level. Unique-named (class+field+Tag)
        // so no collisions across @KapTypeSafe data classes. Receivers for the
        // infix `from` extension functions below.
        writer.write("// ── Tag classes (receivers for infix `from`) ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            writer.write("class $tagClassName internal constructor()\n")
        }
        writer.write("\n")

        // Infix `from` — two overloads per field (raw value + `Kap<T>` so
        // combinators like `Kap { ... }.timeout(...)` compose without
        // leaving the graph). Top-level — receivers are unique per class.
        writer.write("// ── Infix `from` — wraps raw value or Kap<T> into the tagged wrapper ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            val refs = param.typeString.referencedParams(typeParams)
            val tpDecl = if (refs.isEmpty()) "" else "<${refs.names()}> "
            val inst = refs.inst()
            writer.write(
                "infix fun $tpDecl$tagClassName.from(value: ${param.typeString}): " +
                    "$wrapperName$inst = $wrapperName(value)\n",
            )
            writer.write(
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
    private fun writeScopedBuilder(
        writer: OutputStreamWriter,
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
        writer.write("// ── Per-slot interfaces (lambda receivers for `.with` / `.then`) ──\n\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            writer.write("interface $baseName${cap}Slot { val ${param.name}: $baseName${cap}Tag }\n")
        }
        writer.write("\n")

        writer.write("/** Scoped builder for @KapTypeSafe $baseName. Implements every slot interface\n")
        writer.write(" *  so each field is reachable as a member. The per-slot `.with` overloads\n")
        writer.write(" *  below narrow the lambda receiver to a single tag — the IDE shows only the\n")
        writer.write(" *  field expected at the current curry position when the body is empty.\n")
        writer.write(" *\n")
        writer.write(" *  The wrapper deliberately does NOT delegate to `Kap<F>`. If it did, the\n")
        writer.write(" *  imported `Kap.with(suspend () -> A)` would compete with the slot-specific\n")
        writer.write(" *  `.with { field from … }` and K2's overload resolution sometimes picks the\n")
        writer.write(" *  generic one (before typechecking the lambda body), causing the slot's tag\n")
        writer.write(" *  reference to fail with `Unresolved reference`.\n")
        writer.write(" *\n")
        writer.write(" *  The wrapper implements `KapLike<F>`, so kap-core operators (.map /\n")
        writer.write(" *  .recover / .timeout / .settled / .memoize / .timed / .andThen /\n")
        writer.write(" *  .evalGraph) are available directly on partial wrappers as well. For raw\n")
        writer.write(" *  `Kap<F>` (e.g. an external API parameter), use `.asKap`.\n")
        writer.write(" */\n")
        val slotImpls = params.joinToString(", ") { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}Slot" }
        writer.write("class $wrapperName<F>(@PublishedApi internal val _kap: Kap<F>) : KapLike<F>, $slotImpls {\n")
        writer.write("    override val asKap: Kap<F> get() = _kap\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            writer.write("    override val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        // Companion mirrors the tag vals so they're reachable from outside the
        // lambda receiver — e.g. `.with($wrapperName.field from Kap { ... })`.
        writer.write("\n    companion object {\n")
        for (param in params) {
            val cap = param.name.replaceFirstChar { it.uppercase() }
            writer.write("        val ${param.name}: $baseName${cap}Tag = $baseName${cap}Tag()\n")
        }
        writer.write("    }\n")
        writer.write("}\n\n")

        // ── Per-slot `.with` and `.then` — narrowed lambda receiver per slot ──
        // Each overload only matches when F begins with that slot's wrapper type.
        // When the user writes `kap(::T).with { _ }`, only ONE overload applies
        // (the one for the head wrapper), and its lambda receiver is the slot
        // interface exposing the single relevant tag.
        //
        // Last-slot optimization: when F = (LastWrapper) -> ReturnType, the overload
        // returns `Kap<ReturnType>` directly — no `.asKap` needed to chain into
        // `andThen { kap(::X)... }` or to apply kap-core operators on the result.
        writePerSlotOperators(writer, spec)

        writeParensOperators(writer, spec)
        writer.write("suspend fun <A> $wrapperName<A>.evalGraph(): A = _kap.evalGraph()\n\n")
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
    private fun writePerSlotOperators(writer: OutputStreamWriter, spec: BuilderSpec) {
        writer.write("// ── Per-slot operators — IDE shows exactly the field expected at this position ──\n\n")
        for ((index, param) in spec.params.withIndex()) {
            val isLast = index == spec.params.size - 1
            for (op in listOf("with", "then", "thenValue")) {
                emitSlotOperator(writer, spec, param, isLast, op)
            }
        }
    }

    private fun emitSlotOperator(
        writer: OutputStreamWriter,
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

        writer.write("@kotlin.jvm.JvmName(\"${op}_${param.name}\")\n")
        if (isLast) {
            // Last slot: curry is fully applied → return Kap<ReturnType> directly.
            writer.write("inline infix fun $tp$wrapperName<($wrapperType) -> $returnType>.$op(\n")
            writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
            writer.write("): Kap<$returnType> {\n")
            writer.write("    val self = this\n")
            writer.write("    return self._kap.$op(suspend { self.fa() })\n")
        } else {
            // Non-last slot: returns wrapper so the chain continues.
            writer.write("inline infix fun $tp$wrapperName<($wrapperType) -> $rest>.$op(\n")
            writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
            writer.write("): $wrapperName<$rest> {\n")
            writer.write("    val self = this\n")
            writer.write("    return $wrapperName(self._kap.$op(suspend { self.fa() }))\n")
        }
        writer.write("}\n\n")
    }

    /** Parens (Kap-argument) forms + `andThen`/`evalGraph`. */
    private fun writeParensOperators(writer: OutputStreamWriter, spec: BuilderSpec) {
        val baseName = spec.baseName
        val params = spec.params
        val returnType = spec.returnType
        val typeParams = spec.typeParams
        val wrapperName = "${baseName}Kap"
        // ── Generic Kap<A> overloads (parens form) — for non-last slots. ──
        // Used when the value is already a Kap<A> built outside the lambda.
        // The last-slot specific overloads below take precedence when the
        // wrapper is at the final curry position.
        writer.write("infix fun <A, B> $wrapperName<(A) -> B>.with(fa: Kap<A>): $wrapperName<B> =\n")
        writer.write("    $wrapperName(_kap.with(fa))\n\n")

        writer.write("infix fun <A, B> $wrapperName<(A) -> B>.then(fa: Kap<A>): $wrapperName<B> =\n")
        writer.write("    $wrapperName(_kap.then(fa))\n\n")

        writer.write("infix fun <A, B> $wrapperName<(A) -> B>.thenValue(fa: Kap<A>): $wrapperName<B> =\n")
        writer.write("    $wrapperName(_kap.thenValue(fa))\n\n")

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

            writer.write("infix fun $lastTp$lastReceiver.with(fa: $lastKapType): $lastReturn =\n")
            writer.write("    _kap.with(fa)\n\n")

            writer.write("infix fun $lastTp$lastReceiver.then(fa: $lastKapType): $lastReturn =\n")
            writer.write("    _kap.then(fa)\n\n")

            writer.write("infix fun $lastTp$lastReceiver.thenValue(fa: $lastKapType): $lastReturn =\n")
            writer.write("    _kap.thenValue(fa)\n\n")
        }

        writer.write("inline infix fun <A, B> $wrapperName<A>.andThen(\n")
        writer.write("    crossinline f: (A) -> Kap<B>,\n")
        writer.write("): Kap<B> = _kap.andThen(f)\n\n")

        // `.asKap` is now a member of the class (via KapLike<F>), exposed here as
        // a reminder that it is the escape hatch to raw Kap<F> for external APIs.
    }

    /**
     * Emits a `kap(...)` entry point that returns the scoped `${baseName}Kap<curried>`.
     * `paramKind` controls whether the input is a function reference (`f: (P) -> R`)
     * or a marker object (`marker: M`) and the body that invokes it.
     */
    private fun writeScopedEntry(
        writer: OutputStreamWriter,
        spec: BuilderSpec,
        entryFnName: String,
        callableExpression: String,
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

        writer.write("\n/** Official entry point — returns $wrapperName so `.with { field from value }` works without imports. */\n")
        // Generic declarations: the entry is `pure(curry C)` at the caller's
        // instantiation of the type variables — the callable is statically
        // known, so the entry takes no argument: `kapCheckout2<Double>()`.
        val callee = if (typeParams.isEmpty()) callableExpression else spec.callable
        val argList = if (typeParams.isEmpty()) "(f: $inputType)" else "()"
        writer.write("fun $tpDecl$entryFnName$argList: $wrapperName<$curriedType> =\n")
        writer.write("    $wrapperName(Kap.of(")
        val opaqueParamNames = params.indices.map { "p$it" }
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            writer.write("{ $name: $opaque -> ")
        }
        val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }
        writer.write("$callee($opaqueCallArgs)")
        writer.write(" }".repeat(params.size))
        writer.write("))\n")
    }

}
