package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnalysisBackend
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

internal const val srcGenDirName = "src-gen-dir"
internal val srcGenDirKey = CompilerConfigurationKey.create<File>("anvil $srcGenDirName")

internal const val anvilCacheDirName = "anvil-cache-dir"
internal val anvilCacheDirKey =
  CompilerConfigurationKey.create<File>("anvil $anvilCacheDirName")

internal const val gradleProjectDirName = "gradle-project-dir"
internal val gradleProjectDirKey =
  CompilerConfigurationKey.create<File>("anvil $gradleProjectDirName")

internal const val generateDaggerFactoriesName = "generate-dagger-factories"
internal val generateDaggerFactoriesKey =
  CompilerConfigurationKey.create<Boolean>("anvil $generateDaggerFactoriesName")

internal const val generateDaggerFactoriesOnlyName = "generate-dagger-factories-only"
internal val generateDaggerFactoriesOnlyKey =
  CompilerConfigurationKey.create<Boolean>("anvil $generateDaggerFactoriesOnlyName")

internal const val disableComponentMergingName = "disable-component-merging"
internal val disableComponentMergingKey =
  CompilerConfigurationKey.create<Boolean>("anvil $disableComponentMergingName")

internal const val trackSourceFilesName = "track-source-files"
internal val trackSourceFilesKey =
  CompilerConfigurationKey.create<Boolean>("anvil $trackSourceFilesName")

internal const val analysisBackendName = "analysis-backend"
internal val analysisBackendKey =
  CompilerConfigurationKey.create<String>("anvil $analysisBackendName")

internal const val mergingBackendName = "merging-backend"
internal val mergingBackendKey =
  CompilerConfigurationKey.create<String>("anvil $mergingBackendName")

/**
 * Parses arguments from the Gradle plugin for the compiler plugin.
 */
@AutoService(CommandLineProcessor::class)
public class AnvilCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "com.squareup.anvil.compiler"

  override val pluginOptions: Collection<AbstractCliOption> = listOf(
    CliOption(
      optionName = gradleProjectDirName,
      valueDescription = "<file-path>",
      description = "The root directory of the consuming project",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = srcGenDirName,
      valueDescription = "<file-path>",
      description = "Path to directory in which Anvil specific code should be generated",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = anvilCacheDirName,
      valueDescription = "<file-path>",
      description = "Path to directory where Anvil stores its incremental compilation state",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = generateDaggerFactoriesName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate Factory classes that the Dagger " +
        "annotation processor would generate for @Provides methods and @Inject " +
        "constructors.",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = generateDaggerFactoriesOnlyName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate Factory classes only and no code for " +
        "contributed code.",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = disableComponentMergingName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should generate code only and not transform code for " +
        "@MergeComponent or @MergeSubcomponent.",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = trackSourceFilesName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should track its experimental generated " +
        "file caching and invalidation",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = analysisBackendName,
      valueDescription = AnalysisBackend.entries.joinToString("|", "<", ">"),
      description = "Controls whether Anvil analysis is running as an embedded plugin or as KSP.",
      required = false,
      allowMultipleOccurrences = false,
    ),
    CliOption(
      optionName = mergingBackendName,
      valueDescription = ComponentMergingBackend.entries.joinToString("|", "<", ">"),
      description = "Controls whether module merging is running as an IR plugin or as KSP.",
      required = false,
      allowMultipleOccurrences = false,
    ),
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {

      gradleProjectDirName -> configuration.put(gradleProjectDirKey, File(value))
      srcGenDirName -> configuration.put(srcGenDirKey, File(value))
      anvilCacheDirName -> configuration.put(anvilCacheDirKey, File(value))
      generateDaggerFactoriesName ->
        configuration.put(generateDaggerFactoriesKey, value.toBoolean())

      generateDaggerFactoriesOnlyName ->
        configuration.put(generateDaggerFactoriesOnlyKey, value.toBoolean())

      disableComponentMergingName ->
        configuration.put(disableComponentMergingKey, value.toBoolean())

      trackSourceFilesName ->
        configuration.put(trackSourceFilesKey, value.toBoolean())

      analysisBackendName -> configuration.put(analysisBackendKey, value)
    }
  }
}
