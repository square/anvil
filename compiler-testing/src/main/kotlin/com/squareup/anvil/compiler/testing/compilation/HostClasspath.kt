package com.squareup.anvil.compiler.testing.compilation

import com.squareup.anvil.compiler.testing.anvilVersion
import io.github.classgraph.ClassGraph
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

public object HostClasspath {
  private val classGraph by lazy {
    ClassGraph()
      .enableSystemJarsAndModules()
      .removeTemporaryFilesAfterScan()
  }

  private val File.pathSegments: List<String>
    get() = path.split(File.separatorChar)

  public val inheritedClasspath: List<File> by lazy { getHostClasspaths() }

  public val projectClassFileDirectories: List<File> by lazy {
    classGraph.classpathFiles.filter { it.isDirectory }
  }

  public val allInheritedAnvilProjects: List<File> by lazy {

    val relativeJarParents = listOf("build", "libs")

    inheritedClasspath
      .filter { it.isFile && it.extension == "jar" }
      .filter {
        // [..., "anvil", "compiler", "build", "libs", "compiler-2.5.0-SNAPSHOT.jar"]

        @Suppress("MagicNumber")
        val libsDir = it.pathSegments
          // ["build", "libs", "compiler-2.5.0-SNAPSHOT.jar"]
          .takeLast(3)
          // ["build", "libs"]
          .dropLast(1)

        libsDir == relativeJarParents
      }
  }

  private fun anvilModuleJar(moduleName: String, afterVersion: String = ""): File {
    val localBuildDirLibs =
      Regex.escape("+${File.separatorChar}build${File.separatorChar}libs${File.separatorChar}")

    return findInClasspathOrNull("${localBuildDirLibs}$moduleName-$anvilVersion${afterVersion}\\.jar".toRegex())
      // If the jar isn't in a project build directory, this is probably being consumed by an external project.
      // In that case, the jar should resolve like any other dependency from the Gradle cache.
      ?: findInClasspath(group = "com.squareup.anvil", module = moduleName, version = anvilVersion)
  }

  public val anvilAnnotations: File by lazy { anvilModuleJar("annotations") }
  public val anvilAnnotationsOptional: File by lazy { anvilModuleJar("annotations-optional") }
  public val anvilCompiler: File by lazy { anvilModuleJar("compiler") }
  public val anvilCompilerK2: File by lazy { anvilModuleJar("compiler-k2") }
  public val anvilCompilerK2Api: File by lazy { anvilModuleJar("compiler-k2-api") }
  public val anvilCompilerApi: File by lazy { anvilModuleJar("compiler-api") }
  public val anvilCompilerUtils: File by lazy { anvilModuleJar("compiler-utils") }
  public val anvilCompilerUtilsTestFixtures: File by lazy {
    anvilModuleJar("compiler-utils", "-test-fixtures")
  }

  private const val jetbrainsKotlin = "org.jetbrains.kotlin"
  private const val jetbrainsKotlinx = "org.jetbrains.kotlinx"

  public val intellijTrove4j: File by lazy {
    findInClasspath(group = "com.jetbrains.intellij.deps", module = "trove4j")
  }
  public val intellijCore: File by lazy {
    findInClasspath(group = "com.jetbrains.intellij.platform", module = "core")
  }

