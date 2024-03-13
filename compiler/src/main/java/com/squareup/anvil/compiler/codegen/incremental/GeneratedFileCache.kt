package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.codegen.incremental.collections.Multimap
import com.squareup.anvil.compiler.codegen.incremental.collections.MutableBiMap
import com.squareup.anvil.compiler.mapToSet
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.random.Random

internal typealias MD5String = String
internal typealias FileId = Int

internal class GeneratedFileCache private constructor(
  private val binaryFile: File,
  private val projectDir: ProjectDir,
) : Closeable {

  private val tables: Tables by lazy(NONE) { Tables.fromFile(binaryFile) }

  /**
   * Source files that don't have any source files of their own.
   */
  val rootSourceFiles: Set<RelativeFile>
    get() = tables.getSourceFilesWithoutGeneratedContent()
      .filter { source -> source != RelativeFile.ANY }
      .toSet()

  fun getContent(generatedFile: FileType): String {
    return when (generatedFile) {
      is AbsoluteFile -> getContent(generatedFile.relativeTo(projectDir))
      is RelativeFile -> tables.getGeneratedContent(generatedFile)
    }
  }

  fun getGeneratedFiles(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFiles(sourceFile.relativeTo(projectDir))
      is RelativeFile -> tables.getGeneratedFiles(sourceFile)
    }
  }

  fun getGeneratedFilesRecursive(sourceFile: FileType): Set<RelativeFile> {
    return when (sourceFile) {
      is AbsoluteFile -> getGeneratedFilesRecursive(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {
        val visited = mutableSetOf<RelativeFile>()
        generateSequence(tables.getGeneratedFiles(sourceFile)) { generated ->
          generated.filter { visited.add(it) }
            .flatMapTo(mutableSetOf()) { tables.getGeneratedFiles(it) }
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
      tables.putMd5(sourceFile, sourceFile.md5())
    } else {
      tables.putMd5IfAbsent(sourceFile) { sourceFile.md5() }
    }
  }

  fun addGeneratedFile(generated: GeneratedFileWithSources) {

    val generatedAbsolute = AbsoluteFile(generated.file)
    val generatedRelative = generatedAbsolute.relativeTo(projectDir)

    tables.putGeneratedContent(generatedRelative, generated.content)

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

      tables.putSourceGeneratedPair(source, generatedRelative)

      // At this point, any source file should be in the md5 map already.
      // But in case it isn't, we'll add it now.
      addMd5(source, overwrite = false)
    }
  }

  fun removeSource(sourceFile: FileType) {
    when (sourceFile) {
      is AbsoluteFile -> removeSource(sourceFile.relativeTo(projectDir))
      is RelativeFile -> {

        // All generated files that claim this source file as a source.
        val generated = tables.getGeneratedFiles(sourceFile)

        for (gen in generated.toList()) {

          tables.deleteSourceGeneratedPair(sourceFile = sourceFile, generatedFile = gen)

          // For any generated file that has multiple sources,
          // remove that generated file from the "generated" set for those other sources.
          // Note that this does not call `removeSource` for that other source.
          for (otherSource in tables.getSourceFiles(gen)) {
            if (otherSource != sourceFile) {
              tables.deleteSourceGeneratedPair(otherSource, gen)
            }
          }

          // Treat this generated file as a source file,
          // removing it and any files that were generated because of it.
          removeSource(gen)
        }

        tables.deleteFile(sourceFile)
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
        if (!sourceFile.exists(projectDir)) return true
        val currentMd5 = sourceFile.md5()
        val previousMd5 = tables.putMd5(sourceFile, currentMd5)
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

  private class Tables private constructor(
    val fileIds: MutableBiMap<RelativeFile, FileId>,
    val generatedToContent: MutableMap<FileId, String>,
    val filesToMd5: MutableMap<FileId, MD5String>,
    val sourcesToGenerated: Multimap<FileId, FileId>,
    val generatedToSources: Multimap<FileId, FileId>,
  ) : Serializable {

    constructor() : this(
      fileIds = MutableBiMap(),
      generatedToContent = mutableMapOf(),
      filesToMd5 = mutableMapOf(),
      sourcesToGenerated = Multimap(),
      generatedToSources = Multimap(),
    )

    private val RelativeFile.id: FileId
      get() = fileIds.getOrPut(this@id) {
        val inverse = fileIds.inverse
        sequence {
          while (true) {
            yield(Random(System.currentTimeMillis()).nextInt())
          }
        }.first { !inverse.containsKey(it) }
      }

    fun getSourceFilesWithoutGeneratedContent(): Set<RelativeFile> = sourcesToGenerated.keys
      .filter { !generatedToContent.containsKey(it) }
      .mapToSet { fileIds.inverse.getValue(it) }

    fun putSourceGeneratedPair(sourceFile: RelativeFile, generatedFile: RelativeFile) {
      sourcesToGenerated.add(sourceFile.id, generatedFile.id)
      generatedToSources.add(generatedFile.id, sourceFile.id)
    }

    fun deleteFile(file: RelativeFile) {
      val id = file.id
      sourcesToGenerated.remove(id)
      generatedToSources.remove(id)
      generatedToContent.remove(id)
      filesToMd5.remove(id)
      fileIds.remove(file)
    }

    fun deleteSourceGeneratedPair(sourceFile: RelativeFile, generatedFile: RelativeFile) {
      sourcesToGenerated.remove(sourceFile.id, generatedFile.id)
      generatedToSources.remove(generatedFile.id, sourceFile.id)
    }

    fun putMd5(relativeFile: RelativeFile, md5: MD5String): MD5String? {
      return filesToMd5.put(relativeFile.id, md5)
    }

    fun putMd5IfAbsent(relativeFile: RelativeFile, md5: () -> MD5String): MD5String {
      return filesToMd5.computeIfAbsent(relativeFile.id) { md5() }
    }

    fun getSourceFiles(generatedFile: RelativeFile): Set<RelativeFile> =
      generatedToSources[generatedFile.id]
        .mapToSet { fileIds.inverse.getValue(it) }

    fun getGeneratedFiles(sourceFile: RelativeFile): Set<RelativeFile> =
      sourcesToGenerated[sourceFile.id]
        .mapToSet { fileIds.inverse.getValue(it) }

    fun putGeneratedContent(generatedFile: RelativeFile, content: String): String? {
      return generatedToContent.put(generatedFile.id, content)
    }

    fun getGeneratedContent(generatedFile: RelativeFile): String {
      return generatedToContent.getValue(generatedFile.id)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Tables) return false

      if (generatedToContent != other.generatedToContent) return false
      if (sourcesToGenerated != other.sourcesToGenerated) return false
      if (generatedToSources != other.generatedToSources) return false
      if (filesToMd5 != other.filesToMd5) return false
      if (fileIds != other.fileIds) return false

      return true
    }

    override fun hashCode(): Int {
      var result = generatedToContent.hashCode()
      result = 31 * result + sourcesToGenerated.hashCode()
      result = 31 * result + generatedToSources.hashCode()
      result = 31 * result + filesToMd5.hashCode()
      result = 31 * result + fileIds.hashCode()
      return result
    }

    companion object {
      @Suppress("ConstPropertyName")
      private const val serialVersionUID: Long = -5573746546333024013L

      fun fromFile(file: File): Tables {

        if (!file.exists()) return Tables()

        return try {
          file.inputStream().use { inputStream ->
            ObjectInputStream(inputStream).use { objectInputStream ->
              objectInputStream.readObject() as Tables
            }
          }
        } catch (e: IOException) {
          Tables()
        } catch (e: InvalidClassException) {
          Tables()
        }
      }
    }
  }

  companion object {
    private const val GENERATED_FILE_CACHE_NAME = "generated-file-cache.bin"

    fun binaryFile(cacheDir: File): File = cacheDir.resolve(GENERATED_FILE_CACHE_NAME)

    fun fromFile(
      binaryFile: File,
      projectDir: ProjectDir,
    ): GeneratedFileCache = GeneratedFileCache(
      binaryFile = binaryFile,
      projectDir = projectDir,
    )
  }
}
