package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.CommandLineOptions
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.codegen.incremental.AbsoluteFile
import com.squareup.anvil.compiler.codegen.incremental.BaseDir
import com.squareup.anvil.compiler.codegen.incremental.FileCacheOperations
import com.squareup.anvil.compiler.codegen.incremental.GeneratedFileCache
import com.squareup.anvil.compiler.codegen.incremental.GeneratedFileCache.Companion.binaryFile
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.mapToSet
import com.squareup.anvil.compiler.requireDelete
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.AnalysisResult.RetryWithAdditionalRoots
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

internal class CodeGenerationExtension(
  codeGenerators: List<CodeGenerator>,
  private val commandLineOptions: CommandLineOptions,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
  private val projectDir: BaseDir.ProjectDir,
  private val buildDir: BaseDir.BuildDir,
  private val generatedDir: File,
  private val cacheDir: File,
  private val trackSourceFiles: Boolean,
) : AnalysisHandlerExtension {

  private val generatedFileCache by lazy(NONE) {
    GeneratedFileCache.fromFile(
      binaryFile = binaryFile(cacheDir),
      projectDir = projectDir,
      buildDir = buildDir,
    )
  }

  private val cacheOperations by lazy(NONE) {
    FileCacheOperations(
      cache = generatedFileCache,
    )
  }

  private var didRecompile = false
  private var didSyncGeneratedDir = false

  private val codeGenerators = codeGenerators
    .onEach {
      check(it !is FlushingCodeGenerator || it !is PrivateCodeGenerator) {
        "A code generator can't be a private code generator and flushing code generator at the " +
          "same time. Private code generators don't impact other code generators, therefore " +
          "they shouldn't need to flush files after other code generators generated code."
      }
    }
    // Use a stable sort in case code generators depend on the order.
    // At least don't make it random.
    .sortedWith(compareBy({ it is PrivateCodeGenerator }, { it::class.qualifiedName }))

  override fun doAnalysis(
    project: Project,
    module: ModuleDescriptor,
    projectContext: ProjectContext,
    files: Collection<KtFile>,
    bindingTrace: BindingTrace,
    componentProvider: ComponentProvider,
  ): AnalysisResult? {
    // If we already went through the `analysisCompleted` stage, then there's nothing more to do.
    // Returning null ends the cycle.
    return if (!didRecompile) AnalysisResult.EMPTY else null
  }

  override fun analysisCompleted(
    project: Project,
    module: ModuleDescriptor,
    bindingTrace: BindingTrace,
    files: Collection<KtFile>,
  ): AnalysisResult? {

    // Either sync the local file state with our internal cache,
    // or delete any existing generated files (if caching isn't enabled).
    val syncCreatedChanges = syncGeneratedDir(files)
    didSyncGeneratedDir = true

    if (syncCreatedChanges) {
      // Tell the compiler to analyze the generated files *before* calling `analysisCompleted()`.
      // This ensures that the compiler will update/delete any references to stale code in the
      // ModuleDescriptor, as well as updating/deleting any stale .class files.
      // Without redoing the analysis, the PSI and Descriptor APIs are out of sync.
      // See discussion here: https://github.com/square/anvil/pull/877#discussion_r1517184297
      return RetryWithAdditionalRoots(
        bindingContext = bindingTrace.bindingContext,
        moduleDescriptor = module,
        additionalJavaRoots = emptyList(),
        additionalKotlinRoots = listOf(generatedDir),
        addToEnvironment = true,
      )
    }

    if (didRecompile) return null
    didRecompile = true

    // The files in `files` can include generated files.
    // Those files must exist when they're passed in to `anvilModule.addFiles(...)` to avoid:
    // https://youtrack.jetbrains.com/issue/KT-49340
    val psiManager = PsiManager.getInstance(project)
    val anvilModule = moduleDescriptorFactory.create(module)
    anvilModule.addFiles(files)

    val generatedFiles = generateCode(
      psiManager = psiManager,
      anvilModule = anvilModule,
    )

    if (trackSourceFiles) {

      val filesAsAbsoluteFiles = files.map { AbsoluteFile(File(it.virtualFilePath)) }

      // Add all generated files to the cache.
      cacheOperations.addToCache(
        sourceFiles = filesAsAbsoluteFiles,
        filesWithSources = generatedFiles.filterIsInstance<GeneratedFileWithSources>(),
      )
    }

    // This restarts the analysis phase and will include our files.
    return RetryWithAdditionalRoots(
      bindingContext = bindingTrace.bindingContext,
      moduleDescriptor = anvilModule,
      additionalJavaRoots = emptyList(),
      // Return the entire `generatedDir` as additional roots so that any generated files *or
      // restored files* are added/updated for the next analysis.
      // If we only return individual files, then any restored files will not be picked up and added
      // to the final jar.
      // see https://github.com/square/anvil/issues/876
      additionalKotlinRoots = listOf(generatedDir),
      addToEnvironment = true,
    )
  }

  private fun syncGeneratedDir(files: Collection<KtFile>): Boolean {
    return when {
      // If we already synced the generated directory (in an earlier round),
      // don't sync again or it'll delete files that were generated in this round.
      didSyncGeneratedDir -> false
      // For incremental compilation: restore any missing generated files from the cache,
      // and delete any generated files that are out-of-date due to source changes.
      shouldRestoreFromCache() -> {
        cacheOperations.restoreFromCache(
          generatedDir = generatedDir,
          inputKtFiles = files.mapToSet {
            AbsoluteFile(File(it.virtualFilePath))
          },
        )
          .run { restoredFiles.isNotEmpty() || deletedFiles.isNotEmpty() }
      }
      // OLD incremental behavior: just delete everything if we're not tracking source files
      else -> {
        generatedDir.walkBottomUp()
          .filter { it.isFile }
          .also { it.forEach(File::requireDelete) }
          .any()
      }
    }
  }

  private fun shouldRestoreFromCache(): Boolean {
    return when {
      didSyncGeneratedDir -> false
      !trackSourceFiles -> false
      // If caching is enabled but the cache doesn't exist,
      // we still need to treat any files in the generated directory as untracked.
      // The cache file will be created at the end of onAnalysisCompleted.
      !binaryFile(cacheDir).exists() -> false
      else -> true
    }
  }

  private fun generateCode(
    psiManager: PsiManager,
    anvilModule: RealAnvilModuleDescriptor,
  ): MutableCollection<FileWithContent> {

    val anvilContext = commandLineOptions.toAnvilContext(anvilModule)

    val generatedFiles = mutableMapOf<String, FileWithContent>()

    val (privateCodeGenerators, nonPrivateCodeGenerators) =
      codeGenerators
        .filter { it.isApplicable(anvilContext) }
        .partition { it is PrivateCodeGenerator }

    fun onGenerated(
      generatedFile: FileWithContent,
      codeGenerator: CodeGenerator,
      allowOverwrites: Boolean,
    ) {

      checkNoUntrackedSources(
        generatedFile = generatedFile,
        codeGenerator = codeGenerator,
      )
      val relativePath = generatedFile.file.relativeTo(generatedDir).path

      val alreadyGenerated = generatedFiles.put(relativePath, generatedFile)

      if (alreadyGenerated != null && !allowOverwrites) {
        checkNoOverwrites(
          alreadyGenerated = alreadyGenerated,
          generatedFile = generatedFile,
          codeGenerator = codeGenerator,
        )
      }
    }

    fun Collection<CodeGenerator>.generateAndCache(files: Collection<KtFile>): List<KtFile> =
      flatMap { codeGenerator ->
        codeGenerator.generateCode(generatedDir, anvilModule, files)
          .onEach { file ->
            onGenerated(
              generatedFile = file,
              codeGenerator = codeGenerator,
              allowOverwrites = false,
            )
          }
          .toKtFiles(psiManager, anvilModule)
      }

    fun Collection<FlushingCodeGenerator>.flush(): List<KtFile> =
      flatMap { codeGenerator ->
        codeGenerator.flush(generatedDir, anvilModule)
          .onEach {
            onGenerated(
              generatedFile = it,
              codeGenerator = codeGenerator,
              // flushing code generators write the files but no content during normal rounds.
              allowOverwrites = true,
            )
          }
          .toKtFiles(psiManager, anvilModule)
      }

    fun List<CodeGenerator>.loopGeneration() {
      var newFiles = generateAndCache(anvilModule.allFiles.toList())
      while (newFiles.isNotEmpty()) {
        // Parse the KtFile for each generated file. Then feed the code generators with the new
        // parsed files until no new files are generated.
        newFiles = generateAndCache(newFiles)
      }
    }

    // All non-private code generators are batched together.
    // They will execute against the initial set of files,
    // then loop until no generator produces any new files.
    nonPrivateCodeGenerators.loopGeneration()

    // Flushing generators are next.
    // They have already seen all generated code.
    // Their output may be consumed by a private generator.
    codeGenerators.filterIsInstance<FlushingCodeGenerator>().flush()

    // Private generators do not affect each other, so they're invoked last.
    // They may require multiple iterations of their own logic, though,
    // so we loop them individually until there are no more changes.
    privateCodeGenerators.forEach { listOf(it).loopGeneration() }

    return generatedFiles.values
  }

  private fun Collection<FileWithContent>.toKtFiles(
    psiManager: PsiManager,
    anvilModule: RealAnvilModuleDescriptor,
  ): List<KtFile> = mapNotNull { (file, content) ->
    val virtualFile = LightVirtualFile(
      // This must stay an absolute path, or `psiManager.findFile(...)` won't be able to resolve it.
      file.path,
      KotlinFileType.INSTANCE,
      content,
    )

    psiManager.findFile(virtualFile)
  }
    .filterIsInstance<KtFile>()
    .also { anvilModule.addFiles(it) }

  private fun checkNoOverwrites(
    alreadyGenerated: FileWithContent,
    generatedFile: FileWithContent,
    codeGenerator: CodeGenerator,
  ) {

    if (alreadyGenerated.content != generatedFile.content) {
      throw AnvilCompilationException(
        """
        |There were duplicate generated files. Generating and overwriting the same file leads to unexpected results.
        |
        |The file was generated by: ${codeGenerator::class}
        |The file is: ${generatedFile.file.path}
        |
        |The content of the already generated file is:
        |
        |${alreadyGenerated.content.prependIndent("\t")}
        |
        |The content of the new file is:
        |
        |${generatedFile.content.prependIndent("\t")}
        """.trimMargin(),
      )
    }
  }

  private fun checkNoUntrackedSources(
    generatedFile: FileWithContent,
    codeGenerator: CodeGenerator,
  ) {
    if (!trackSourceFiles) return

    if (generatedFile !is GeneratedFileWithSources) {
      throw AnvilCompilationException(
        """
        |Source file tracking is enabled but this generated file is not tracking them.
        |Please report this issue to the code generator's maintainers.
        |
        |The file was generated by: ${codeGenerator::class}
        |The file is: ${generatedFile.file.path}
        |
        |To stop this error, disable the `trackSourceFiles` property in the Anvil Gradle extension:
        |
        |   // build.gradle(.kts)
        |   anvil {
        |     trackSourceFiles = false
        |   }
        |
        |or disable the property in `gradle.properties`:
        |
        |   # gradle.properties
        |   com.squareup.anvil.trackSourceFiles=false
        |
        """.trimMargin(),
      )
    }
  }
}
