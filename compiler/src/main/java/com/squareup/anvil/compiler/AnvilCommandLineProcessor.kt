package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal const val srcGenDirName = "src-gen-dir"
internal val srcGenDirKey = CompilerConfigurationKey.create<String>("anvil $srcGenDirName")

internal const val generateDaggerFactoriesName = "generate-dagger-factories"
internal val generateDaggerFactoriesKey =
  CompilerConfigurationKey.create<Boolean>("anvil $generateDaggerFactoriesName")

internal const val generateDaggerFactoriesOnlyName = "generate-dagger-factories-only"
internal val generateDaggerFactoriesOnlyKey =
  CompilerConfigurationKey.create<Boolean>("anvil $generateDaggerFactoriesOnlyName")

internal const val disableComponentMergingName = "disable-component-merging"
internal val disableComponentMergingKey =
  CompilerConfigurationKey.create<Boolean>("anvil $disableComponentMergingName")

/**
 * Parses arguments from the Gradle plugin for the compiler plugin.
 */
@AutoService(CommandLineProcessor::class)
class AnvilCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.anvil.compiler"

  override val pluginOptions: Collection<AbstractCliOption> = listOf(
    CliOption(
      optionName = srcGenDirName,
      valueDescription = "<file-path>",
      description = "Path to directory in which Anvil specific code should be generated",
      required = true,
      allowMultipleOccurrences = false
    ),
    CliOption(
      optionName = generateDaggerFactoriesName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate Factory classes that the Dagger " +
        "annotation processor would generate for @Provides methods and @Inject " +
        "constructors.",
      required = false,
      allowMultipleOccurrences = false
    ),
    CliOption(
      optionName = generateDaggerFactoriesOnlyName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate Factory classes only and no code for " +
        "contributed code.",
      required = false,
      allowMultipleOccurrences = false
    ),
    CliOption(
      optionName = disableComponentMergingName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate code only and not transform code for " +
        "@MergeComponent or @MergeSubcomponent.",
      required = false,
      allowMultipleOccurrences = false
    )
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration
  ) {
    when (option.optionName) {
      srcGenDirName -> configuration.put(srcGenDirKey, value)
      generateDaggerFactoriesName ->
        configuration.put(generateDaggerFactoriesKey, value.toBoolean())
      generateDaggerFactoriesOnlyName ->
        configuration.put(generateDaggerFactoriesOnlyKey, value.toBoolean())
      disableComponentMergingName ->
        configuration.put(disableComponentMergingKey, value.toBoolean())
    }
  }
}
