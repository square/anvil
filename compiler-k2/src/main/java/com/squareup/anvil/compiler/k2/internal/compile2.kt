package com.squareup.anvil.compiler.k2.internal

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.AnvilFirExtensionRegistrar
import com.squareup.anvil.compiler.k2.CanaryIrMerger
import dagger.internal.codegen.ComponentProcessor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.PlainTextMessageRenderer
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.incremental.isJavaFile
import org.jetbrains.kotlin.incremental.isKotlinFile
import org.jetbrains.kotlin.kapt3.KAPT_OPTIONS
import org.jetbrains.kotlin.kapt3.base.AptMode
import org.jetbrains.kotlin.kapt3.base.DetectMemoryLeaksMode
import org.jetbrains.kotlin.kapt3.base.KaptFlag
import org.jetbrains.kotlin.kapt3.base.KaptOptions
import org.jetbrains.kotlin.kapt4.Kapt4CompilerPluginRegistrar
import java.io.File

internal fun compile2(
  workingDir: File,
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
    args.jvmTarget = "17"
    args.languageVersion = "2.0"
    // args.reportOutputFiles = true
    // args.suppressMissingBuiltinsError = true
    args.verbose = true
    args.version = true
    args.noStdlib = true
    args.noReflect = true
    args.allowNoSourceFiles = true

    args.destination = workingDir.resolve("kotlin/classes").absolutePath

    args.freeArgs += "-Xuse-kapt4"

    args.freeArgs += sourceFiles.pathStrings()

    args.pluginOptions = kaptOptions(workingDir).toPluginOptions()
      .plus("plugin:org.jetbrains.kotlin.kapt3:useK2=true")
      .toTypedArray()

    args.classpath = listOf(
      HostEnvironment.jakartaInject,
      HostEnvironment.javaxInject,
      HostEnvironment.dagger,
      HostEnvironment.kotlinStdLib,
      HostEnvironment.jetbrainsAnnotations,
      HostEnvironment.anvilCompiler,
      HostEnvironment.anvilAnnotations,
    )
      .pathStrings()
      .joinToString(File.pathSeparator)

    args.pluginClasspaths = listOf(
      HostEnvironment.kotlinAnnotationProcessingEmbeddable,
      HostEnvironment.javaxInject,
      HostEnvironment.daggerCompiler,
      HostEnvironment.anvilCompiler,
      HostEnvironment.anvilCompilerApi,
      HostEnvironment.anvilCompilerUtils,
      HostEnvironment.anvilAnnotations,
    )
      .pathStrings()
      .toTypedArray()
  }
    .let { parseCommandLineArguments<K2JVMCompilerArguments>(it.toArgumentStrings()) }

  val messageRenderer = ColorizedPlainTextMessageRenderer()

  val exitCode = K2JVMCompiler().exec(
    errStream = System.err,
    messageRenderer = messageRenderer,
    args = k2JvmArgs.toArgumentStrings().toTypedArray(),
  )
  return exitCode == ExitCode.OK
}

public class ColorizedPlainTextMessageRenderer : PlainTextMessageRenderer(true) {
  private val cwd by lazy(LazyThreadSafetyMode.NONE) { File(".").absoluteFile }

  private val _errors = mutableListOf<String>()
  public val errors: List<String> get() = _errors

  override fun getName(): String = "ColorizedRelativePath"

  override fun render(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ): String = super.render(severity, message, location)
    .also {
      if (severity == CompilerMessageSeverity.ERROR) {
        _errors += it
      }
      println("~~~  $it")
    }

  override fun getPath(location: CompilerMessageSourceLocation): String {
    return File(location.path).toRelativeString(cwd)
  }
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
      // HostEnvironment.kotlinStdLibJdkJar,
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
      // KaptFlag.CORRECT_ERROR_TYPES,
      KaptFlag.INCLUDE_COMPILE_CLASSPATH,
    ),
  )
}

internal fun createCompilerConfiguration(
  workingDir: File,
  compilerClasspathFiles: List<File>,
  sourceFiles: List<File>,
  moduleName: String,
  messageCollector: MessageCollector,
  kotlinLanguageVersion: LanguageVersion,
  jvmTarget: JvmTarget,
): CompilerConfiguration {

  val javaFiles = mutableListOf<File>()
  val kotlinFiles = mutableListOf<String>()

  sourceFiles.forEach { file ->
    when {
      file.isKotlinFile(listOf("kt")) -> kotlinFiles.add(file.absolutePath)
      file.isJavaFile() -> javaFiles.add(file)
    }
  }

  val kaptOptions = kaptOptions(workingDir)

  return CompilerConfiguration().also { config ->
    config.put(KAPT_OPTIONS, kaptOptions)

    config.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
    config.put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
    config.put(CommonConfigurationKeys.MODULE_NAME, moduleName)

    val languageVersionSettings = LanguageVersionSettingsImpl(
      languageVersion = kotlinLanguageVersion,
      apiVersion = ApiVersion.createByLanguageVersion(kotlinLanguageVersion),
    )
    config.put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)

    config.addJavaSourceRoots(javaFiles)
    config.addKotlinSourceRoots(kotlinFiles)
    config.addJvmClasspathRoots(compilerClasspathFiles)
    config.configureJdkClasspathRoots()
  }
}

@AutoService(CompilerPluginRegistrar::class)
internal class CanaryCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    FirExtensionRegistrarAdapter.registerExtension(AnvilFirExtensionRegistrar())

    IrGenerationExtension.registerExtension(CanaryIrMerger())

    with(Kapt4CompilerPluginRegistrar()) {
      registerExtensions(configuration)
    }
  }
}

internal fun javaHome(): File = javaHomeOrNull() ?: error("JAVA_HOME and 'java.home' not set")

internal fun javaHomeOrNull(): File? {
  val path = System.getProperty("java.home")
    ?: System.getenv("JAVA_HOME")
    ?: return null

  return File(path).also { check(it.isDirectory) }
}

public fun Iterable<File>.pathStrings(): List<String> = map { it.absolutePath }.distinct()
