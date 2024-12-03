package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnalysisBackend
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Locale

public class CommandLineOptions private constructor(
  public val generateFactories: Boolean,
  public val generateFactoriesOnly: Boolean,
  public val disableComponentMerging: Boolean,
  public val trackSourceFiles: Boolean,
  public val willHaveDaggerFactories: Boolean,
  public val backend: AnalysisBackend,
  public val componentMergingBackend: ComponentMergingBackend,
) {
  public companion object {
    public val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        disableComponentMerging = get(disableComponentMergingKey, false),
        trackSourceFiles = get(trackSourceFilesKey, true),
        willHaveDaggerFactories = get(willHaveDaggerFactoriesKey, false),
        backend = parseBackend(),
        componentMergingBackend = parseComponentMergingBackend(),
      )

    private fun CompilerConfiguration.parseBackend(): AnalysisBackend {
      val config = get(analysisBackendKey, AnalysisBackend.EMBEDDED.name)
      return config
        .uppercase(Locale.US)
        .let { value -> AnalysisBackend.entries.find { it.name == value } }
        ?: error("Unknown backend option: '$config'")
    }

    private fun CompilerConfiguration.parseComponentMergingBackend(): ComponentMergingBackend {
      val config = get(mergingBackendKey, ComponentMergingBackend.IR.name)
      return config
        .uppercase(Locale.US)
        .let { value -> ComponentMergingBackend.entries.find { it.name == value } }
        ?: error("Unknown backend option: '$config'")
    }
  }
}
