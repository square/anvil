package com.squareup.anvil.compiler.testing.compilation

import org.jetbrains.kotlin.kapt.cli.KaptCliOption
import org.jetbrains.kotlin.kapt3.base.KaptFlag
import org.jetbrains.kotlin.kapt3.base.KaptOptions
import java.io.File

internal fun KaptOptions.Builder.toPluginOptions(): List<String> {
  val options = mutableListOf<String>()
  for (option in KaptCliOption.entries) {
    fun Any.pluginOption(value: String = this.toString()) {
      options +=
        "plugin:${KaptCliOption.ANNOTATION_PROCESSING_COMPILER_PLUGIN_ID}:${option.optionName}=$value"
    }

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
        ?.joinToString(File.pathSeparator)?.pluginOption()
      KaptCliOption.INCREMENTAL_CACHE -> incrementalCache?.pluginOption()
      KaptCliOption.CLASSPATH_CHANGES -> {
        for (change in classpathChanges) {
          change.pluginOption()
        }
      }
      KaptCliOption.PROCESS_INCREMENTALLY -> (KaptFlag.INCREMENTAL_APT in flags).pluginOption()

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

      KaptCliOption.VERBOSE_MODE_OPTION -> (KaptFlag.VERBOSE in flags).pluginOption()
      KaptCliOption.USE_LIGHT_ANALYSIS_OPTION -> (KaptFlag.USE_LIGHT_ANALYSIS in flags).pluginOption()
      KaptCliOption.CORRECT_ERROR_TYPES_OPTION -> (KaptFlag.CORRECT_ERROR_TYPES in flags).pluginOption()
      KaptCliOption.DUMP_DEFAULT_PARAMETER_VALUES ->
        (KaptFlag.DUMP_DEFAULT_PARAMETER_VALUES in flags).pluginOption()
      KaptCliOption.MAP_DIAGNOSTIC_LOCATIONS_OPTION -> (KaptFlag.MAP_DIAGNOSTIC_LOCATIONS in flags).pluginOption()
      KaptCliOption.INFO_AS_WARNINGS_OPTION -> (KaptFlag.INFO_AS_WARNINGS in flags).pluginOption()
      KaptCliOption.STRICT_MODE_OPTION -> (KaptFlag.STRICT in flags).pluginOption()
      KaptCliOption.STRIP_METADATA_OPTION -> (KaptFlag.STRIP_METADATA in flags).pluginOption()
      KaptCliOption.KEEP_KDOC_COMMENTS_IN_STUBS -> (KaptFlag.KEEP_KDOC_COMMENTS_IN_STUBS in flags).pluginOption()
      KaptCliOption.USE_K2 -> {}

      KaptCliOption.SHOW_PROCESSOR_STATS -> (KaptFlag.SHOW_PROCESSOR_STATS in flags).pluginOption()
      KaptCliOption.DUMP_PROCESSOR_STATS -> processorsStatsReportFile?.pluginOption()
      KaptCliOption.DUMP_FILE_READ_HISTORY -> fileReadHistoryReportFile?.pluginOption()
      KaptCliOption.INCLUDE_COMPILE_CLASSPATH -> (KaptFlag.INCLUDE_COMPILE_CLASSPATH in flags).pluginOption()

      KaptCliOption.DETECT_MEMORY_LEAKS_OPTION -> detectMemoryLeaks.stringValue.pluginOption()
      KaptCliOption.APT_MODE_OPTION -> mode.stringValue.pluginOption()
      @Suppress(
        "DEPRECATION",
        "ktlint:standard:annotation",
        "ktlint:standard:trailing-comma-on-declaration-site",
      )
      KaptCliOption.CONFIGURATION -> {
        /* do nothing */
      }

      else -> {
        println("Unknown option: $option")
      }
    }
  }

  return options
}
