package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.stdlib.letIf
import dagger.internal.codegen.ComponentProcessor
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.copyOf
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.incremental.isJavaFile
import org.jetbrains.kotlin.kapt3.base.AptMode
import org.jetbrains.kotlin.kapt3.base.DetectMemoryLeaksMode
import org.jetbrains.kotlin.kapt3.base.KaptFlag
import org.jetbrains.kotlin.kapt3.base.KaptOptions
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.net.URI
import java.net.URLClassLoader
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

public class Compile2Compilation(
  public val config: Compile2CompilationConfiguration,
  public val expectedExitCode: ExitCode,
) {

  private val messageRenderer by lazy { ColorizedPlainTextMessageRenderer() }

  internal fun execute(): Compile2Result {

    val baseArgs = K2JVMCompilerArguments().also { args ->
      args.reportPerf = true

      args.moduleName = "root"
      args.additionalJavaModules = emptyArray()

      // args.enableDebugMode = true
      // args.noJdk = args.jdkHome == null
      // args.reportAllWarnings = true
      // args.reportPerf = true

      args.jdkHome = javaHome().absolutePath
      args.languageVersion = config.languageVersion.versionString

      args.compileJava = true
      args.useJavac = true

      args.verbose = config.verbose
      args.version = config.verbose

      args.allowNoSourceFiles = config.sourceFiles.isEmpty()

      args.noStdlib = true
      args.noReflect = true
      args.disableStandardScript = true

      args.destination = config.classFilesDir.absolutePath

      args.classpath = config.compilationClasspath.classpathString()

      args.pluginClasspaths = config.compilerPluginClasspath
        .pathStrings()
        .toTypedArray()

      args.freeArgs += config.sourceFiles.pathStrings()
    }

    // sanity check to ensure the arguments translate properly
    parseCommandLineArguments<K2JVMCompilerArguments>(baseArgs.toArgumentStrings())

    Compile2CompilerPluginRegistrar.threadLocalParams.set(
      Compile2CompilerPluginRegistrar.Compile2RegistrarParams(firExtensions = listOf()),
    )

    if (config.useKapt) {
      executeKapt(baseArgs)
    }

    val allSources = config.sourceFiles
      .plus(config.kaptGeneratedSourcesDir.walkBottomUp().filter { it.isJavaFile() })
      .distinct()

    val secondArgs = baseArgs.copyOf().also { args ->

      args.freeArgs = allSources.pathStrings()
      args.useJavac = true
    }
      .toArgumentStrings()

    val exitCode = K2JVMCompiler().exec(
      errStream = System.out,
      messageRenderer = messageRenderer,
      args = secondArgs.toTypedArray(),
    )
    exitCode shouldBe expectedExitCode

    val javaSources = allSources.filter { it.isJavaFile() }

    if (javaSources.isNotEmpty()) {
      compileJava(javaSources)
    }

    val classLoader = URLClassLoader.newInstance(
      config.compilationClasspath
        .plus(config.classFilesDir)
        .map { it.toURI().toURL() }.toTypedArray(),
    )

    return Compile2Result(
      libsDir = config.jarOutputDir,
      classFilesDir = config.classFilesDir,
      classpathFiles = config.compilationClasspath,
      exitCode = exitCode,
      classLoader = classLoader,
    ).apply {

      exitCode shouldBe expectedExitCode
    }
  }

  private fun compileJava(javaSources: List<File>) {
    val javac = synchronized(ToolProvider::class.java) {
      ToolProvider.getSystemJavaCompiler()
    } ?: error("No system Java compiler found.")

    val javaFileManager = javac.getStandardFileManager(null, null, null)
    val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

    val internalMessageStream = PrintStream(System.out)

    class AnvilSimpleJavaFileObject(uri: URI) : SimpleJavaFileObject(
      uri,
      JavaFileObject.Kind.SOURCE,
    ) {

      override fun openInputStream(): InputStream = uri.toURL().openStream()

      override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
        uri.toURL().readText()
    }

    val javacArgs = buildList {
      if (config.verbose) {
        this.add("-verbose")
        this.add("-Xlint:path") // warn about invalid paths in CLI
        this.add("-Xlint:options") // warn about invalid options in CLI
        this.add("-Xlint:module") // warn about issues with the module system
      }

      this.addAll(listOf("-d", config.classFilesDir.absolutePath))

      this.add("-proc:none") // disable annotation processing

      if (config.allWarningsAsErrors) this.add("-Werror")

      // also add class output path to javac classpath so it can discover already compiled Kotlin
      // classes
      this.addAll(listOf("-cp", (config.compilationClasspath + config.classFilesDir).classpathString()))
    }

    val javacSuccess = javac.getTask(
      OutputStreamWriter(internalMessageStream),
      javaFileManager,
      diagnosticCollector,
      javacArgs,
      /* classes */
      null,
      javaSources.map { AnvilSimpleJavaFileObject(it.toURI()) },
    )
      .call()

    diagnosticCollector.diagnostics.forEach { diag ->
      when (diag.kind) {
        Diagnostic.Kind.ERROR -> error(diag.toString())
        Diagnostic.Kind.WARNING,
        Diagnostic.Kind.MANDATORY_WARNING,
        -> System.err.println(diag.toString())

        else -> println(diag.toString())
      }
    }

    val javaExitCode = if (javacSuccess) ExitCode.OK else ExitCode.COMPILATION_ERROR
    javaExitCode shouldBe expectedExitCode
  }

  private fun executeKapt(baseArgs: K2JVMCompilerArguments) {

    val kaptPassArgs = baseArgs.copyOf().also { args ->
      args.freeArgs = config.sourceFiles.pathStrings()
      args.useJavac = false

      args.useK2Kapt = true

      val processingClasspath = listOf(
        HostClasspath.daggerCompiler,
        HostClasspath.jakartaInject,
        HostClasspath.javaxInject,
        HostClasspath.jetbrainsAnnotations,
      )

      val kaptOptions = KaptOptions.Builder().also { kapt ->
        kapt.detectMemoryLeaks = DetectMemoryLeaksMode.DEFAULT
        kapt.processors.add(ComponentProcessor::class.qualifiedName!!)
        kapt.classesOutputDir = config.kaptClassesDir.absoluteFile
        kapt.incrementalDataOutputDir = config.kaptIncrementalDir.absoluteFile
        kapt.processingClasspath += processingClasspath
        kapt.sourcesOutputDir = config.kaptGeneratedSourcesDir.absoluteFile
        kapt.stubsOutputDir = config.kaptStubsDir.absoluteFile
        kapt.mode = AptMode.STUBS_AND_APT

        kapt.flags.addAll(
          listOf(
            KaptFlag.USE_LIGHT_ANALYSIS,
            KaptFlag.INCLUDE_COMPILE_CLASSPATH,
          ),
        )

        if (config.verbose) {
          kapt.flags.add(KaptFlag.VERBOSE)
        }
      }

      args.pluginOptions = kaptOptions.toPluginOptions()
        .toTypedArray()
    }

    val exitCode = K2JVMCompiler().exec(
      errStream = System.out,
      messageRenderer = messageRenderer,
      args = kaptPassArgs.toArgumentStrings().toTypedArray(),
    )
    exitCode shouldBe expectedExitCode
  }
}

