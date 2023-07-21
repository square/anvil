package com.squareup.anvil.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration

class CommandLineOptions private constructor(
  val generateFactories: Boolean,
  val generateFactoriesOnly: Boolean,
  val generateFactoriesPathWhitelist: List<String>,
  val disableComponentMerging: Boolean,
) {
  companion object {
    val CompilerConfiguration.commandLineOptions: CommandLineOptions
      get() = CommandLineOptions(
        generateFactories = get(generateDaggerFactoriesKey, false),
        generateFactoriesOnly = get(generateDaggerFactoriesOnlyKey, false),
        generateFactoriesPathWhitelist = get(generateDaggerFactoriesPathWhitelistKey, emptyList()),
        disableComponentMerging = get(disableComponentMergingKey, false)
      )
  }
}
