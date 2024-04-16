package com.squareup.anvil.compiler.internal.testing

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.anvil.compiler.api.AnalysisBackend
import com.squareup.anvil.compiler.api.AnalysisBackend.EMBEDDED
import com.squareup.anvil.compiler.api.AnalysisBackend.KSP
import com.squareup.anvil.compiler.api.CodeGenerator

public sealed class AnvilCompilationMode(public val analysisBackend: AnalysisBackend) {
  public data class Embedded(
    val codeGenerators: List<CodeGenerator> = emptyList(),
  ) : AnvilCompilationMode(EMBEDDED) {
    override fun toString(): String {
      return if (codeGenerators.isEmpty()) {
        "Embedded"
      } else {
        "Embedded(codeGenerators=$codeGenerators)"
      }
    }
  }

  public data class Ksp(
    val symbolProcessorProviders: List<SymbolProcessorProvider> = emptyList(),
    val useKSP2: Boolean = false,
  ) : AnvilCompilationMode(KSP) {
    override fun toString(): String {
      val processors = if (symbolProcessorProviders.isEmpty()) {
        ""
      } else {
        "symbolProcessorProviders=$symbolProcessorProviders,"
      }
      return "Ksp(${processors}useKSP2=$useKSP2)"
    }
  }
}
