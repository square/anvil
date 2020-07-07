package com.squareup.hephaestus.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal const val srcGenDirName = "src-gen-dir"
internal val srcGenDirKey = CompilerConfigurationKey.create<String>("hephaestus $srcGenDirName")

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
      )
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration
  ) {
    when (option.optionName) {
      srcGenDirName -> configuration.put(srcGenDirKey, value)
    }
  }
}
