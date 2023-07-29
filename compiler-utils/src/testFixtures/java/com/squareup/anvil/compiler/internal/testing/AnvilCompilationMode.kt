package com.squareup.anvil.compiler.internal.testing

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode.Type.EMBEDDED
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode.Type.KSP

sealed class AnvilCompilationMode(val type: Type) {
  enum class Type {
    EMBEDDED, KSP
  }

  data class Embedded(
    val codeGenerators: List<CodeGenerator> = emptyList()
  ) : AnvilCompilationMode(EMBEDDED)
  data class Ksp(
    val symbolProcessorProviders: List<SymbolProcessorProvider> = emptyList()
  ) : AnvilCompilationMode(KSP)
}
