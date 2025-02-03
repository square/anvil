package com.squareup.anvil.compiler.testing.compilation

import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.testing.CompilationEnvironment
import com.squareup.anvil.compiler.testing.kotlinVersion
import dagger.internal.codegen.ComponentProcessor
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
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

public fun CompilationEnvironment.compile2(
  @Language("kotlin") vararg kotlinSources: String,
  javaSources: List<String> = emptyList(),
  expectedExitCode: ExitCode = ExitCode.OK,
  firExtensions: List<AnvilFirExtensionFactory<*>> = emptyList(),
  exec: Compile2Result.() -> Unit = {},
): Compile2Result {
  val kotlinFiles = kotlinSources.mapIndexed { i, kotlinNotTrimmed ->
    val kotlin = kotlinNotTrimmed.trimIndent()
    val packageName = kotlin.substringAfter("package ").substringBefore("\n").trim()
    val packageDir = packageName.replace(".", "/")

    workingDir.resolve("sources/$packageDir/Source$i.kt")
      .createSafely(kotlin)
  }

  val javaFiles = javaSources.map { java ->
    val packageName = java.substringAfter("package ").substringBefore(";").trim()
    val packageDir = packageName.replace(".", "/")

    val reg = """^[^/]*(?:class|interface|enum)[\t ]+(\S+)""".toRegex()
    val className = java.lineSequence().firstNotNullOf { line ->
      reg.find(line)?.groupValues?.get(1)
    }

    workingDir.resolve("sources/$packageDir/$className.java")
      .createSafely(java)
  }

  return compile2(
    sourceFiles = kotlinFiles + javaFiles,
    expectedExitCode = expectedExitCode,
    firExtensions = firExtensions,
    exec = exec,
  )
}

public fun CompilationEnvironment.compile2(
  sourceFiles: List<File>,
  languageVersion: LanguageVersion = LanguageVersion.KOTLIN_2_0,
  expectedExitCode: ExitCode = ExitCode.OK,
  firExtensions: List<AnvilFirExtensionFactory<*>> = emptyList(),
  exec: Compile2Result.() -> Unit = {},
): Compile2Result {

  val kaptDir = workingDir / "kapt"

  val config = Compile2CompilationConfiguration(
    rootDir = workingDir,
    sourceFiles = sourceFiles,
    useKapt = mode.useKapt,
    verbose = false,
    allWarningsAsErrors = true,
    jarOutputDir = workingDir / "libs",
    classFilesDir = workingDir / "kotlin/classes",
    kaptStubsDir = kaptDir / "stubs",
    kaptGeneratedSourcesDir = kaptDir / "generated",
    kaptIncrementalDir = kaptDir / "incremental",
    kaptClassesDir = kaptDir / "classes",
    languageVersion = languageVersion,
    firExtensions = firExtensions,
  )

  val compilation = Compile2Compilation(config, expectedExitCode)

  return compilation.execute().also { it.exec() }
}

/**
 * Represents a single compiler invocation that can handle Kotlin and Java compilation, as well as
 * an optional KAPT pass. Used primarily by [CompilationEnvironment.compile2].
 *
 * @property config The [Compile2CompilationConfiguration] containing file paths, classpaths, and various optional flags.
 * @property expectedExitCode The exit code this compilation expects. Typically [ExitCode.OK].
 */
