package com.squareup.hephaestus.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal const val srcGenDirName = "src-gen-dir"
internal val srcGenDirKey = CompilerConfigurationKey.create<String>("hephaestus $srcGenDirName")

internal const val skipAnalysisName = "skip-analysis"
internal val skipAnalysisKey = CompilerConfigurationKey.create<Boolean>("hephaestus $skipAnalysisName")

/**
 * Parses arguments from the Gradle plugin for the compiler plugin.
 */
@AutoService(CommandLineProcessor::class)
class HephaestusCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.hephaestus.compiler"

  override val pluginOptions: Collection<AbstractCliOption> = listOf(
      CliOption(
          optionName = srcGenDirName,
          valueDescription = "<file-path>",
          description = "Path to directory in which Hephaestus specific code should be generated",
          required = true,
          allowMultipleOccurrences = false
      ),
      CliOption(
          optionName = skipAnalysisName,
          valueDescription = "<true|false>",
          description = "Whether this plugin should skip the analysis phase. This happens during " +
              "stub generation automatically and this option is helpful to test this scenario.",
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
      skipAnalysisName -> configuration.put(skipAnalysisKey, value.toBoolean())
    }
  }
}
