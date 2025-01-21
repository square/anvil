package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.gradle.GradleProjectBuilder
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import java.io.File

fun GradleProjectBuilder.requireJarArtifact(buildDir: File = path.resolve("build")): File {

  fun Sequence<File>.requireSingle(libs: File): File {
    val size = count()
    require(size == 1) {
      """
      |Expected 1 jar file, but found $size in $libs:
      |  ${joinToString("\n  ")}
      |
      """.trimMargin()
    }
    return single()
  }

  // Android libs
  val intermediates = buildDir.resolve("intermediates/aar_main_jar/debug")
  val classesJar = intermediates
    .walkBottomUp()
    .filter { it.isFile && it.extension == "jar" }

  if (classesJar.any()) {
    return classesJar.requireSingle(intermediates)
  }

  // Java/Kotlin libs
  val libs = buildDir.resolve("libs")
  return libs.walkBottomUp()
    .filter { file ->
      when {
        !file.isFile -> false
        file.extension != "jar" -> false
        // We only care about the main jar file, not sources or javadoc jars.
        file.nameWithoutExtension.matches(".*?-(?:sources|javadoc)".toRegex()) -> false
        else -> true
      }
    }.requireSingle(libs)
}

/**
 * Fetches the generated `.jar` artifact for this project and optional dependencies,
 * then creates a ClassGraph scan result for inspection.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   rootProject {
 *     gradleProject("a") { /* ... */ }
 *     gradleProject("b") {
 *       buildFile {
 *          // ...
 *          dependencies {
 *            implementation(project(":a"))
 *          }
 *       }
 *
 *       /* ... */
 *     }
 *   }
 *
 *   shouldSucceed(":b:jar")
 *
 *   val a by rootProject.subprojects
 *   val b by rootProject.subprojects
 *
 *   val scanResult = b.classGraphResult(a)
 * }
 * ```
 */
fun GradleProjectBuilder.classGraphResult(
  vararg dependencyProjects: GradleProjectBuilder,
  buildDir: File = path.resolve("build"),
): ScanResult {
  val jars = buildList {
    add(requireJarArtifact(buildDir = buildDir))
    addAll(dependencyProjects.map { it.requireJarArtifact() })
  }
  return ClassGraph().enableAllInfo()
    .overrideClasspath(jars)
    .scan()
}
