package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest
import kotlin.LazyThreadSafetyMode.NONE

internal typealias MD5String = String

internal class GeneratedFileCache private constructor(
  private val binaryFile: File,
  private val projectDir: ProjectDir,
) : Serializable, Closeable {

  private val tables: Tables by lazy(NONE) {

    fun tables() = Tables(
      generatedToContent = mutableMapOf(),
      generatedToSources = SimpleMultimap(),
      sourcesToGenerated = SimpleMultimap(),
      filesToMd5 = mutableMapOf(),
    )

    if (!binaryFile.isFile) return@lazy tables()
    try {
      ObjectInputStream(binaryFile.inputStream())
        .use { it.readObject() as Tables }
    } catch (e: IOException) {
      tables()
    }
  }

  val sourceFiles: Set<RelativeFile> get() = tables.sourcesToGenerated.keys

  fun isGenerated(file: FileType): Boolean {
    return when (file) {
      is AbsoluteFile -> isGenerated(file.relativeTo(projectDir))
      is RelativeFile -> tables.generatedToContent.containsKey(file)
    }
  }

  fun getSourceFiles(generatedFile: FileType): Set<RelativeFile> {
    return when (generatedFile) {
      is AbsoluteFile -> getSourceFiles(generatedFile.relativeTo(projectDir))
      is RelativeFile -> tables.generatedToSources[generatedFile]
    }
  }

  fun getContent(generatedFile: FileType): String {
    return when (generatedFile) {
      is AbsoluteFile -> getContent(generatedFile.relativeTo(projectDir))
      is RelativeFile -> tables.generatedToContent.getValue(generatedFile)
    }
  }

  fun getGeneratedFiles(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFiles(sourceFile.relativeTo(projectDir))
      is RelativeFile -> tables.sourcesToGenerated[sourceFile]
    }
  }

  fun getGeneratedFilesRecursive(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFilesRecursive(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        val visited = mutableSetOf<RelativeFile>()
        generateSequence(tables.sourcesToGenerated[sourceFile]) { generated ->
          generated.filter { visited.add(it) }
            .flatMapTo(mutableSetOf()) { tables.sourcesToGenerated[it] }
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
      tables.filesToMd5[sourceFile] = sourceFile.md5()
    } else {
      tables.filesToMd5.computeIfAbsent(sourceFile) { sourceFile.md5() }
    }
  }

  fun addGeneratedFile(generated: GeneratedFileWithSources) {

    val generatedAbsolute = AbsoluteFile(generated.file)
    val generatedRelative = generatedAbsolute.relativeTo(projectDir)

    tables.generatedToContent[generatedRelative] = generated.content

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

      tables.sourcesToGenerated.add(source, generatedRelative)
      tables.generatedToSources.add(generatedRelative, source)

      // At this point, any source file should be in the md5 map already.
      // But in case it isn't, we'll add it now.
      addMd5(source, overwrite = false)
    }
  }

  fun removeSource(sourceFile: FileType) {
    when (sourceFile) {
      is AbsoluteFile -> removeSource(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        tables.filesToMd5.remove(sourceFile)
        tables.generatedToSources.remove(sourceFile)

        // All generated files that claim this source file as a source.
        val generated = tables.sourcesToGenerated.remove(sourceFile) ?: return

        for (gen in generated) {

          // For any generated file that has multiple sources,
          // remove that generated file from the "generated" set for those other sources.
          // Note that this does not call `removeSource` for that other source.
          for (otherSource in tables.generatedToSources[gen]) {
            if (otherSource != sourceFile) {
              tables.sourcesToGenerated.remove(otherSource, gen)
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
        val previousMd5 = tables.filesToMd5.put(sourceFile, currentMd5)
        return previousMd5 != currentMd5
      }
    }
  }

  private fun FileType.md5(): MD5String = when (this) {
    RelativeFile.ANY -> ""
    is AbsoluteFile -> MessageDigest.getInstance("MD5")
      .digest(file.readBytes())
      .joinToString("") { "%02x".format(it) }

    is RelativeFile -> projectDir.resolve(this).md5()
  }

  override fun close() {
    writeToBinaryFile()
  }

  private fun writeToBinaryFile() {

    binaryFile.delete()
    binaryFile.parentFile.mkdirs()
    ObjectOutputStream(binaryFile.outputStream())
      .use { it.writeObject(tables) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GeneratedFileCache) return false
    if (tables != other.tables) return false
    return true
  }

  override fun hashCode(): Int {
    return tables.hashCode()
  }

  private class Tables(
    val generatedToContent: MutableMap<RelativeFile, String>,
    val generatedToSources: SimpleMultimap<RelativeFile, RelativeFile>,
    val sourcesToGenerated: SimpleMultimap<RelativeFile, RelativeFile>,
    val filesToMd5: MutableMap<RelativeFile, MD5String>,
  ) : Serializable {

    override fun toString(): String = """
      |======================== ${this::class.simpleName}
      | -- sourcesToGenerated
      |$sourcesToGenerated
      |
      | -- generatedToSources
      |$generatedToSources
      |
      | -- filesToMd5
      |${filesToMd5.toList().joinToString("\n") { "${it.first} -> ${it.second}" }}
      |
      | -- generatedToContent
      |$generatedToContent
      |========================
    """.trimMargin()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Tables) return false

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
      private const val serialVersionUID: Long = -5573746546333024015L
    }
  }

  companion object {
    const val GENERATED_FILE_CACHE_NAME = "generated-file-cache.bin"

    fun fromFile(
      binaryFile: File,
      projectDir: ProjectDir,
    ): GeneratedFileCache = GeneratedFileCache(
      binaryFile = binaryFile,
      projectDir = projectDir,
    )
  }
}
