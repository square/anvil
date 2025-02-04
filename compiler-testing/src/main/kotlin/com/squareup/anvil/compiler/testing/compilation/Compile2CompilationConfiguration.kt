package com.squareup.anvil.compiler.testing.compilation

import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionFactory
import com.squareup.anvil.compiler.testing.BuildConfig
import org.jetbrains.kotlin.config.LanguageVersion
import java.io.File

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
 * @property languageVersion Kotlin language API level.
 * @property jdkHome Explicit path to a JDK home, if custom or different from the system default.
 * @property compilationClasspath Classpath files required for compilation.
 * @property compilerPluginClasspath Classpath files for any compiler plugins (including Anvil).
 * @property kaptPluginClasspath Classpath files for KAPT annotation processors (if [useKapt] is true).
 * @property firExtensions Optional FIR plugin extensions for one-off compiler tests.
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
  val languageVersion: LanguageVersion,
  val jdkHome: File?,
  val compilationClasspath: List<File>,
  val compilerPluginClasspath: List<File>,
  val kaptPluginClasspath: List<File>,
  val firExtensions: List<AnvilFirExtensionFactory<*>>,
) {

  public companion object {

    /**
     * Creates a [Compile2CompilationConfiguration] with default paths and classpaths
     * suitable for typical test usage.
     *
     * @param workingDir The directory to serve as [rootDir].
     * @param useKapt Whether to enable KAPT support in the resulting configuration.
     * @return A [Compile2CompilationConfiguration] pre-filled with typical defaults.
     */
    public fun default(workingDir: File, useKapt: Boolean): Compile2CompilationConfiguration {
      val kaptDir = workingDir / "kapt"
      val compilationClasspath = listOf(
        HostClasspath.jakartaInject,
        HostClasspath.javaxInject,
        HostClasspath.dagger,
        HostClasspath.kotlinStdLib,
        HostClasspath.jetbrainsAnnotations,
        HostClasspath.anvilAnnotations,
      )

      return Compile2CompilationConfiguration(
        rootDir = workingDir,
        sourceFiles = emptyList(),
        useKapt = useKapt,
        verbose = false,
        allWarningsAsErrors = false,
        jarOutputDir = workingDir / "libs",
        classFilesDir = workingDir / "kotlin" / "classes",
        kaptStubsDir = kaptDir / "stubs",
        kaptGeneratedSourcesDir = kaptDir / "generated",
        kaptIncrementalDir = kaptDir / "incremental",
        kaptClassesDir = kaptDir / "classes",
        languageVersion = BuildConfig.languageVersion,
        jdkHome = javaHomeOrNull(),
        firExtensions = emptyList(),

        // Classpath for the compiler itself.
        compilationClasspath = compilationClasspath,

        // Classpath for compiler plugins (including Anvil).
        compilerPluginClasspath = compilationClasspath +
          HostClasspath.projectClassFileDirectories +
          HostClasspath.allInheritedAnvilProjects + listOf(
            HostClasspath.jakartaInject,
            HostClasspath.javaxInject,
            HostClasspath.javaxJsr250Api,
            HostClasspath.kotlinCompilerEmbeddable,
          )
            .letIf(useKapt) { it + HostClasspath.kotlinAnnotationProcessingEmbeddable }
            .distinct(),

        // Classpath specifically used for KAPT annotation processors.
        kaptPluginClasspath = listOf(
          HostClasspath.jakartaInject,
          HostClasspath.javaxInject,
          HostClasspath.dagger,
          HostClasspath.kotlinStdLib,
          HostClasspath.jetbrainsAnnotations,
          HostClasspath.anvilAnnotations,
        ),
      )
    }
  }
}
