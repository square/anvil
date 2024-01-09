package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilBackend
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Locale

public class CommandLineOptions private constructor(
  public val generateFactories: Boolean,
  public val generateFactoriesOnly: Boolean,
  public val disableComponentMerging: Boolean,
  public val backend: AnvilBackend,
) {
  public companion object {
    public val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        disableComponentMerging = get(disableComponentMergingKey, false),
        backend = parseBackend(),
      )

    private fun CompilerConfiguration.parseBackend(): AnvilBackend {
      val config = get(backendKey, AnvilBackend.EMBEDDED.name)
      return config
        .uppercase(Locale.US)
        .let { value -> AnvilBackend.entries.find { it.name == value } }
        ?: error("Unknown backend option: '$config'")
    }
  }
}