public data class Compile2CompilationConfiguration(
  val sourceFiles: List<File>,
  val useKapt: Boolean,
  val verbose: Boolean,
  val allWarningsAsErrors: Boolean,
  val jarOutputDir: File,
  val classFilesDir: File,
  val kaptStubsDir: File,
  val kaptGeneratedSourcesDir: File,
  val kaptIncrementalDir: File,
  val kaptClassesDir: File,
  val languageVersion: LanguageVersion = LanguageVersion.KOTLIN_2_0,
  val compilationClasspath: List<File> = listOf(
    HostClasspath.jakartaInject,
    HostClasspath.javaxInject,
    HostClasspath.dagger,
    HostClasspath.kotlinStdLib,
    HostClasspath.jetbrainsAnnotations,
    HostClasspath.anvilAnnotations,
  ),
  val compilerPluginClasspath: List<File> = compilationClasspath +
    HostClasspath.projectClassFileDirectories +
    HostClasspath.allInheritedAnvilProjects + listOf(
      HostClasspath.jakartaInject,
      HostClasspath.javaxInject,
      HostClasspath.javaxJsr250Api,
      HostClasspath.kotlinCompilerEmbeddable,
    )
      .letIf(useKapt) { it + HostClasspath.kotlinAnnotationProcessingEmbeddable }
      .distinct(),
  val kaptPluginClasspath: List<File> = listOf(
    HostClasspath.jakartaInject,
    HostClasspath.javaxInject,
    HostClasspath.dagger,
    HostClasspath.kotlinStdLib,
    HostClasspath.jetbrainsAnnotations,
    HostClasspath.anvilAnnotations,
  ),
)
