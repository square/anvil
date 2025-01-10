package com.squareup.anvil.compiler.testing

import io.github.classgraph.ClassGraph
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal object HostEnvironment {

  private val File.pathSegments: List<String>
    get() = path.split(File.separatorChar)

  val inheritedClasspath: List<File> by lazy { getHostClasspaths() }

  val allInheritedAnvilProjects: List<File> by lazy {

    val relativeJarParents = listOf("build", "root-build", "libs")

    inheritedClasspath
      .filter { it.isFile && it.extension == "jar" }
      .filter {
        // [..., "anvil", "compiler", "build", "included-build", "libs", "compiler-2.5.0-SNAPSHOT.jar"]

        @Suppress("MagicNumber")
        val last3Segments = it.pathSegments
          // ["build", "included-build", "libs", "compiler-2.5.0-SNAPSHOT.jar"]
          .takeLast(4)
          // ["build", "included-build", "libs"]
          .dropLast(1)

        last3Segments == relativeJarParents
      }
  }

  private fun anvilModuleJar(moduleName: String): File {
    return findInClasspath(".+build/libs/$moduleName-${anvilVersion}\\.jar".toRegex())
  }

  val anvilAnnotations: File by lazy { anvilModuleJar("annotations") }
  val anvilAnnotationsOptional: File by lazy { anvilModuleJar("annotations-optional") }
  val anvilCompiler: File by lazy { anvilModuleJar("compiler") }
  val anvilCompilerK2: File by lazy { anvilModuleJar("compiler-k2") }
  val anvilCompilerApi: File by lazy { anvilModuleJar("compiler-api") }
  val anvilCompilerUtils: File by lazy { anvilModuleJar("compiler-utils") }
  val anvilCompilerUtilsTestFixtures: File by lazy {
    findInClasspath(".+build/libs/compiler-utils-$anvilVersion-test-fixtures\\.jar".toRegex())
  }

  private val jetbrainsKotlin = "org.jetbrains.kotlin"
  private val jetbrainsKotlinx = "org.jetbrains.kotlinx"

  val intellijCore: File by lazy {
    findInClasspath(group = "com.jetbrains.intellij.platform", module = "core")
  }

  val intellijUtil: File by lazy {
    findInClasspath(group = "com.jetbrains.intellij.platform", module = "util")
  }
  val kotlinCompilerEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-compiler-embeddable")
  }
  val kotlinScriptingCompilerEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-scripting-compiler-embeddable")
  }
  val kotlinScriptingCompiler: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-scripting-compiler")
  }

  val kotlinAnnotationProcessingEmbeddable: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing-embeddable")
  }

  val kotlinAnnotationProcessing: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing")
  }

  val kotlinAnnotationProcessingCompiler: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-annotation-processing-compiler")
  }

  val autoServiceAnnotations: File by lazy {
    findInClasspath(group = "com.google.auto.service", module = "auto-service-annotations")
  }

  val dagger: File by lazy { findInClasspath(group = "com.google.dagger", module = "dagger") }
  val daggerCompiler: File by lazy {
    findInClasspath(group = "com.google.dagger", module = "dagger-compiler")
  }

  val javaxInject: File by lazy {
    findInClasspath(group = "javax.inject", module = "javax.inject")
  }
  val jakartaInject: File by lazy {
    findInClasspath(group = "jakarta.inject", module = "jakarta.inject-api")
  }

  val kotlinxSerializationCoreJvm: File by lazy {
    findInClasspath(group = jetbrainsKotlinx, module = "kotlinx-serialization-core-jvm")
  }

  val jetbrainsAnnotations: File by lazy {
    findInClasspath(group = "org.jetbrains", module = "annotations")
  }

  val kotlinStdLib: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-stdlib")
  }

  val kotlinReflect: File by lazy {
    findInClasspath(group = jetbrainsKotlin, module = "kotlin-reflect")
  }

  val kotlinStdLibCommonJar: File by lazy {
    findInClasspath(kotlinDependencyRegex("kotlin-stdlib-common"))
  }

  val kotlinStdLibJdkJar: File by lazy {
    findInClasspath(kotlinDependencyRegex("kotlin-stdlib-jdk[0-9]+"))
  }

  private fun kotlinDependencyRegex(prefix: String): Regex {
    return Regex("$prefix(-[0-9]+\\.[0-9]+(\\.[0-9]+)?)([-0-9a-zA-Z]+)?\\.jar")
  }

  /** Tries to find a file matching the given [regex] in the host process' classpath. */
  private fun findInClasspath(regex: Regex): File = inheritedClasspath
    .firstOrNull { classpath -> classpath.path.matches(regex) }
    .requireNotNull { "could not find classpath file via regex: $regex" }

  /** Tries to find a .jar file given pieces of its maven coordinates */
  private fun findInClasspath(
    group: String? = null,
    module: String? = null,
    version: String? = null,
  ): File {
    require(group != null || module != null || version != null)
    return inheritedClasspath.firstOrNull { classpath ->

      val classpathIsLocal = classpath.absolutePath.contains(".m2/repository/")

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
      .substringAfter(".m2/repository/")
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
  inline fun <T : Any> T?.requireNotNull(lazyMessage: () -> Any): T {
    contract {
      returns() implies (this@requireNotNull != null)
    }
    return requireNotNull(this, lazyMessage)
  }

  /** Returns the files on the classloader's classpath and module path. */
  private fun getHostClasspaths(): List<File> {
    val classGraph = ClassGraph()
      .enableSystemJarsAndModules()
      .removeTemporaryFilesAfterScan()

    val classpaths = classGraph.classpathFiles
    val modules = classGraph.modules.mapNotNull { it.locationFile }

    return (classpaths + modules).distinctBy(File::getAbsolutePath)
  }
}
