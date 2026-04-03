package kap.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

class KapTypeSafeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val unprocessed = mutableListOf<KSAnnotated>()

        // Process @KapTypeSafe
        resolver.getSymbolsWithAnnotation("kap.KapTypeSafe").forEach { symbol ->
            if (!symbol.validate()) {
                unprocessed.add(symbol)
                return@forEach
            }
            when (symbol) {
                is KSClassDeclaration -> {
                    if (symbol.classKind == ClassKind.CLASS) {
                        generateForClass(symbol)
                    } else {
                        logger.error("@KapTypeSafe can only be applied to classes or functions", symbol)
                    }
                }
                is KSFunctionDeclaration -> generateForFunction(symbol)
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

    private fun generateForClass(classDecl: KSClassDeclaration) {
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
        )
    }

    private fun generateForFunction(funcDecl: KSFunctionDeclaration) {
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

        // Functions use kap(MarkerObject) — generated object, never collides
        generateForMarkerObject(
            containingFile = funcDecl.containingFile!!,
            packageName = packageName,
            baseName = baseName,
            markerObjectName = baseName,
            functionCall = functionCall,
            params = params,
            returnType = returnType,
            prefix = prefix,
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
     * Generates kap(f: (P1, P2, ...) -> ReturnType): Step0
     * Used for @KapTypeSafe classes and @KapBridge — the return type
     * makes the overload unique, so no marker object is needed.
     */
    private fun generateForConstructor(
        containingFile: KSFile,
        packageName: String,
        baseName: String,
        constructorCall: String,
        params: List<ParamInfo>,
        returnType: String,
        prefix: String = "",
    ) {
        val hasPackage = packageName.isNotEmpty()
        val stepPrefix = if (prefix.isEmpty()) baseName else "$prefix$baseName"

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${stepPrefix}KapBuilder"
        )

        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)
            writeStepClasses(writer, stepPrefix, params, returnType, prefix)

            // Entry point: kap(f: (...) -> ReturnType)
            val originalTypes = params.joinToString(", ") { it.typeString }
            val step0 = "${stepPrefix}Step0"

            writer.write("fun kap(f: ($originalTypes) -> $returnType): $step0 =\n")
            writer.write("    $step0(\n")
            writer.write("        Kap.of(")
            writeCurriedLambda(writer, params, "f")
            writer.write(")\n")
            writer.write("    )\n")
        }
    }

    // ── Code generation: marker-based (functions) ──────────────────

    /**
     * Generates an object marker + kap(marker: MarkerObject): Step0
     * Used for @KapTypeSafe functions — the marker object makes the
     * overload unique even when multiple functions share the same types.
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
    ) {
        val hasPackage = packageName.isNotEmpty()
        val stepPrefix = if (prefix.isEmpty()) baseName else "$prefix$baseName"

        val file = codeGenerator.createNewFile(
            Dependencies(true, containingFile),
            packageName,
            "${stepPrefix}KapBuilder"
        )

        OutputStreamWriter(file).use { writer ->
            writeHeader(writer, hasPackage, packageName, params)

            // Generate marker object
            writer.write("object $markerObjectName\n\n")

            writeStepClasses(writer, stepPrefix, params, returnType, prefix)

            // Entry point: kap(marker: MarkerObject)
            val step0 = "${stepPrefix}Step0"

            writer.write("@Suppress(\"UNUSED_PARAMETER\")\n")
            writer.write("fun kap(marker: $markerObjectName): $step0 =\n")
            writer.write("    $step0(\n")
            writer.write("        Kap.of(")

            // Build curried lambda that calls the original function
            val paramNames = params.indices.map { "p$it" }
            paramNames.zip(params).forEach { (name, param) ->
                writer.write("{ $name: ${param.typeString} -> ")
            }
            val callArgs = paramNames.joinToString(", ")
            writer.write("$functionCall($callArgs)")
            writer.write(" }".repeat(params.size))

            writer.write(")\n")
            writer.write("    )\n")
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
        writer.write("import kap.of\n")
        writer.write("import kap.with\n")
        writer.write("import kap.then\n")
        if (params.any { it.isNullable }) {
            writer.write("import kap.withOrNull\n")
        }
        writer.write("\n")
    }

    private fun writeStepClasses(
        writer: OutputStreamWriter,
        stepPrefix: String,
        params: List<ParamInfo>,
        returnType: String,
        prefix: String,
    ) {
        for (i in params.indices) {
            val stepClassName = "${stepPrefix}Step$i"
            val remainingCurried = buildCurriedType(params, i, returnType)
            val param = params[i]
            val paramNameCap = param.name.replaceFirstChar { it.uppercase() }
            val methodPrefix = if (prefix.isEmpty()) "" else prefix
            val withName = "with$methodPrefix$paramNameCap"
            val thenName = "then$methodPrefix$paramNameCap"

            val isLastStep = i == params.size - 1
            val nextType = if (isLastStep) "Kap<$returnType>" else "${stepPrefix}Step${i + 1}"
            val wrapNext = { expr: String ->
                if (isLastStep) expr else "${stepPrefix}Step${i + 1}($expr)"
            }

            writer.write("class $stepClassName internal constructor(\n")
            writer.write("    internal val inner: Kap<$remainingCurried>,\n")
            writer.write(") {\n")

            // withX (suspend lambda)
            writer.write("    fun $withName(f: suspend () -> ${param.typeString}): $nextType =\n")
            writer.write("        ${wrapNext("inner.with(f)")}\n")

            // withX (Kap overload)
            writer.write("    fun $withName(fa: Kap<${param.typeString}>): $nextType =\n")
            writer.write("        ${wrapNext("inner.with(fa)")}\n")

            // thenX (suspend lambda)
            writer.write("    fun $thenName(f: suspend () -> ${param.typeString}): $nextType =\n")
            writer.write("        ${wrapNext("inner.then(f)")}\n")

            // thenX (Kap overload)
            writer.write("    fun $thenName(fa: Kap<${param.typeString}>): $nextType =\n")
            writer.write("        ${wrapNext("inner.then(fa)")}\n")

            // withXOrNull for nullable types
            if (param.isNullable) {
                val nonNullType = param.typeString.removeSuffix("?")
                writer.write("    fun ${withName}OrNull(fa: Kap<$nonNullType>?): $nextType =\n")
                writer.write("        ${wrapNext("inner.withOrNull(fa)")}\n")
            }

            writer.write("}\n\n")
        }
    }

    private fun writeCurriedLambda(
        writer: OutputStreamWriter,
        params: List<ParamInfo>,
        functionRef: String,
    ) {
        val paramNames = params.indices.map { "p$it" }
        paramNames.zip(params).forEach { (name, param) ->
            writer.write("{ $name: ${param.typeString} -> ")
        }
        val callArgs = paramNames.joinToString(", ")
        writer.write("$functionRef($callArgs)")
        writer.write(" }".repeat(params.size))
    }

    private fun buildCurriedType(params: List<ParamInfo>, fromIndex: Int, returnType: String): String {
        val parts = params.subList(fromIndex, params.size)
            .joinToString(" -> ") { "(${it.typeString})" }
        return "$parts -> $returnType"
    }
}
