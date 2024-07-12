package com.squareup.anvil.compiler.fir.internal

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.fir.AnvilFirExtensionRegistrar
import com.squareup.anvil.compiler.fir.CanaryIrMerger
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
//      HostEnvironment.kotlinAnnotationProcessingEmbeddable,
      HostEnvironment.javaxInject,
//      HostEnvironment.daggerCompiler,
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

// return   execNoCLI(workingDir, sourceFiles, k2JvmArgs)
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
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger-compiler/2.52/ff418f410326eba248f5540a64c4a1e9c45cf570/dagger-compiler-2.52.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger-spi/2.52/50674112d6ffdf0c77ba383dd789216f8c35a995/dagger-spi-2.52.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.dagger/dagger/2.52/c385ea51a9873b238d183faa22d54c00d65195ac/dagger-2.52.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.googlejavaformat/google-java-format/1.5/fba7f130d29061d2d2ea384b4880c10cae92ef73/google-java-format-1.5.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/33.0.0-jre/161ba27964a62f241533807a46b8711b13c1d94b/guava-33.0.0-jre.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.code.findbugs/jsr305/3.0.2/25ea2e8b0c338a877313bd4672d3fe056ea78f0d/jsr305-3.0.2.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.guava/failureaccess/1.0.2/c4a06a64e650562f30b7bf9aaec1bfed43aca12b/failureaccess-1.0.2.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.squareup/javapoet/1.13.0/d6562d385049f35eb50403fa86bb11cce76b866a/javapoet-1.13.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.squareup/kotlinpoet/1.11.0/5a16322632c6361f7058c948bab1aafa6e7a337f/kotlinpoet-1.11.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/javax.inject/javax.inject/1/6975da39a7040257bd51d21a231b76c915872d38/javax.inject-1.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/net.ltgt.gradle.incap/incap/0.2/c73e3db9bee414d6ee27995d951fcdbee09acad/incap-0.2.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.checkerframework/checker-compat-qual/2.5.5/435dc33e3019c9f019e15f01aa111de9d6b2b79c/checker-compat-qual-2.5.5.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.9.0/e000bd084353d84c9e888f6fb341dc1f5b79d948/kotlin-stdlib-jdk8-1.9.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.6.10/1cbe9c92c12a94eea200d23c2bbaedaf3daf5132/kotlin-reflect-1.6.10.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.9.0/f320478990d05e0cfaadd74f9619fd6027adbf37/kotlin-stdlib-jdk7-1.9.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.24/9928532f12c66ad816a625b3f9984f8368ca6d2b/kotlin-stdlib-1.9.24.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/jakarta.inject/jakarta.inject-api/2.0.1/4c28afe1991a941d7702fe1362c365f0a8641d1e/jakarta.inject-api-2.0.1.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.errorprone/javac-shaded/9-dev-r4023-3/72b688efd290280a0afde5f9892b0fde6f362d1d/javac-shaded-9-dev-r4023-3.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/b421526c5f297295adef1c886e5246c39d4ac629/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.checkerframework/checker-qual/3.41.0/8be6df7f1e9bccb19f8f351b3651f0bac2f5e0c/checker-qual-3.41.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/com.google.errorprone/error_prone_annotations/2.23.0/43a27853b6c7d54893e0b1997c2c778c347179eb/error_prone_annotations-2.23.0.jar",
      // "/Users/rbusarow/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar",
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
