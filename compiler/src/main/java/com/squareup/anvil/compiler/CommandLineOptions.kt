package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilBackend
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Locale

public class CommandLineOptions private constructor(
  public val generateFactories: Boolean,
  public val generateFactoriesOnly: Boolean,
  public val disableComponentMerging: Boolean,
  public val trackSourceFiles: Boolean,
  public val backend: AnvilBackend,
  public val componentMergingBackend: ComponentMergingBackend,
) {
  public companion object {
    public val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        disableComponentMerging = get(disableComponentMergingKey, false),
        trackSourceFiles = get(trackSourceFilesKey, false),
        backend = parseBackend(),
        componentMergingBackend = parseComponentMergingBackend(),
      )

    private fun CompilerConfiguration.parseBackend(): AnvilBackend {
      val config = get(analysisBackendKey, AnvilBackend.EMBEDDED.name)
      return config
        .uppercase(Locale.US)
        .let { value -> AnvilBackend.entries.find { it.name == value } }
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
