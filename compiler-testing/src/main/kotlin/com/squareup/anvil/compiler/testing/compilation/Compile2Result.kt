package com.squareup.anvil.compiler.testing.compilation

import com.rickbusarow.kase.stdlib.div
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import io.kotest.matchers.file.shouldBeAFile
import org.jetbrains.kotlin.cli.common.ExitCode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

public class Compile2Result(
  public val rootDir: File,
  private val libsDir: File,
  private val classFilesDir: File,
  private val classpathFiles: List<File>,
  public val exitCode: ExitCode,
  public val classLoader: ClassLoader,
) {
  public val jar: File by lazy(LazyThreadSafetyMode.NONE) {
    createJar(
      outputJar = libsDir / "output.jar",
      classFilesDir = classFilesDir,
    )
  }
  public val classGraph: ScanResult by lazy(LazyThreadSafetyMode.NONE) {
    classGraphResult(classpathFiles + jar)
  }
}

private fun createJar(
  outputJar: File,
  classFilesDir: File,
): File = outputJar.apply {
  parentFile.mkdirs()

  outputStream().use { fos ->
    JarOutputStream(fos).use { jos ->
      classFilesDir.walkTopDown().forEach { file ->
        if (file.isFile && file.extension == "class") {
          val entryName = classFilesDir.toPath().relativize(file.toPath()).toString()
          val entry = JarEntry(entryName)
          jos.putNextEntry(entry)
          file.inputStream().use { it.copyTo(jos) }
          jos.closeEntry()
        }
      }
    }
  }
}

public fun File.requireIsJarFile(): File = apply {
  this.shouldBeAFile()
  require(extension == "jar") { "Expected a .jar file, but was: $this" }
}

/** */
public fun classGraphResult(jars: List<File>): ScanResult =
  ClassGraph().enableAllInfo()
    .overrideClasspath(jars.onEach { it.requireIsJarFile() })
    .scan()