public class Compile2Compilation(
  public val config: Compile2CompilationConfiguration,
  public val expectedExitCode: ExitCode,
) {

  /** Renders compiler messages (errors, warnings, etc.) in a human-friendly format.  Now in color! */
  private val messageRenderer by lazy { ColorizedPlainTextMessageRenderer() }

  /**
   * Executes the compilation process. This includes:
   *
   * 1. Optionally running KAPT if [Compile2CompilationConfiguration.useKapt] is true.
   * 2. Running the Kotlin compiler on the provided source files (plus any KAPT-generated files).
   * 3. (Optional) Running the Java compiler if Java sources exist.
   *
   * @return A [Compile2Result] containing the compilation result: class loader, exit code, etc.
   */
  internal fun execute(): Compile2Result {

    val baseArgs = K2JVMCompilerArguments().also { args ->
      args.reportPerf = true

      args.moduleName = "root"
      args.additionalJavaModules = emptyArray()

      args.jdkHome = javaHome().absolutePath
      args.languageVersion = config.languageVersion.versionString

      args.compileJava = true
      args.useJavac = true

      args.verbose = config.verbose
      args.version = config.verbose

      args.allowNoSourceFiles = true

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

    // Sanity check to ensure the arguments translate properly into compiler-compatible strings.
    parseCommandLineArguments<K2JVMCompilerArguments>(baseArgs.toArgumentStrings())

    if (config.firExtensions.isNotEmpty()) {
      // Register the FIR extensions, if any, via thread-local.
      Compile2CompilerPluginRegistrar.threadLocalParams.set(
        Compile2CompilerPluginRegistrar.Compile2RegistrarParams(
          firExtensionFactories = config.firExtensions,
        ),
      )
    }

    if (config.useKapt) {
      executeKapt(baseArgs)
    }

    // After KAPT, gather all sources including anything KAPT might have generated.
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
      rootDir = config.rootDir,
      libsDir = config.jarOutputDir,
      classFilesDir = config.classFilesDir,
      classpathFiles = config.compilationClasspath,
      exitCode = exitCode,
      classLoader = classLoader,
    ).apply {

      exitCode shouldBe expectedExitCode
    }
  }

  /**
   * Compiles any Java files using `javac`. This step is only invoked if there are Java files
   * in the final set of sources (including any KAPT-generated .java files).
   *
   * @param javaSources A list of Java [File]s to compile.
   */
  private fun compileJava(javaSources: List<File>) {
    val javac = synchronized(ToolProvider::class.java) {
      ToolProvider.getSystemJavaCompiler()
    } ?: error("No system Java compiler found.")

    val javaFileManager = javac.getStandardFileManager(null, null, null)
    val diagnosticCollector = DiagnosticCollector<JavaFileObject>()

    val internalMessageStream = PrintStream(System.out)

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

      // Also add Kotlin classes to javac classpath so references to them resolve properly.
      this.addAll(
        listOf(
          "-cp",
          (config.compilationClasspath + config.classFilesDir).classpathString(),
        ),
      )
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

  /**
   * Executes a separate KAPT pass using the Kotlin compiler. This includes setting up
   * [KaptOptions], applying them, and running the `K2JVMCompiler` with appropriate flags
   * for annotation processing.
   *
   * @param baseArgs The [K2JVMCompilerArguments] to copy and modify for the KAPT invocation.
   * @throws AssertionError if the KAPT pass does not match the [expectedExitCode].
   */
  private fun executeKapt(baseArgs: K2JVMCompilerArguments) {

    val kaptPassArgs = baseArgs.copyOf().also { args ->
      args.freeArgs = config.sourceFiles.pathStrings()
      args.useJavac = false

      // The args property was renamed from `useKapt4` to `useK2Kapt` in Kotlin 2.1.0.
      // We use the string version so that it's source compatible.
      if (kotlinVersion < "2.1.0") {
        // aka `args.useKapt4 = true`
        args.freeArgs += "-Xuse-kapt4"
      } else {
        // aka `args.useK2Kapt = true`
        args.freeArgs += "-Xuse-k2-kapt"
      }

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

/**
 * A simple in-memory [SimpleJavaFileObject] for reading Java code from a [URI] rather
 * than from a local file. Used by [Compile2Compilation.compileJava] to feed source strings
 * directly to `javac`.
 *
 * @constructor Creates a file object representing the Java source pointed to by [uri].
 */
private class AnvilSimpleJavaFileObject(uri: URI) :
  SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {

  /**
   * Opens the [InputStream] from the underlying URL so `javac` can read the file's contents.
   */
  override fun openInputStream(): InputStream = uri.toURL().openStream()

  /**
   * Loads and returns the file contents as a [CharSequence].
   */
  override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
    uri.toURL().readText()
}

/**
 * Encapsulates all configuration needed by [Compile2Compilation] to perform a single
 * compile operation. This includes directories, classpaths, whether to run KAPT,
 * the Kotlin [LanguageVersion], and optional FIR extensions.
 *
 * @property rootDir The root directory for the compilation.
 * @property sourceFiles All files (Kotlin and Java) to compile.
 * @property useKapt If `true`, runs a separate KAPT pass before compiling the final set of sources.
 * @property verbose If `true`, enables verbose logging in both the Kotlin and Java compilers.
 * @property allWarningsAsErrors If `true`, treats all compiler warnings as errors in `javac`.
 * @property jarOutputDir Where any resultant artifacts (like JARs) should be placed.
 * @property classFilesDir Directory into which all `.class` files will be compiled.
 * @property kaptStubsDir KAPT-specific directory for stubs.
 * @property kaptGeneratedSourcesDir KAPT-specific directory for generated sources.
 * @property kaptIncrementalDir KAPT-specific directory for incremental data output.
 * @property kaptClassesDir KAPT-specific directory for classes output.
 * @property languageVersion Kotlin language version to use.
 * @property compilationClasspath Classpath files required for compilation.
 * @property compilerPluginClasspath Classpath files for any compiler plugins (including Anvil).
 * @property kaptPluginClasspath Classpath files for KAPT annotation processors.
 * @property firExtensions Optional FIR plugin extensions for advanced compiler tests.
 */
public data class Compile2CompilationConfiguration(
  val rootDir: File,
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
  val firExtensions: List<AnvilFirExtensionFactory<*>>,
)

/**
 * @return The current `java.home` as a [File], or throws if not set. Typically used to
 * provide the Kotlin compiler arguments with a Java home directory.
 * @throws IllegalStateException if neither `JAVA_HOME` nor `java.home` is set.
 */
internal fun javaHome(): File = javaHomeOrNull() ?: error("JAVA_HOME and 'java.home' not set")

/**
 * @return The current Java home as a [File], or `null` if neither `JAVA_HOME` nor `java.home` is set.
 */
internal fun javaHomeOrNull(): File? {
  val path = System.getProperty("java.home")
    ?: System.getenv("JAVA_HOME")
    ?: return null

  return File(path).also { check(it.isDirectory) }
}

/**
 * Converts each [File] in this collection to its absolute path [String], de-duplicating entries.
 *
 * @return A list of distinct file path strings.
 */
internal fun Iterable<File>.pathStrings(): List<String> = map { it.absolutePath }.distinct()

/**
 * Joins all [File] paths in this collection into a single classpath [String],
 * separated by [File.pathSeparator].
 *
 * @return A classpath string suitable for compiler arguments.
 */
internal fun Iterable<File>.classpathString(): String =
  pathStrings().joinToString(File.pathSeparator)
