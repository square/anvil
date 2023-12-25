package com.squareup.anvil.compiler.internal.testing

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.anvil.compiler.api.AnvilBackend
import com.squareup.anvil.compiler.api.AnvilBackend.EMBEDDED
import com.squareup.anvil.compiler.api.AnvilBackend.KSP
import com.squareup.anvil.compiler.api.CodeGenerator

sealed class AnvilCompilationMode(val backend: AnvilBackend) {
  data class Embedded(
    val codeGenerators: List<CodeGenerator> = emptyList(),
  ) : AnvilCompilationMode(EMBEDDED)
  data class Ksp(
    val symbolProcessorProviders: List<SymbolProcessorProvider> = emptyList(),
  ) : AnvilCompilationMode(KSP)
}
