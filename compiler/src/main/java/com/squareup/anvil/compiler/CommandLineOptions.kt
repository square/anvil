package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilBackend
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.util.Locale

class CommandLineOptions private constructor(
  val generateFactories: Boolean,
  val generateFactoriesOnly: Boolean,
  val disableComponentMerging: Boolean,
  val backend: AnvilBackend,
) {
  companion object {
    val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        disableComponentMerging = get(disableComponentMergingKey, false),
        backend = get(backendKey, AnvilBackend.EMBEDDED.name)
          .uppercase(Locale.US)
          .let { value -> AnvilBackend.entries.find { it.name == value } }
          ?: error("Unknown backend option: $this"),
      )
  }
}
