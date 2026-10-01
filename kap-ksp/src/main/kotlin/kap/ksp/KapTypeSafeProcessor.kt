package kap.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

/**
 * Symbolic resolution half of the code generator: reads `@KapTypeSafe` /
 * `@KapBridge` declarations, decides WHAT to generate (names, entry policy,
 * clash handling, type-parameter collection) and hands a [BuilderSpec] to the
 * pure [KapBuilderEmitter] engine, which decides HOW it renders.
 *
 * Options:
 *  - `kap.dump=true` — dry run: log the generated sources instead of writing
 *    them, so users can inspect a declaration's output before compiling
 *    (`./gradlew :app:kspKotlin -PkapDump` with the standard wiring).
 */
class KapTypeSafeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String> = emptyMap(),
) : SymbolProcessor {

    private val dump = options["kap.dump"] == "true"

    /**
     * Full-signature counts across all @KapTypeSafe/@KapBridge declarations in
     * this round. The plain `kap(f)` / `kapV(f)` entries are emitted only when
     * the signature is unique; colliding shapes fall back to class-named
     * entries (`kap${BaseName}` / zero-arg `kap${BaseName}()` for generics).
     */
    private val signatureCounts = mutableMapOf<String, Int>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val unprocessed = mutableListOf<KSAnnotated>()
        signatureCounts.clear()

        val kapArrowPresent = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("arrow.core.Either")
        ) != null

        // Pre-pass: count signatures across classes + functions + bridges so
        // the generators can decide whether `kap(f: ...)` would collide.
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

    // ── Signature accounting ───────────────────────────────────────

    /**
     * Clash key = the full Kotlin signature of the generated `kap(f)` entry,
     * with the declaration's type parameters α-renamed positionally
     * (`T` -> `#0`), so `Box<T>(v: T)` collides with `Crate<U>(v: U): Crate<U>`
     * only when returns also match — different returns coexist fine: top-level
     * functions in different generated files live in different JVM facades,
     * and the callable reference's return type disambiguates at the call site.
     */
    private fun clashKey(
        paramTypes: List<String>,
        typeParamNames: List<String>,
        returnType: String,
    ): String {
        fun canonical(t: String) =
            typeParamNames.foldIndexed(t) { i, acc, name ->
                acc.replace("\\b$name\\b".toRegex(), "#$i")
            }
        val params = paramTypes.map(::canonical).joinToString(",")
        return "($params)->${canonical(returnType)}"
    }

    private fun recordSignature(symbol: KSAnnotated) {
        when (symbol) {
            is KSClassDeclaration -> {
                if (symbol.classKind != ClassKind.CLASS) return
                val ctor = symbol.primaryConstructor ?: return
                val paramTypes = ctor.parameters.map { renderType(it.type.resolve()) }
                // Return renders with the class's OWN type parameters (not star
                // projections) so class/function keys compare canonically.
                val tpNames = collectTypeParams(symbol).map { it.name }
                val base = symbol.simpleName.asString()
                val returnType = if (tpNames.isEmpty()) base else "$base<${tpNames.joinToString(", ")}>"
                signatureCounts.merge(clashKey(paramTypes, tpNames, returnType), 1, Int::plus)
            }
            is KSFunctionDeclaration -> {
                val paramTypes = symbol.parameters.map { renderType(it.type.resolve()) }
                val returnType = symbol.returnType?.resolve()?.let { renderType(it) } ?: "kotlin.Unit"
                val tpNames = collectTypeParams(symbol).map { it.name }
                signatureCounts.merge(clashKey(paramTypes, tpNames, returnType), 1, Int::plus)
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
            val tpNames = collectTypeParams(classDecl).map { it.name }
            signatureCounts.merge(clashKey(paramTypes, tpNames, returnType), 1, Int::plus)
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
                val bridgeUnique = signatureCounts[
                    clashKey(params.map { it.typeString }, emptyList(), returnType),
                ] == 1
                generateForConstructor(
                    containingFile = file,
                    packageName = genPackage,
                    spec = BuilderSpec(
                        baseName = className,
                        packageName = genPackage,
                        params = params,
                        returnType = returnType,
                    ),
                    signatureIsUnique = bridgeUnique,
                )
            }
    }

    // ── @KapTypeSafe processing ────────────────────────────────────

    private fun extractPrefix(annotated: KSAnnotated): String {
        val annotation = annotated.annotations.first {
            it.shortName.asString() == "KapTypeSafe"
        }
        val prefixArg = annotation.arguments.firstOrNull { it.name?.asString() == "prefix" }
        return (prefixArg?.value as? String).orEmpty()
    }

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
        val constructor = classDecl.primaryConstructor
        val containingFile = classDecl.containingFile
        if (constructor == null || containingFile == null) {
            if (constructor == null) {
                logger.error("@KapTypeSafe requires a primary constructor", classDecl)
            } else {
                logger.error("@KapTypeSafe class $className has no containing file", classDecl)
            }
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

        // Classes use kap(::ClassName) — plain `kap` only when the ctor shape
        // is unique across all declarations (JVM erasure ignores return types).
        val signatureIsUnique = signatureCounts[
            clashKey(params.map { it.typeString }, typeParams.map { it.name }, returnType),
        ] == 1
        generateForConstructor(
            containingFile = containingFile,
            packageName = packageName,
            spec = BuilderSpec(
                baseName = className,
                packageName = packageName,
                params = params,
                returnType = returnType,
                typeParams = typeParams,
                prefix = prefix,
                callable = classFqn,
            ),
            signatureIsUnique = signatureIsUnique,
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

        val containingFile = funcDecl.containingFile
        if (funcDecl.parameters.any { it.isVararg } || containingFile == null) {
            if (funcDecl.parameters.any { it.isVararg }) {
                logger.error("@KapTypeSafe does not support vararg parameters", funcDecl)
            } else {
                logger.error("@KapTypeSafe function $funcName has no containing file", funcDecl)
            }
            return
        }

        val typeParams = collectTypeParams(funcDecl)

        val returnTypeRef = funcDecl.returnType?.resolve()
        val returnType = returnTypeRef?.let { renderType(it) } ?: "kotlin.Unit"

        val baseName = funcName.replaceFirstChar { it.uppercase() }
        val functionCall = if (packageName.isEmpty()) funcName else "$packageName.$funcName"
        val signatureIsUnique = signatureCounts[
            clashKey(params.map { it.typeString }, typeParams.map { it.name }, returnType),
        ] == 1

        generateForMarkerObject(
            containingFile = containingFile,
            packageName = packageName,
            spec = BuilderSpec(
                baseName = baseName,
                packageName = packageName,
                params = params,
                returnType = returnType,
                typeParams = typeParams,
                prefix = prefix,
                callable = functionCall,
            ),
            signatureIsUnique = signatureIsUnique,
            kapArrowPresent = kapArrowPresent,
        )
    }

    // ── Generation dispatch ────────────────────────────────────────

    private fun generateForConstructor(
        containingFile: KSFile,
        packageName: String,
        spec: BuilderSpec,
        signatureIsUnique: Boolean = true,
        kapArrowPresent: Boolean = false,
    ) {
        val baseName = spec.baseName

        // Plain `kap(f)` whenever the ctor shape is unique — for generics the
        // caller pins the type variables: `kap<Double>(::Checkout2)`. The
        // zero-arg `kap$baseName()` fallback is emitted by the engine.
        val entryFnName = if (signatureIsUnique) "kap" else "kap$baseName"
        writeOrDump(
            containingFile, packageName, "${spec.fileBaseName}KapBuilder",
            KapBuilderEmitter.plainFile(spec, entryFnName, kapExtensionProperty = false),
        )
        if (kapArrowPresent) {
            val validatedEntryFnName = if (signatureIsUnique) "kapV" else "kapV$baseName"
            writeOrDump(
                containingFile, packageName, "${spec.fileBaseName}KapBuilderValidated",
                KapBuilderEmitter.validatedFile(spec, validatedEntryFnName),
            )
        }
    }

    private fun generateForMarkerObject(
        containingFile: KSFile,
        packageName: String,
        spec: BuilderSpec,
        signatureIsUnique: Boolean = true,
        kapArrowPresent: Boolean = false,
    ) {
        val baseName = spec.baseName

        val entryFnName = if (signatureIsUnique) "kap" else "kap$baseName"
        writeOrDump(
            containingFile, packageName, "${spec.fileBaseName}KapBuilder",
            KapBuilderEmitter.plainFile(spec, entryFnName, kapExtensionProperty = signatureIsUnique),
        )
        if (kapArrowPresent) {
            val validatedEntryFnName = if (signatureIsUnique) "kapV" else "kapV$baseName"
            writeOrDump(
                containingFile, packageName, "${spec.fileBaseName}KapBuilderValidated",
                KapBuilderEmitter.validatedFile(spec, validatedEntryFnName),
            )
        }
    }

    /**
     * Dry-run seam: with `kap.dump=true` the generated sources are logged
     * instead of written — inspect what a declaration produces BEFORE
     * compiling the consumer.
     */
    private fun writeOrDump(
        containingFile: KSFile,
        packageName: String,
        fileName: String,
        content: String,
    ) {
        if (dump) {
            logger.warn("kap.dump — $fileName.kt\n$content")
            return
        }
        val file = codeGenerator.createNewFile(Dependencies(true, containingFile), packageName, fileName)
        file.use { it.write(content.toByteArray()) }
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
}
