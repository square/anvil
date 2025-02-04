package com.squareup.anvil.compiler.testing.compilation

import org.jetbrains.kotlin.kapt.cli.CliToolOption
import org.jetbrains.kotlin.kapt.cli.KaptCliOption
import org.jetbrains.kotlin.kapt3.base.KaptFlag
import org.jetbrains.kotlin.kapt3.base.KaptOptions

internal fun KaptOptions.Builder.toPluginOptions(): List<String> {
  val options = mutableListOf<String>()

  data class Option(val cliToolOption: CliToolOption, val pluginOption: KaptCliOption)

  val cliOptions = KaptCliOption.entries
    .mapNotNull { Option(it.cliToolOption ?: return@mapNotNull null, it) }

  for (option in KaptCliOption.entries) {
    fun Any.pluginOption(value: String = this.toString()) {
      options +=
        "plugin:${KaptCliOption.ANNOTATION_PROCESSING_COMPILER_PLUGIN_ID}:${option.optionName}=$value"
    }

    fun KaptFlag.pluginFlag() {
      (this in flags).pluginOption()
    }

    @Suppress(
      "ktlint:standard:annotation",
      "ktlint:standard:trailing-comma-on-declaration-site",
    )
    when (option) {
      KaptCliOption.SOURCE_OUTPUT_DIR_OPTION -> sourcesOutputDir?.pluginOption()
      KaptCliOption.CLASS_OUTPUT_DIR_OPTION -> classesOutputDir?.pluginOption()
      KaptCliOption.STUBS_OUTPUT_DIR_OPTION -> stubsOutputDir?.pluginOption()
      KaptCliOption.INCREMENTAL_DATA_OUTPUT_DIR_OPTION -> incrementalDataOutputDir?.pluginOption()

      KaptCliOption.CHANGED_FILES -> {
        for (file in changedFiles) {
          file.pluginOption()
        }
      }

      KaptCliOption.COMPILED_SOURCES_DIR -> compiledSources.takeIf { it.isNotEmpty() }
        ?.pathStrings()?.pluginOption()

      KaptCliOption.INCREMENTAL_CACHE -> incrementalCache?.pluginOption()
      KaptCliOption.CLASSPATH_CHANGES -> {
        for (change in classpathChanges) {
          change.pluginOption()
        }
      }

      KaptCliOption.PROCESS_INCREMENTALLY -> KaptFlag.INCREMENTAL_APT.pluginFlag()

      KaptCliOption.ANNOTATION_PROCESSOR_CLASSPATH_OPTION -> {
        for (path in processingClasspath) {
          path.pluginOption()
        }
      }

      KaptCliOption.ANNOTATION_PROCESSORS_OPTION ->
        processors
          .map(String::trim)
          .filterNot(String::isEmpty)
          .joinToString(",")
          .pluginOption()

      KaptCliOption.APT_OPTION_OPTION -> {
        for ((k, v) in processingOptions) {
          "$k=$v".pluginOption()
        }
      }

      KaptCliOption.JAVAC_OPTION_OPTION -> {
        for ((k, v) in javacOptions) {
          "$k=$v".pluginOption()
        }
      }

      KaptCliOption.VERBOSE_MODE_OPTION -> KaptFlag.VERBOSE.pluginFlag()
      KaptCliOption.USE_LIGHT_ANALYSIS_OPTION -> KaptFlag.USE_LIGHT_ANALYSIS.pluginFlag()
      KaptCliOption.CORRECT_ERROR_TYPES_OPTION -> KaptFlag.CORRECT_ERROR_TYPES.pluginFlag()
      KaptCliOption.DUMP_DEFAULT_PARAMETER_VALUES -> KaptFlag.DUMP_DEFAULT_PARAMETER_VALUES.pluginFlag()
      KaptCliOption.MAP_DIAGNOSTIC_LOCATIONS_OPTION -> KaptFlag.MAP_DIAGNOSTIC_LOCATIONS.pluginFlag()
      KaptCliOption.INFO_AS_WARNINGS_OPTION -> KaptFlag.INFO_AS_WARNINGS.pluginFlag()
      KaptCliOption.STRICT_MODE_OPTION -> KaptFlag.STRICT.pluginFlag()
      KaptCliOption.STRIP_METADATA_OPTION -> KaptFlag.STRIP_METADATA.pluginFlag()
      KaptCliOption.KEEP_KDOC_COMMENTS_IN_STUBS -> KaptFlag.KEEP_KDOC_COMMENTS_IN_STUBS.pluginFlag()

      KaptCliOption.SHOW_PROCESSOR_STATS -> KaptFlag.SHOW_PROCESSOR_STATS.pluginFlag()
      KaptCliOption.DUMP_PROCESSOR_STATS -> processorsStatsReportFile?.pluginOption()
      KaptCliOption.DUMP_FILE_READ_HISTORY -> fileReadHistoryReportFile?.pluginOption()
      KaptCliOption.INCLUDE_COMPILE_CLASSPATH -> KaptFlag.INCLUDE_COMPILE_CLASSPATH.pluginFlag()

      KaptCliOption.DETECT_MEMORY_LEAKS_OPTION -> detectMemoryLeaks.stringValue.pluginOption()
      KaptCliOption.APT_MODE_OPTION -> mode.stringValue.pluginOption()

      // As of Kotlin 2.0.21, this cannot be set in the KaptOptions class.  If it's passed as a string argument,
      // the Kapt plugin CLI processor ignores it, and the standalone CLI just converts it to the `-Xuse-k2-kapt` flag.
      // https://github.com/JetBrains/kotlin/blob/d6c2591b13262378ab7fd03d618e3bfcfa198765/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/Kapt3Plugin.kt#L126
      // https://github.com/JetBrains/kotlin/blob/0208bba813e9b861405c048b9f4a9fa25a2fc046/plugins/kapt3/kapt3-cli/src/KaptCli.kt#L110
      KaptCliOption.USE_K2 -> Unit

      // unsupported and impossible to set
      KaptCliOption.TOOLS_JAR_OPTION -> Unit

      @Suppress("DEPRECATION")
      KaptCliOption.CONFIGURATION -> Unit

      @Suppress("DEPRECATION")
      KaptCliOption.APT_OPTIONS_OPTION -> Unit

      @Suppress("DEPRECATION")
      KaptCliOption.JAVAC_CLI_OPTIONS_OPTION -> Unit

      else -> {
        println("Unknown option: $option")
      }
    }
  }

  return options
}
