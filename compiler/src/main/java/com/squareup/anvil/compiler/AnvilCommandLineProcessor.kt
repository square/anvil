package com.squareup.anvil.compiler

import com.google.auto.service.AutoService
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

internal const val gradleBuildDirName = "gradle-build-dir"
internal val gradleBuildDirKey =
  CompilerConfigurationKey.create<File>("anvil $gradleBuildDirName")

internal const val irMergesFileName = "ir-merges-file"
internal val irMergesFileKey =
  CompilerConfigurationKey.create<File>("anvil $irMergesFileName")

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

internal const val willHaveDaggerFactoriesName = "will-have-dagger-factories"
internal val willHaveDaggerFactoriesKey =
  CompilerConfigurationKey.create<Boolean>("anvil $willHaveDaggerFactoriesName")

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
      optionName = gradleBuildDirName,
      valueDescription = "<file-path>",
      description = "The build directory of the consuming project",
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
      optionName = irMergesFileName,
      valueDescription = "<file-path>",
      description = "Path of the file where Anvil records its merged module annotations and component/module interfaces",
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
      optionName = willHaveDaggerFactoriesName,
      valueDescription = "<true|false>",
      description = "Whether Anvil should expect that Dagger's Factory models will be generated " +
        "by the end of compilation, from Anvil itself or from Dagger's generators.",
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
      gradleBuildDirName -> configuration.put(gradleBuildDirKey, File(value))
      srcGenDirName -> configuration.put(srcGenDirKey, File(value))
      anvilCacheDirName -> configuration.put(anvilCacheDirKey, File(value))
      irMergesFileName -> configuration.put(irMergesFileKey, File(value))
      generateDaggerFactoriesName ->
        configuration.put(generateDaggerFactoriesKey, value.toBoolean())

      generateDaggerFactoriesOnlyName ->
        configuration.put(generateDaggerFactoriesOnlyKey, value.toBoolean())

      disableComponentMergingName ->
        configuration.put(disableComponentMergingKey, value.toBoolean())

      willHaveDaggerFactoriesName ->
        configuration.put(willHaveDaggerFactoriesKey, value.toBoolean())

      trackSourceFilesName ->
        configuration.put(trackSourceFilesKey, value.toBoolean())
    }
  }
}
