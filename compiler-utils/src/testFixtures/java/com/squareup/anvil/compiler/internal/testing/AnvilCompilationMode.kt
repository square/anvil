package com.squareup.anvil.compiler.internal.testing

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.anvil.compiler.api.AnalysisBackend
import com.squareup.anvil.compiler.api.AnalysisBackend.EMBEDDED
import com.squareup.anvil.compiler.api.AnalysisBackend.KSP
import com.squareup.anvil.compiler.api.CodeGenerator

public sealed class AnvilCompilationMode(public val analysisBackend: AnalysisBackend) {
  public data class Embedded(
    val codeGenerators: List<CodeGenerator> = emptyList(),
    val useDagger: Boolean = false,
  ) : AnvilCompilationMode(EMBEDDED)
  public data class Ksp(
    val symbolProcessorProviders: List<SymbolProcessorProvider> = emptyList(),
  ) : AnvilCompilationMode(KSP)
}
