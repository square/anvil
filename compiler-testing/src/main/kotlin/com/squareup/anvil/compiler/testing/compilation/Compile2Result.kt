package com.squareup.anvil.compiler.testing.compilation

import com.rickbusarow.kase.stdlib.div
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import io.kotest.matchers.file.shouldBeAFile
import org.jetbrains.kotlin.cli.common.ExitCode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.pathString

/**
 * Represents the result of a single [Compile2Compilation] invocation.
 *
 * **Typical usage**:
 * ```kotlin
 * val result = compile2("... some Kotlin code ...")
 * // Check the exit code.
 * require(result.exitCode == ExitCode.OK)
 *
 * // Examine compiled classes with classGraph.
 * val classInfo = result.classGraph.getClassInfo("com.example.YourClass")
 * check(classInfo != null)
 *
 * // Use the class loader.
 * val yourClass = result.classLoader.loadClass("com.example.YourClass")
 * ```
 *
 * @property rootDir The root directory for the compilation process.
 * @property libsDir The directory containing any `.jar` files.
 * @property classFilesDir The directory containing all compiled `.class` files.
 * @property classpathFiles The classpath files used during compilation.
 * @property exitCode The compiler's exit code after compilation.
 * @property classLoader A class loader containing the newly compiled classes (and the classpath used).
 */
public class Compile2Result(
  public val rootDir: File,
  public val libsDir: File,
  public val classFilesDir: File,
  public val classpathFiles: List<File>,
  public val exitCode: ExitCode,
  public val classLoader: ClassLoader,
) {
  /**
   * A `.jar` archive containing all `.class` files produced by this compilation.  This is equivalent
   * to the output of a Gradle `:jar` task.
   *
   * Built lazily when first accessed, it is written to `[libsDir]/output.jar`.
   */
  public val jar: File by lazy(LazyThreadSafetyMode.NONE) {
    createJar(
      outputJar = libsDir / "output.jar",
      classFilesDir = classFilesDir,
    )
  }

  /**
   * A [ScanResult] from [ClassGraph] which scans both the compiled [jar] plus any additional
   * [classpathFiles].
   *
   * Use this for introspection of the compiled classes (e.g., verifying generated classes, checking
   * annotations, etc.), or for loading classes via [ClassLoader.loadClass].
   *
   * @see classLoader for another way to load java Class types for introspection.
   */
  public val classGraph: ScanResult by lazy(LazyThreadSafetyMode.NONE) {
    classGraphResult(classpathFiles + jar)
  }
}

private fun createJar(
  outputJar: File,
  classFilesDir: File,
): File = outputJar.apply {
  // Ensure the output directory exists before writing the jar.
  parentFile.mkdirs()

  outputStream().use { fos ->
    JarOutputStream(fos).use { jos ->
      // Walk the directory tree to find all .class files
      classFilesDir.walkTopDown().forEach { file ->
        if (file.isFile && file.extension == "class") {
          // Determine the relative path inside the jar
          val entryName = classFilesDir.toPath().relativize(file.toPath())
            .pathString
            // Force Windows separators to be unix-style
            .replace("\\", "/")
          val entry = JarEntry(entryName)
          jos.putNextEntry(entry)
          // Copy the .class file into the jar
          file.inputStream().use { it.copyTo(jos) }
          jos.closeEntry()
        }
      }
    }
  }
}

/**
 * Ensures that this [File] is a jar file (i.e., has the `.jar` extension). Throws if it's not.
 *
 * @return This file, if it is indeed a jar.
 * @throws IllegalArgumentException if the file does not have a `.jar` extension.
 */
public fun File.requireIsJarFile(): File = apply {
  this.shouldBeAFile()
  require(extension == "jar") { "Expected a .jar file, but was: $this" }
}

/**
 * Creates a [ScanResult] by scanning all provided [jars] with [ClassGraph].
 * Any attempt to scan non-jar files results in an error due to [requireIsJarFile].
 *
 * Usage example:
 * ```kotlin
 * val scanResult = classGraphResult(listOf(myCompiledJar))
 * val classInfo = scanResult.getClassInfo("com.example.SomeClass")
 * println(classInfo.annotations)
 * ```
 *
 * @param jars The list of jar files to be scanned. Each must end with `.jar`.
 * @return A [ScanResult] which can be used to examine package, class, and annotation metadata.
 */
public fun classGraphResult(jars: List<File>): ScanResult =
  ClassGraph().enableAllInfo()
    .overrideClasspath(jars.onEach { it.requireIsJarFile() })
    .scan()