  public val intellijUtil: File by lazy {
    findInClasspath(group = "com.jetbrains.intellij.platform", module = "util")
  }
  public val kotlinCompilerEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-compiler-embeddable")
  }
  public val kotlinScriptingCompilerEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-scripting-compiler-embeddable")
  }
  public val kotlinScriptingCompiler: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-scripting-compiler")
  }

  public val kotlinAnnotationProcessingEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing-embeddable")
  }

  public val kotlinAnnotationProcessing: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing")
  }

  public val kotlinAnnotationProcessingCompiler: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing-compiler")
  }

  public val autoServiceAnnotations: File by lazy {
    findInClasspath(group = "com.google.auto.service", module = "auto-service-annotations")
  }

  public val dagger: File by lazy {
    findInClasspath(group = "com.google.dagger", module = "dagger")
  }
  public val daggerCompiler: File by lazy {
    findInClasspath(group = "com.google.dagger", module = "dagger-compiler")
  }

  public val javaxJsr250Api: File by lazy {
    findInClasspath(group = "javax.annotation", module = "jsr250-api")
  }
  public val javaxInject: File by lazy {
    findInClasspath(group = "javax.inject", module = "javax.inject")
  }
  public val jakartaInject: File by lazy {
    findInClasspath(group = "jakarta.inject", module = "jakarta.inject-api")
  }

  public val kotlinxSerializationCoreJvm: File by lazy {
    findInClasspath(group = jetbrainsKotlinx, module = "kotlinx-serialization-core-jvm")
  }

  public val jetbrainsAnnotations: File by lazy {
    findInClasspath(group = "org.jetbrains", module = "annotations")
  }

  public val kotlinStdLib: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-stdlib")
  }

  public val kotlinReflect: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-reflect")
  }

  public val kotlinStdLibCommonJar: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-stdlib-common")
  }

  public val kotlinStdLibJdkJar: File by lazy {
    findInClasspath(kotlinDependencyRegex("kotlin-stdlib-jdk[0-9]+"))
  }

  private val SLASH = File.separatorChar

  private fun kotlinDependencyRegex(prefix: String): Regex {
    return Regex("""$prefix(-[0-9]+\.[0-9]+(\.[0-9]+)?)([-0-9a-zA-Z]+)?\.jar""")
  }

  /** Tries to find a file matching the given [regex] in the host process' classpath. */
  private fun findInClasspath(regex: Regex): File = findInClasspathOrNull(regex)
    .requireNotNull { "could not find classpath file via regex: $regex" }

  /** Tries to find a file matching the given [regex] in the host process' classpath. */
  private fun findInClasspathOrNull(regex: Regex): File? = inheritedClasspath
    .firstOrNull { classpath -> classpath.path.matches(regex) }

  /** Tries to find a .jar file given pieces of its maven coordinates */
  private fun findInClasspath(
    group: String? = null,
    module: String? = null,
    version: String? = null,
  ): File {
    require(group != null || module != null || version != null)
    return inheritedClasspath.firstOrNull { classpath ->

      val classpathIsLocal = classpath.absolutePath.contains(".m2${SLASH}repository$SLASH")

      val (fileGroup, fileModule, fileVersion) = if (classpathIsLocal) {
        parseMavenLocalClasspath(classpath)
      } else {
        parseGradleCacheClasspath(classpath)
      }

      if (group != null && group != fileGroup) return@firstOrNull false
      if (module != null && module != fileModule) return@firstOrNull false
      version == null || version == fileVersion
    }
      .requireNotNull {
        "could not find classpath file [group: $group, module: $module, version: $version]"
      }
  }

  private fun parseMavenLocalClasspath(classpath: File): List<String> {
    // ~/.m2/repository/com/square/anvil/compiler-utils/1.0.0/compiler-utils-1.0.0.jar
    return classpath.absolutePath
      .substringAfter(".m2${SLASH}repository$SLASH")
      // Groups have their dots replaced with file separators, like "com/squareup/anvil".
      // Module names use dashes, so they're unchanged.
      .split(File.separatorChar)
      // ["com", "square", "anvil", "compiler-utils", "1.0.0", "compiler-1.0.0.jar"]
      // drop the simple name and extension
      .dropLast(1)
      .let { segments ->

        listOf(
          // everything but the last two segments is the group
          segments.dropLast(2).joinToString("."),
          // second-to-last segment is the module
          segments[segments.lastIndex - 1],
          // the last segment is the version
          segments.last(),
        )
      }
  }

  @Suppress("MagicNumber")
  private fun parseGradleCacheClasspath(classpath: File): List<String> {
    // example of a starting path:
    // [...]/com.square.anvil/compiler/1.0.0/911d07691411f7cbccf00d177ac41c1af38/compiler-1.0.0.jar
    return classpath.absolutePath
      .split(File.separatorChar)
      // [..., "com.square.anvil", "compiler", "1.0.0", "91...38", "compiler-1.0.0.jar"]
      .dropLast(2)
      .takeLast(3)
  }

  @OptIn(ExperimentalContracts::class)
  public inline fun <T : Any> T?.requireNotNull(lazyMessage: () -> Any): T {
    contract {
      returns() implies (this@requireNotNull != null)
    }
    return requireNotNull(this, lazyMessage)
  }

  /** Returns the files on the classloader's classpath and module path. */
  private fun getHostClasspaths(): List<File> {

    val classpaths = classGraph.classpathFiles
    val modules = classGraph.modules.mapNotNull { it.locationFile }

    return (classpaths + modules).distinctBy(File::getAbsolutePath)
  }
}
