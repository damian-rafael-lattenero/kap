package kap.ksp

import com.google.devtools.ksp.processing.*

class KapTypeSafeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KapTypeSafeProcessor(environment.codeGenerator, environment.logger)
}
