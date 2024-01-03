package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import java.io.Closeable
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest

internal typealias MD5String = String

internal class GeneratedFileCache private constructor(
  private val binaryFile: File,
  private val projectDir: ProjectDir,
) : Serializable, Closeable {

  private val generatedToContent = mutableMapOf<RelativeFile, String>()
  private val generatedToSources = SimpleMultimap<RelativeFile, RelativeFile>()
  private val sourcesToGenerated = SimpleMultimap<RelativeFile, RelativeFile>()

  private val filesToMd5 = mutableMapOf<RelativeFile, MD5String>()

  val sourceFiles: Set<RelativeFile> get() = sourcesToGenerated.keys

  fun isGenerated(file: FileType): Boolean {
    return when (file) {
      is AbsoluteFile -> isGenerated(file.relativeTo(projectDir))
      is RelativeFile -> generatedToContent.containsKey(file)
    }
  }

  fun getSourceFiles(generatedFile: FileType): Set<RelativeFile> {
    return when (generatedFile) {
      is AbsoluteFile -> getSourceFiles(generatedFile.relativeTo(projectDir))
      is RelativeFile -> generatedToSources[generatedFile]
    }
  }

  fun getContent(generatedFile: FileType): String {
    return when (generatedFile) {
      is AbsoluteFile -> getContent(generatedFile.relativeTo(projectDir))
      is RelativeFile -> generatedToContent.getValue(generatedFile)
    }
  }

  fun getGeneratedFiles(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFiles(sourceFile.relativeTo(projectDir))
      is RelativeFile -> sourcesToGenerated[sourceFile]
    }
  }

  fun getGeneratedFilesRecursive(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFilesRecursive(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        val visited = mutableSetOf<RelativeFile>()
        generateSequence(sourcesToGenerated[sourceFile]) { generated ->
          generated.filter { visited.add(it) }
            .flatMapTo(mutableSetOf()) { sourcesToGenerated[it] }
            .takeIf { it.iterator().hasNext() }
        }
          .flatten()
          .toSet()
      }
    }
  }

  fun addSourceFile(sourceFile: RelativeFile) {
    addMd5(sourceFile, overwrite = true)
  }

  private fun addMd5(sourceFile: RelativeFile, overwrite: Boolean) {
    if (overwrite) {
      filesToMd5[sourceFile] = sourceFile.md5()
    } else {
      filesToMd5.computeIfAbsent(sourceFile) { sourceFile.md5() }
    }
  }

  fun addGeneratedFile(generated: GeneratedFileWithSources) {

    val generatedAbsolute = AbsoluteFile(generated.file)
    val generatedRelative = generatedAbsolute.relativeTo(projectDir)

    generatedToContent[generatedRelative] = generated.content

    addMd5(generatedRelative, overwrite = true)

    val sourceFiles = generated.sourceFiles
      .map { AbsoluteFile(it).relativeTo(projectDir) }
      .ifEmpty { listOf(RelativeFile.ANY) }

    for (source in sourceFiles) {

      val hasCycle = getGeneratedFilesRecursive(generatedRelative).any { it == source }

      if (hasCycle) {
        throw AnvilCompilationException(
          """
          Adding this mapping would create a cycle.
             source path: ${source.absolute(projectDir).file}
          generated path: ${generated.file}
          """.trimIndent(),
        )
      }

      sourcesToGenerated.add(source, generatedRelative)
      generatedToSources.add(generatedRelative, source)

      // At this point, any source file should be in the md5 map already.
      // But in case it isn't, we'll add it now.
      addMd5(source, overwrite = false)
    }
  }

  fun removeSource(sourceFile: FileType) {
    when (sourceFile) {
      is AbsoluteFile -> removeSource(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        filesToMd5.remove(sourceFile)
        generatedToSources.remove(sourceFile)

        // All generated files that claim this source file as a source.
        val generated = sourcesToGenerated.remove(sourceFile) ?: return

        for (gen in generated) {

          // For any generated file that has multiple sources,
          // remove that generated file from the "generated" set for those other sources.
          // Note that this does not call `removeSource` for that other source.
          for (otherSource in generatedToSources[gen]) {
            if (otherSource != sourceFile) {
              sourcesToGenerated.remove(otherSource, gen)
            }
          }
          removeSource(gen)
        }
      }
    }
  }

  /**
   * Returns `true` if the [sourceFile] has changed since the last time it was added to the cache.
   */
  fun hasChanged(sourceFile: FileType): Boolean {
    return when (sourceFile) {
      is AbsoluteFile -> hasChanged(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        if (sourceFile == RelativeFile.ANY) return true
        val currentMd5 = sourceFile.md5()
        val previousMd5 = filesToMd5.put(sourceFile, currentMd5)
        return previousMd5 != currentMd5
      }
    }
  }

  override fun close() {
    writeToBinaryFile()
  }

  private fun FileType.md5(): MD5String = when (this) {
    RelativeFile.ANY -> ""
    is AbsoluteFile -> MessageDigest.getInstance("MD5")
      .digest(file.readBytes())
      .joinToString("") { "%02x".format(it) }

    is RelativeFile -> projectDir.resolve(this).md5()
  }

  override fun toString(): String = """
    |======================== ${this::class.simpleName}
    | -- sourcesToGenerated
    |$sourcesToGenerated
    |
    | -- generatedToSources
    |$generatedToSources
    |
    | -- generatedToContent
    |$generatedToContent
    |
    | -- filesToMd5
    |${filesToMd5.toList().joinToString("\n") { "${it.first} -> ${it.second}" }}
    |========================
  """.trimMargin()

  private fun writeToBinaryFile() {

    binaryFile.delete()
    binaryFile.parentFile.mkdirs()
    ObjectOutputStream(binaryFile.outputStream()).use {
      it.writeObject(this@GeneratedFileCache)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GeneratedFileCache) return false

    if (generatedToContent != other.generatedToContent) return false
    if (generatedToSources != other.generatedToSources) return false
    if (sourcesToGenerated != other.sourcesToGenerated) return false
    if (filesToMd5 != other.filesToMd5) return false

    return true
  }

  override fun hashCode(): Int {
    var result = generatedToContent.hashCode()
    result = 31 * result + generatedToSources.hashCode()
    result = 31 * result + sourcesToGenerated.hashCode()
    result = 31 * result + filesToMd5.hashCode()
    return result
  }

  companion object {
    const val GENERATED_FILE_CACHE_NAME = "generated-file-cache.bin"

    fun fromFile(binaryFile: File, projectDir: ProjectDir): GeneratedFileCache {

      return if (binaryFile.exists()) {
        try {
          ObjectInputStream(binaryFile.inputStream()).use {
            it.readObject() as GeneratedFileCache
          }
        } catch (e: Throwable) {
          GeneratedFileCache(binaryFile, projectDir)
        }
      } else {
        GeneratedFileCache(binaryFile, projectDir)
      }
    }
  }
}
