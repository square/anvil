package com.squareup.anvil.compiler.internal.testing

import com.squareup.anvil.compiler.api.AnalysisBackend
import com.squareup.anvil.compiler.api.AnalysisBackend.EMBEDDED
import com.squareup.anvil.compiler.api.CodeGenerator

// TODO: Repurpose this as a way to pass a spec into `compile(...)` in tests,
//  instead of individual flags.
//  This is left in place for now because it's already wired up everywhere.
public sealed class AnvilCompilationMode(public val analysisBackend: AnalysisBackend) {
  public data class Embedded(
    val codeGenerators: List<CodeGenerator> = emptyList(),
  ) : AnvilCompilationMode(EMBEDDED)
}
