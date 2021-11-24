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
  codeGenerators: List<CodeGenerator>
) : AnalysisHandlerExtension {

  private var didRecompile = false

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

    // It's important to resolve types before clearing the output directory
    // to avoid https://youtrack.jetbrains.com/issue/KT-49340
    val psiManager = PsiManager.getInstance(project)
    val anvilModule = RealAnvilModuleDescriptor(module)
    anvilModule.addFiles(files)

    codeGenDir.listFiles()
      ?.forEach {
        check(it.deleteRecursively()) {
          "Could not clean file: $it"
        }
      }

    val (privateCodeGenerators, nonPrivateCodeGenerators) =
      codeGenerators.partition { it is PrivateCodeGenerator }

    fun Collection<GeneratedFile>.toKtFile(): Collection<KtFile> {
      return this
        .mapNotNull { (file, content) ->
          val virtualFile = LightVirtualFile(
            file.relativeTo(codeGenDir).path,
            KotlinFileType.INSTANCE,
            content
          )

          psiManager.findFile(virtualFile)
        }
        .filterIsInstance<KtFile>()
        .also { anvilModule.addFiles(it) }
    }

    fun Collection<CodeGenerator>.generateCode(files: Collection<KtFile>): Collection<KtFile> =
      flatMap { codeGenerator ->
        codeGenerator.generateCode(codeGenDir, anvilModule, files).toKtFile()
      }

    fun Collection<CodeGenerator>.flush(): Collection<KtFile> =
      filterIsInstance<FlushingCodeGenerator>()
        .flatMap { codeGenerator ->
          codeGenerator.flush(codeGenDir, anvilModule).toKtFile()
        }

    var newFiles = nonPrivateCodeGenerators.generateCode(files)

    while (newFiles.isNotEmpty()) {
      // Parse the KtFile for each generated file. Then feed the code generators with the new
      // parsed files until no new files are generated.
      newFiles = nonPrivateCodeGenerators.generateCode(newFiles)
    }

    nonPrivateCodeGenerators.flush()

    // PrivateCodeGenerators don't impact other code generators. Therefore, they can be called a
    // single time at the end.
    privateCodeGenerators.generateCode(anvilModule.allFiles)

    // This restarts the analysis phase and will include our files.
    return RetryWithAdditionalRoots(
      bindingContext = bindingTrace.bindingContext,
      moduleDescriptor = anvilModule,
      additionalJavaRoots = emptyList(),
      additionalKotlinRoots = listOf(codeGenDir),
      addToEnvironment = true
    )
  }
}
