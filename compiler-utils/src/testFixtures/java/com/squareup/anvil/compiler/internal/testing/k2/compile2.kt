package com.squareup.anvil.compiler.internal.testing.k2

import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.k2.AnvilFactoryDelegateDeclarationGenerationExtension
import com.squareup.anvil.compiler.k2.AnvilFirSupertypeGenerationExtension
import com.tschuchort.compiletesting.kapt.toPluginOptions
import dagger.internal.codegen.ComponentProcessor
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings
import org.jetbrains.kotlin.kapt.cli.KaptCliOption
import org.jetbrains.kotlin.kapt3.base.AptMode
import org.jetbrains.kotlin.kapt3.base.DetectMemoryLeaksMode
import org.jetbrains.kotlin.kapt3.base.KaptFlag
import org.jetbrains.kotlin.kapt3.base.KaptOptions
import java.io.File

public fun CompilationEnvironment.compile2(
  @Language("kotlin") vararg kotlinSources: String,
): Boolean {
  val files = kotlinSources.mapIndexed { i, kotlin ->
    val packageName = kotlin.substringAfter("package ").substringBefore("\n")
    val packageDir = packageName.replace(".", "/")

    workingDir.resolve("sources/$packageDir/Source$i.kt")
      .createSafely(kotlin)
  }
  return compile2(files)
}

public fun CompilationEnvironment.compile2(
  sourceFiles: List<File>,
): Boolean {

  val k2JvmArgs = K2JVMCompilerArguments().also { args ->

    args.useKapt4 = true
    args.reportPerf = true

    args.moduleName = "root"
    args.additionalJavaModules = emptyArray()

    // args.enableDebugMode = true
    // args.noJdk = args.jdkHome == null
    // args.reportAllWarnings = true
    // args.reportPerf = true

    // args.compileJava = true
    args.jdkHome = javaHome().absolutePath
    args.languageVersion = "2.0"
    args.verbose = true
    args.version = true
    args.noStdlib = true
    args.noReflect = true
    args.allowNoSourceFiles = true

    args.destination = workingDir.resolve("kotlin/classes").absolutePath

    if (mode.useKapt) {
      args.freeArgs += "-Xuse-kapt4"
      args.pluginOptions = kaptOptions(workingDir).toPluginOptions()
        .plus("plugin:org.jetbrains.kotlin.kapt3:useK2=true")
        .toTypedArray()
    }

    args.classpath = listOf(
      HostEnvironment.jakartaInject,
      HostEnvironment.javaxInject,
      HostEnvironment.dagger,
      HostEnvironment.kotlinStdLib,
      HostEnvironment.jetbrainsAnnotations,
      HostEnvironment.anvilAnnotations,
    )
      .joinToString(File.pathSeparator) { it.absolutePath }

    args.pluginClasspaths = HostEnvironment.inheritedClasspath
      .filter { it != HostEnvironment.kotlinAnnotationProcessingEmbeddable || mode.useKapt }
      .letIf(mode.useKapt) {
        it + HostEnvironment.kotlinAnnotationProcessingEmbeddable
      }
      .pathStrings()
      .toTypedArray()

    args.freeArgs += sourceFiles.pathStrings()
  }
    .let { parseCommandLineArguments<K2JVMCompilerArguments>(it.toArgumentStrings()) }

  val messageRenderer = ColorizedPlainTextMessageRenderer()

  val b = ::AnvilFactoryDelegateDeclarationGenerationExtension
  val c = ::AnvilFirSupertypeGenerationExtension

  Compile2CompilerPluginRegistrar.threadLocalParams.set(
    Compile2CompilerPluginRegistrar.Compile2RegistrarParams(
      firExtensions = emptyList(),
    ),
  )

  val exitCode = K2JVMCompiler().exec(
    errStream = System.err,
    messageRenderer = messageRenderer,
    args = k2JvmArgs.toArgumentStrings().toTypedArray(),
  )
  return exitCode == ExitCode.OK
}

private fun kaptOptions(workingDir: File) = KaptOptions.Builder().also { kapt ->
  kapt.detectMemoryLeaks = DetectMemoryLeaksMode.DEFAULT
  kapt.processors.add(ComponentProcessor::class.qualifiedName!!)
  kapt.classesOutputDir = workingDir.resolve("kapt/generated").absoluteFile
  kapt.incrementalDataOutputDir = workingDir.resolve("kapt/generated").absoluteFile
  kapt.processingClasspath.addAll(
    listOf(
      HostEnvironment.kotlinCompilerEmbeddable,
      HostEnvironment.dagger,
      HostEnvironment.daggerCompiler,
      HostEnvironment.kotlinStdLib,
      HostEnvironment.kotlinReflect,
      HostEnvironment.jakartaInject,
      HostEnvironment.javaxInject,
      HostEnvironment.jetbrainsAnnotations,
    ),
  )
  kapt.sourcesOutputDir = workingDir.resolve("kapt/generated").absoluteFile
  kapt.stubsOutputDir = workingDir.resolve("kapt/stubs").absoluteFile
  kapt.mode = AptMode.STUBS_AND_APT

  kapt.flags.addAll(
    listOf(
      KaptFlag.KEEP_KDOC_COMMENTS_IN_STUBS,
      KaptFlag.STRIP_METADATA,
      KaptFlag.USE_LIGHT_ANALYSIS,
      KaptFlag.CORRECT_ERROR_TYPES,
      KaptFlag.INCLUDE_COMPILE_CLASSPATH,
    ),
  )
}

internal fun javaHome(): File = javaHomeOrNull() ?: error("JAVA_HOME and 'java.home' not set")

internal fun javaHomeOrNull(): File? {
  val path = System.getProperty("java.home")
    ?: System.getenv("JAVA_HOME")
    ?: return null

  return File(path).also { check(it.isDirectory) }
}

internal fun Iterable<File>.pathStrings(): List<String> = map { it.absolutePath }.distinct()

internal fun KaptOptions.Builder.toPluginOptions(): List<String> {
  val options = mutableListOf<String>()
  for (option in KaptCliOption.entries) {
    fun Any.pluginOption(value: String = this.toString()) {
      options +=
        "plugin:${KaptCliOption.ANNOTATION_PROCESSING_COMPILER_PLUGIN_ID}:${option.optionName}=$value"
    }

    @Suppress("DEPRECATION")
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
      KaptCliOption.DUMP_DEFAULT_PARAMETER_VALUES -> (
        KaptFlag.DUMP_DEFAULT_PARAMETER_VALUES in
          flags
        ).pluginOption()
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
