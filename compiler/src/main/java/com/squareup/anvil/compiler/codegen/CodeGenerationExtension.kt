package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
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

internal class CodeGenerationExtension(
  private val codeGenDir: File,
  private val codeGenerators: List<CodeGenerator>
) : AnalysisHandlerExtension {

  private var didRecompile = false

  override fun doAnalysis(
    project: Project,
    module: ModuleDescriptor,
    projectContext: ProjectContext,
    files: Collection<KtFile>,
    bindingTrace: BindingTrace,
    componentProvider: ComponentProvider
  ): AnalysisResult? {
    // Tell the compiler that we have something to do in the analysisCompleted() method if
    // necessary.
    return if (!didRecompile && codeGenerators.isNotEmpty()) AnalysisResult.EMPTY else null
  }

  override fun analysisCompleted(
    project: Project,
    module: ModuleDescriptor,
    bindingTrace: BindingTrace,
    files: Collection<KtFile>
  ): AnalysisResult? {
    if (didRecompile || codeGenerators.isEmpty()) return null
    didRecompile = true

    codeGenDir.listFiles()
      ?.forEach {
        check(it.deleteRecursively()) {
          "Could not clean file: $it"
        }
      }

    val psiManager = PsiManager.getInstance(project)
    val anvilModule = AnvilModuleDescriptor(module, emptyList())

    fun generateCode(files: Collection<KtFile>): Collection<GeneratedFile> =
      codeGenerators
        .sortedBy { it is PrivateCodeGenerator }
        .flatMap { codeGenerator ->
          codeGenerator.generateCode(codeGenDir, anvilModule, files).also { new ->
            anvilModule.addFiles(new.parse(psiManager))
          }
        }

    var newFiles = generateCode(files)
    while (newFiles.isNotEmpty()) {
      // Parse the KtFile for each generated file. Then feed the code generators with the new
      // parsed files until no new files are generated.
      newFiles = generateCode(newFiles.parse(psiManager))
    }

    val flushedFiles = codeGenerators
      .filterIsInstance<FlushingCodeGenerator>()
      .flatMap { codeGenerator -> codeGenerator.flush(codeGenDir, anvilModule) }
      .parse(psiManager)

    anvilModule.addFiles(flushedFiles)

    codeGenerators
      .filterIsInstance<PrivateCodeGenerator>()
      .forEach { codeGenerator ->
        codeGenerator.generateCode(codeGenDir, anvilModule, flushedFiles).let { new ->
          anvilModule.addFiles(new.parse(psiManager))
        }
      }

    codeGenerators
      .filterIsInstance<FlushingCodeGenerator>()
      .forEach { codeGenerator ->
        codeGenerator.flush(codeGenDir, anvilModule).let { new ->
          anvilModule.addFiles(new.parse(psiManager))
        }
      }

    // This restarts the analysis phase and will include our files.
    return RetryWithAdditionalRoots(
      bindingContext = bindingTrace.bindingContext,
      moduleDescriptor = anvilModule,
      additionalJavaRoots = emptyList(),
      additionalKotlinRoots = listOf(codeGenDir),
      addToEnvironment = true
    )
  }

  private fun Collection<GeneratedFile>.parse(psiManager: PsiManager): Collection<KtFile> =
    mapNotNull { (file, content) ->
      val virtualFile = LightVirtualFile(
        file.relativeTo(codeGenDir).path,
        KotlinFileType.INSTANCE,
        content
      )

      psiManager.findFile(virtualFile)
    }.filterIsInstance<KtFile>()
}
