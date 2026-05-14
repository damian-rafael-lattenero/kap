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
                    baseName = className,
                    constructorCall = returnType,
                    params = params,
                    returnType = returnType,
                    prefix = "",
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

        val returnType = if (packageName.isEmpty()) className else "$packageName.$className"

        // Classes use kap(::ClassName) — function reference, unique by return type
        generateForConstructor(
            containingFile = classDecl.containingFile!!,
            packageName = packageName,
            baseName = className,
            constructorCall = returnType,
            params = params,
            returnType = returnType,
            prefix = prefix,
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

        val returnTypeRef = funcDecl.returnType?.resolve()
        val returnType = returnTypeRef?.let { renderType(it) } ?: "kotlin.Unit"

        val baseName = funcName.replaceFirstChar { it.uppercase() }
        val functionCall = if (packageName.isEmpty()) funcName else "$packageName.$funcName"
        val paramTypes = params.map { it.typeString }
        val signatureIsUnique = signatureCounts[signatureKey(paramTypes, returnType)] == 1

        generateForMarkerObject(
            containingFile = funcDecl.containingFile!!,
            packageName = packageName,
            baseName = baseName,
            markerObjectName = baseName,
            functionCall = functionCall,
            params = params,
            returnType = returnType,
            prefix = prefix,
            signatureIsUnique = signatureIsUnique,
            kapArrowPresent = kapArrowPresent,
        )
    }

    // ── Type rendering ─────────────────────────────────────────────

    private fun renderType(type: KSType): String {
        val decl = type.declaration
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
        baseName: String,
        constructorCall: String,
        params: List<ParamInfo>,
        returnType: String,
        prefix: String = "",
        kapArrowPresent: Boolean = false,
    ) {
        val hasPackage = packageName.isNotEmpty()
        val fileBaseName = if (prefix.isEmpty()) baseName else "$prefix$baseName"

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${fileBaseName}KapBuilder"
        )

        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)
            writeOpaqueTypes(writer, baseName, params)
            writeScopedBuilder(writer, baseName, params, returnType)
            writeScopedEntry(writer, baseName, params, returnType, entryFnName = "kap", callableExpression = "f")
        }

        if (kapArrowPresent) {
            val validatedFile = codeGenerator.createNewFile(
                Dependencies(true, containingFile),
                packageName,
                "${fileBaseName}KapBuilderValidated"
            )
            OutputStreamWriter(validatedFile).use { writer ->
                writeValidatedHeader(writer, hasPackage, packageName)
                writeValidatedFromOverloads(writer, baseName, params)
                writeValidatedScopedBuilder(writer, baseName, params, returnType)
                writeValidatedScopedEntry(writer, baseName, params, returnType, entryFnName = "kapV", callableExpression = "f")
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
        baseName: String,
        markerObjectName: String,
        functionCall: String,
        params: List<ParamInfo>,
        returnType: String,
        prefix: String = "",
        signatureIsUnique: Boolean = true,
        kapArrowPresent: Boolean = false,
    ) {
        val hasPackage = packageName.isNotEmpty()
        val fileBaseName = if (prefix.isEmpty()) baseName else "$prefix$baseName"

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${fileBaseName}KapBuilder"
        )

        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)
            writeOpaqueTypes(writer, baseName, params)
            writeScopedBuilder(writer, baseName, params, returnType)

            val entryFnName = if (signatureIsUnique) "kap" else "kap$baseName"
            writeScopedEntry(writer, baseName, params, returnType, entryFnName, callableExpression = "f")

            // Extension property: `(::myFn).kap` and `kap((::myFn)::kap)` — also returns the wrapper.
            if (signatureIsUnique) {
                val wrapperName = "${baseName}Kap"
                val opaqueNames = params.map { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}" }
                val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
                val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"
                val opaqueParamNames = params.indices.map { "p$it" }
                val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }

                writer.write("\n/** Extension property — enables `(::myFn).kap` and `kap((::myFn)::kap)` forms. */\n")
                writer.write("val ($inputType).kap: $wrapperName<$curriedType>\n")
                writer.write("    get() = $wrapperName(Kap.of(")
                opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
                    writer.write("{ $name: $opaque -> ")
                }
                writer.write("this($opaqueCallArgs)")
                writer.write(" }".repeat(params.size))
                writer.write("))\n")
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
                writeValidatedFromOverloads(writer, baseName, params)
                writeValidatedScopedBuilder(writer, baseName, params, returnType)
                val validatedEntryFnName = if (signatureIsUnique) "kapV" else "kapV$baseName"
                writeValidatedScopedEntry(writer, baseName, params, returnType, entryFnName = validatedEntryFnName, callableExpression = "f")
            }
        }
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
        writer.write("import kap.evalGraph\n")
        writer.write("\n")
    }

    private fun writeValidatedFromOverloads(
        writer: OutputStreamWriter,
        baseName: String,
        params: List<ParamInfo>,
    ) {
        writer.write("// ── Validated infix `from` — maps Either<Nel<E>, FieldType> into tagged wrapper ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            val tagClassName = "${wrapperName}Tag"
            writer.write("infix fun <E> $tagClassName.from(value: Either<NonEmptyList<E>, ${param.typeString}>): Either<NonEmptyList<E>, $wrapperName> =\n")
            writer.write("    value.map(::$wrapperName)\n\n")
        }
    }

    private fun writeValidatedScopedBuilder(
        writer: OutputStreamWriter,
        baseName: String,
        params: List<ParamInfo>,
        returnType: String,
    ) {
        val wrapperName = "${baseName}ValidatedKap"

        writer.write("/** Validated scoped builder for @KapTypeSafe $baseName. Uses the same per-slot\n")
        writer.write(" *  tag interfaces as ${baseName}Kap — each `.withV { field from validateField() }`\n")
        writer.write(" *  narrows the receiver to one slot, accumulating errors via Arrow's applicative.\n")
        writer.write(" */\n")
        val slotImpls = params.joinToString(", ") { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}Slot" }
        writer.write("class $wrapperName<E, F>(@PublishedApi internal val _kap: Kap<Either<NonEmptyList<E>, F>>) : KapLike<Either<NonEmptyList<E>, F>>, $slotImpls {\n")
        writer.write("    override val asKap: Kap<Either<NonEmptyList<E>, F>> get() = _kap\n")
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

        writer.write("// ── Per-slot .withV / .thenV operators ──\n\n")
        for ((index, param) in params.withIndex()) {
            val isLast = index == params.size - 1
            val cap = param.name.replaceFirstChar { it.uppercase() }
            val wrapperType = "$baseName$cap"
            val slotType = "${wrapperType}Slot"

            if (isLast) {
                writer.write("@kotlin.jvm.JvmName(\"withV_${param.name}\")\n")
                writer.write("inline fun <E> $wrapperName<E, ($wrapperType) -> $returnType>.withV(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> Either<NonEmptyList<E>, $wrapperType>,\n")
                writer.write("): Kap<Either<NonEmptyList<E>, $returnType>> {\n")
                writer.write("    val self = this\n")
                writer.write("    return self._kap.withV(Kap { self.fa() })\n")
                writer.write("}\n\n")

                writer.write("@kotlin.jvm.JvmName(\"thenV_${param.name}\")\n")
                writer.write("inline fun <E> $wrapperName<E, ($wrapperType) -> $returnType>.thenV(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> Either<NonEmptyList<E>, $wrapperType>,\n")
                writer.write("): Kap<Either<NonEmptyList<E>, $returnType>> {\n")
                writer.write("    val self = this\n")
                writer.write("    return self._kap.thenV(Kap { self.fa() })\n")
                writer.write("}\n\n")
            } else {
                writer.write("@kotlin.jvm.JvmName(\"withV_${param.name}\")\n")
                writer.write("inline fun <E, Rest> $wrapperName<E, ($wrapperType) -> Rest>.withV(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> Either<NonEmptyList<E>, $wrapperType>,\n")
                writer.write("): $wrapperName<E, Rest> {\n")
                writer.write("    val self = this\n")
                writer.write("    return $wrapperName(self._kap.withV(Kap { self.fa() }))\n")
                writer.write("}\n\n")

                writer.write("@kotlin.jvm.JvmName(\"thenV_${param.name}\")\n")
                writer.write("inline fun <E, Rest> $wrapperName<E, ($wrapperType) -> Rest>.thenV(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> Either<NonEmptyList<E>, $wrapperType>,\n")
                writer.write("): $wrapperName<E, Rest> {\n")
                writer.write("    val self = this\n")
                writer.write("    return $wrapperName(self._kap.thenV(Kap { self.fa() }))\n")
                writer.write("}\n\n")
            }
        }

        // Generic Kap<Either<Nel<E>, A>> overloads (parens form)
        writer.write("fun <E, A, B> $wrapperName<E, (A) -> B>.withV(fa: Kap<Either<NonEmptyList<E>, A>>): $wrapperName<E, B> =\n")
        writer.write("    $wrapperName(_kap.withV(fa))\n\n")

        writer.write("fun <E, A, B> $wrapperName<E, (A) -> B>.thenV(fa: Kap<Either<NonEmptyList<E>, A>>): $wrapperName<E, B> =\n")
        writer.write("    $wrapperName(_kap.thenV(fa))\n\n")

        // Last-slot parens form
        if (params.isNotEmpty()) {
            val lastCap = params.last().name.replaceFirstChar { it.uppercase() }
            val lastWrapperType = "$baseName$lastCap"
            writer.write("fun <E> $wrapperName<E, ($lastWrapperType) -> $returnType>.withV(fa: Kap<Either<NonEmptyList<E>, $lastWrapperType>>): Kap<Either<NonEmptyList<E>, $returnType>> =\n")
            writer.write("    _kap.withV(fa)\n\n")

            writer.write("fun <E> $wrapperName<E, ($lastWrapperType) -> $returnType>.thenV(fa: Kap<Either<NonEmptyList<E>, $lastWrapperType>>): Kap<Either<NonEmptyList<E>, $returnType>> =\n")
            writer.write("    _kap.thenV(fa)\n\n")
        }

        writer.write("suspend fun <E, A> $wrapperName<E, A>.evalGraph(): Either<NonEmptyList<E>, A> = _kap.evalGraph()\n\n")
    }

    private fun writeValidatedScopedEntry(
        writer: OutputStreamWriter,
        baseName: String,
        params: List<ParamInfo>,
        returnType: String,
        entryFnName: String,
        callableExpression: String,
    ) {
        val wrapperName = "${baseName}ValidatedKap"
        val opaqueNames = params.map { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}" }
        val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
        val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"

        writer.write("\n/** Validated entry — returns $wrapperName so `.withV { field from validate() }` works without imports. */\n")
        writer.write("fun <E> $entryFnName(f: $inputType): $wrapperName<E, $curriedType> {\n")
        writer.write("    val fn: $curriedType = ")
        val opaqueParamNames = params.indices.map { "p$it" }
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            writer.write("{ $name: $opaque -> ")
        }
        val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }
        writer.write("$callableExpression($opaqueCallArgs)")
        writer.write(" }".repeat(params.size))
        writer.write("\n")
        writer.write("    val kap: Kap<Either<NonEmptyList<E>, $curriedType>> = Kap.of(Either.Right(fn))\n")
        writer.write("    return $wrapperName(kap)\n")
        writer.write("}\n")
    }

    private fun writeOpaqueTypes(
        writer: OutputStreamWriter,
        baseName: String,
        params: List<ParamInfo>,
    ) {
        // Wrapper data classes — one per field, named uniquely by class+field.
        writer.write("// ── Opaque wrappers — one per field ──\n\n")
        for (param in params) {
            val wrapperName = "$baseName${param.name.replaceFirstChar { it.uppercase() }}"
            writer.write("data class $wrapperName(val value: ${param.typeString})\n\n")
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
            writer.write("infix fun $tagClassName.from(value: ${param.typeString}): $wrapperName = $wrapperName(value)\n")
            writer.write("infix fun $tagClassName.from(kap: Kap<${param.typeString}>): Kap<$wrapperName> = kap.map(::$wrapperName)\n\n")
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
        baseName: String,
        params: List<ParamInfo>,
        returnType: String,
    ) {
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
        writer.write("// ── Per-slot operators — IDE shows exactly the field expected at this position ──\n\n")
        for ((index, param) in params.withIndex()) {
            val isLast = index == params.size - 1
            val cap = param.name.replaceFirstChar { it.uppercase() }
            val wrapperType = "$baseName$cap"
            val slotType = "${wrapperType}Slot"

            if (isLast) {
                // Last slot: curry is fully applied → return Kap<ReturnType> directly.
                writer.write("@kotlin.jvm.JvmName(\"with_${param.name}\")\n")
                writer.write("inline fun $wrapperName<($wrapperType) -> $returnType>.with(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
                writer.write("): Kap<$returnType> {\n")
                writer.write("    val self = this\n")
                writer.write("    return self._kap.with(suspend { self.fa() })\n")
                writer.write("}\n\n")

                writer.write("@kotlin.jvm.JvmName(\"then_${param.name}\")\n")
                writer.write("inline fun $wrapperName<($wrapperType) -> $returnType>.then(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
                writer.write("): Kap<$returnType> {\n")
                writer.write("    val self = this\n")
                writer.write("    return self._kap.then(suspend { self.fa() })\n")
                writer.write("}\n\n")
            } else {
                // Non-last slot: returns wrapper so the chain continues.
                writer.write("@kotlin.jvm.JvmName(\"with_${param.name}\")\n")
                writer.write("inline fun <Rest> $wrapperName<($wrapperType) -> Rest>.with(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
                writer.write("): $wrapperName<Rest> {\n")
                writer.write("    val self = this\n")
                writer.write("    return $wrapperName(self._kap.with(suspend { self.fa() }))\n")
                writer.write("}\n\n")

                writer.write("@kotlin.jvm.JvmName(\"then_${param.name}\")\n")
                writer.write("inline fun <Rest> $wrapperName<($wrapperType) -> Rest>.then(\n")
                writer.write("    crossinline fa: suspend $slotType.() -> $wrapperType,\n")
                writer.write("): $wrapperName<Rest> {\n")
                writer.write("    val self = this\n")
                writer.write("    return $wrapperName(self._kap.then(suspend { self.fa() }))\n")
                writer.write("}\n\n")
            }
        }

        // ── Generic Kap<A> overloads (parens form) — for non-last slots. ──
        // Used when the value is already a Kap<A> built outside the lambda.
        // The last-slot specific overloads below take precedence when the
        // wrapper is at the final curry position.
        writer.write("fun <A, B> $wrapperName<(A) -> B>.with(fa: Kap<A>): $wrapperName<B> =\n")
        writer.write("    $wrapperName(_kap.with(fa))\n\n")

        writer.write("fun <A, B> $wrapperName<(A) -> B>.then(fa: Kap<A>): $wrapperName<B> =\n")
        writer.write("    $wrapperName(_kap.then(fa))\n\n")

        // ── Last-slot parens form — more specific, returns Kap<ReturnType>. ──
        if (params.isNotEmpty()) {
            val lastCap = params.last().name.replaceFirstChar { it.uppercase() }
            val lastWrapperType = "$baseName$lastCap"
            writer.write("fun $wrapperName<($lastWrapperType) -> $returnType>.with(fa: Kap<$lastWrapperType>): Kap<$returnType> =\n")
            writer.write("    _kap.with(fa)\n\n")

            writer.write("fun $wrapperName<($lastWrapperType) -> $returnType>.then(fa: Kap<$lastWrapperType>): Kap<$returnType> =\n")
            writer.write("    _kap.then(fa)\n\n")
        }

        writer.write("inline fun <A, B> $wrapperName<A>.andThen(\n")
        writer.write("    crossinline f: (A) -> Kap<B>,\n")
        writer.write("): Kap<B> = _kap.andThen(f)\n\n")

        // `.asKap` is now a member of the class (via KapLike<F>), exposed here as
        // a reminder that it is the escape hatch to raw Kap<F> for external APIs.
        writer.write("suspend fun <A> $wrapperName<A>.evalGraph(): A = _kap.evalGraph()\n\n")
    }

    /**
     * Emits a `kap(...)` entry point that returns the scoped `${baseName}Kap<curried>`.
     * `paramKind` controls whether the input is a function reference (`f: (P) -> R`)
     * or a marker object (`marker: M`) and the body that invokes it.
     */
    private fun writeScopedEntry(
        writer: OutputStreamWriter,
        baseName: String,
        params: List<ParamInfo>,
        returnType: String,
        entryFnName: String,
        callableExpression: String,
    ) {
        val wrapperName = "${baseName}Kap"
        val opaqueNames = params.map { "$baseName${it.name.replaceFirstChar { c -> c.uppercase() }}" }
        val curriedType = opaqueNames.joinToString(" -> ") { "($it)" } + " -> $returnType"
        val inputType = "(${params.joinToString(", ") { it.typeString }}) -> $returnType"

        writer.write("\n/** Official entry point — returns $wrapperName so `.with { field from value }` works without imports. */\n")
        writer.write("fun $entryFnName(f: $inputType): $wrapperName<$curriedType> =\n")
        writer.write("    $wrapperName(Kap.of(")
        val opaqueParamNames = params.indices.map { "p$it" }
        opaqueParamNames.zip(opaqueNames).forEach { (name, opaque) ->
            writer.write("{ $name: $opaque -> ")
        }
        val opaqueCallArgs = opaqueParamNames.joinToString(", ") { "$it.value" }
        writer.write("$callableExpression($opaqueCallArgs)")
        writer.write(" }".repeat(params.size))
        writer.write("))\n")
    }

}
