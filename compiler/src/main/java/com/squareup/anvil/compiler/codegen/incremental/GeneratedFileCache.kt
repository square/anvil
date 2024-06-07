package com.squareup.anvil.compiler.codegen.incremental

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.codegen.incremental.collections.Multimap
import com.squareup.anvil.compiler.codegen.incremental.collections.MutableBiMap
import com.squareup.anvil.compiler.mapNotNullToSet
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
  private val projectDir: BaseDir.ProjectDir,
  private val buildDir: BaseDir.BuildDir,
) : Closeable {

  private val tables: Tables by lazy(NONE) { Tables.fromFile(binaryFile) }

  /** Source files that don't have any source files of their own. */
  val rootSourceFiles: Set<AbsoluteFile>
    get() = tables.getSourceFilesWithoutGeneratedContent()
      .mapNotNullToSet { it.absolute() }

  private val absoluteToBaseDir = mutableMapOf<AbsoluteFile, Pair<BaseDir, RelativeFile>>()

  // We use this list to find the base directory of a given absolute file,
  // and we want it to be the closest parent. If the build directory is a child of the project
  // directory (it probably is), we want to find the build directory first.
  // Sorting by the path ensures that we find the closest parent first.
  private val baseDirs = listOf(buildDir, projectDir).sortedByDescending { it.file }

  private fun AbsoluteFile.relative(): RelativeFile = when (this) {
    AbsoluteFile.ANY_ABSOLUTE -> RelativeFile.ANY_RELATIVE
    else -> {
      val (_, relative) = absoluteToBaseDir.getOrPut(this) {
        val base = baseDirs.first { file.startsWith(it.file) }

        val relative = relativeTo(base)
        tables.putBaseDirType(relative, base.type)

        base to relative
      }
      relative
    }
  }

  private fun RelativeFile.absolute(): AbsoluteFile = when (this) {
    RelativeFile.ANY_RELATIVE -> AbsoluteFile.ANY_ABSOLUTE
    else -> {
      val baseDir = tables.getBaseDirType(this).dir
      baseDir.resolve(this)
    }
  }

  private fun AbsoluteFile.relativeTo(baseDir: BaseDir): RelativeFile =
    RelativeFile(file.relativeTo(baseDir.file))

  fun getContent(generatedFile: AbsoluteFile): String {
    check(generatedFile != AbsoluteFile.ANY_ABSOLUTE) {
      "ANY_ABSOLUTE is not a valid source file."
    }

    return tables.getGeneratedContent(generatedFile.relative())
  }

  fun getGeneratedFiles(sourceFile: AbsoluteFile): Set<AbsoluteFile> {
    return tables.getGeneratedFiles(sourceFile.relative())
      .mapToSet { it.absolute() }
  }

  fun getGeneratedFilesRecursive(sourceFile: AbsoluteFile): Set<AbsoluteFile> {

    val visited = mutableSetOf<AbsoluteFile>()

    return generateSequence(getGeneratedFiles(sourceFile)) { generated ->
      generated.filter { visited.add(it) }
        .flatMapTo(mutableSetOf()) { getGeneratedFiles(it) }
        .takeIf { it.iterator().hasNext() }
    }
      .flatten()
      .toSet()
  }

  fun addSourceFile(sourceFile: AbsoluteFile) {
    addMd5(sourceFile, overwrite = true)
  }

  private fun addMd5(sourceFile: AbsoluteFile, overwrite: Boolean) {
    if (overwrite) {
      tables.putMd5(sourceFile.relative(), sourceFile.md5())
    } else {
      tables.putMd5IfAbsent(sourceFile.relative()) { sourceFile.md5() }
    }
  }

  fun addGeneratedFile(generated: GeneratedFileWithSources) {

    val generatedAbsolute = AbsoluteFile(generated.file)
    val generatedRelative = generatedAbsolute.relative()

    tables.putGeneratedContent(generatedRelative, generated.content)

    addMd5(generatedAbsolute, overwrite = true)

    val sourceFiles = generated.sourceFiles
      .map(::AbsoluteFile)
      .ifEmpty { listOf(AbsoluteFile.ANY_ABSOLUTE) }

    for (source in sourceFiles) {

      val hasCycle = getGeneratedFilesRecursive(generatedAbsolute).any { it == source }

      if (hasCycle) {
        throw AnvilCompilationException(
          """
          Adding this mapping would create a cycle.
             source path: ${source.file}
          generated path: ${generated.file}
          """.trimIndent(),
        )
      }

      val sourceRelative = source.relative()

      tables.putSourceGeneratedPair(sourceFile = sourceRelative, generatedFile = generatedRelative)

      // At this point, any source file should be in the md5 map already.
      // But in case it isn't, we'll add it now.
      addMd5(source, overwrite = false)
    }
  }

  fun removeSource(sourceFile: AbsoluteFile) {
    val sourceRelative = sourceFile.relative()

    // All generated files that claim this source file as a source.
    val generated = tables.getGeneratedFiles(sourceRelative)

    for (gen in generated.toList()) {

      tables.deleteSourceGeneratedPair(sourceFile = sourceRelative, generatedFile = gen)

      // For any generated file that has multiple sources,
      // remove that generated file from the "generated" set for those other sources.
      // Note that this does not call `removeSource` for that other source.
      for (otherSource in tables.getSourceFiles(gen)) {
        if (otherSource != sourceRelative) {
          tables.deleteSourceGeneratedPair(otherSource, gen)
        }
      }

      // Treat this generated file as a source file,
      // removing it and any files that were generated because of it.
      removeSource(gen.absolute())
    }

    tables.deleteFile(sourceRelative, fileStillExists = sourceFile.exists())
  }

  /**
   * Returns `true` if the [sourceFile] has changed since the last time it was added to the cache.
   */
  fun hasChanged(sourceFile: AbsoluteFile): Boolean {
    if (sourceFile == AbsoluteFile.ANY_ABSOLUTE) return true
    if (!sourceFile.exists()) return true
    val currentMd5 = sourceFile.md5()
    val previousMd5 = tables.putMd5(sourceFile.relative(), currentMd5)
    return previousMd5 != currentMd5
  }

  private fun AbsoluteFile.md5(): MD5String = when (this) {
    AbsoluteFile.ANY_ABSOLUTE -> ""
    else -> MessageDigest.getInstance("MD5")
      .digest(file.readBytes())
      .joinToString("") { "%02x".format(it) }
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

  private val BaseDir.type: BaseDirType
    get() = when (this) {
      is BaseDir.ProjectDir -> BaseDirType.PROJECT
      is BaseDir.BuildDir -> BaseDirType.BUILD
    }
  private val BaseDirType.dir: BaseDir
    get() = when (this) {
      BaseDirType.PROJECT -> projectDir
      BaseDirType.BUILD -> buildDir
    }

  /**
   * We can't cache base directories, since those are machine-specific
   * and it would break remote build caching.
   * Instead, we cache which base directory a path is relative to,
   * then when we restore from the cache,
   * we replace that "type" token with the corresponding directory from the current environment.
   */
  enum class BaseDirType {
    PROJECT,
    BUILD,
  }

  private class Tables private constructor(
    val fileIds: MutableBiMap<RelativeFile, FileId>,
    val generatedToContent: MutableMap<FileId, String>,
    val filesToMd5: MutableMap<FileId, MD5String>,
    val relativeToBaseDir: MutableMap<FileId, BaseDirType>,
    val sourcesToGenerated: Multimap<FileId, FileId>,
    val generatedToSources: Multimap<FileId, FileId>,
  ) : Serializable {

    constructor() : this(
      fileIds = MutableBiMap(),
      generatedToContent = mutableMapOf(),
      filesToMd5 = mutableMapOf(),
      relativeToBaseDir = mutableMapOf(),
      sourcesToGenerated = Multimap(),
      generatedToSources = Multimap(),
    )

    private val random = Random(System.currentTimeMillis())

    private val RelativeFile.id: FileId
      get() = fileIds.getOrPut(this@id) {
        val inverse = fileIds.inverse
        sequence {
          while (true) {
            yield(random.nextInt())
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

    fun deleteFile(file: RelativeFile, fileStillExists: Boolean) {
      val id = file.id
      sourcesToGenerated.remove(id)
      generatedToSources.remove(id)
      generatedToContent.remove(id)
      filesToMd5.remove(id)

      if (!fileStillExists) {
        relativeToBaseDir.remove(id)
        fileIds.remove(file)
      }
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

    fun putBaseDirType(relativeFile: RelativeFile, baseDirType: BaseDirType): BaseDirType? {
      return relativeToBaseDir.put(relativeFile.id, baseDirType)
    }

    fun getBaseDirType(relativeFile: RelativeFile): BaseDirType {
      return relativeToBaseDir.getValue(relativeFile.id)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Tables) return false

      if (generatedToContent != other.generatedToContent) return false
      if (sourcesToGenerated != other.sourcesToGenerated) return false
      if (generatedToSources != other.generatedToSources) return false
      if (filesToMd5 != other.filesToMd5) return false
      if (fileIds != other.fileIds) return false
      if (relativeToBaseDir != other.relativeToBaseDir) return false

      return true
    }

    override fun hashCode(): Int {
      var result = generatedToContent.hashCode()
      result = 31 * result + sourcesToGenerated.hashCode()
      result = 31 * result + generatedToSources.hashCode()
      result = 31 * result + filesToMd5.hashCode()
      result = 31 * result + fileIds.hashCode()
      result = 31 * result + relativeToBaseDir.hashCode()
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
      projectDir: BaseDir.ProjectDir,
      buildDir: BaseDir.BuildDir,
    ): GeneratedFileCache = GeneratedFileCache(
      binaryFile = binaryFile,
      projectDir = projectDir,
      buildDir = buildDir,
    )
  }
}

private fun BaseDir.resolve(relativePath: RelativeFile): AbsoluteFile {
  return AbsoluteFile(file.resolve(relativePath.file))
}

/**
 * All cached files must be relative to work with Gradle remote build caches.
 * They are all relative to the [project directory][BaseDir.ProjectDir],
 * which mirrors what Gradle does with relative file inputs to tasks.
 */
@JvmInline
private value class RelativeFile(val file: File) : Serializable, Comparable<RelativeFile> {
  override fun compareTo(other: RelativeFile): Int = file.compareTo(other.file)
  override fun toString(): String = "RelativeFile: $file"

  companion object {
    val ANY_RELATIVE = RelativeFile(File("<any source file>"))
  }
}
