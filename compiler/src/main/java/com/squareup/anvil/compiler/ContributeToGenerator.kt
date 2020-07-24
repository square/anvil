package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.AnalysisResult.RetryWithAdditionalRoots
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import java.io.File

/**
 * Generates a hint for each contributed class in the `hint.anvil` packages. This allows the
 * compiler plugin to find all contributed classes a lot faster when merging moduled and component
 * interfaces.
 */
/*
 * Note that this extension doesn't run when generating Java stubs for Kapt.
 */
internal class ContributeToGenerator(
  private val codeGenDir: File
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
    return if (!didRecompile) AnalysisResult.EMPTY else null
  }

  override fun analysisCompleted(
    project: Project,
    module: ModuleDescriptor,
    bindingTrace: BindingTrace,
    files: Collection<KtFile>
  ): AnalysisResult? {
    if (didRecompile) return null
    didRecompile = true

    codeGenDir.listFiles()
        ?.forEach {
          check(it.deleteRecursively()) {
            "Could not clean file: $it"
          }
        }

    files.asSequence()
        .flatMap { it.classesAndInnerClasses() }
        .filter { it.hasAnnotation(contributesToFqName) }
        .onEach { clazz ->
          if (!clazz.isInterface() && !clazz.hasAnnotation(daggerModuleFqName)) {
            throw AnvilCompilationException(
                "${clazz.requireFqName()} is annotated with " +
                    "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
                    "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
                element = clazz.identifyingElement
            )
          }

          if (clazz.visibilityModifierTypeOrDefault().value != KtTokens.PUBLIC_KEYWORD.value) {
            throw AnvilCompilationException(
                "${clazz.requireFqName()} is contributed to the Dagger graph, but the " +
                    "module is not public. Only public modules are supported.",
                element = clazz.identifyingElement
            )
          }
        }
        .forEach { clazz ->
          val packageName = clazz.containingKtFile.packageFqName.asString()
          val generatedPackage = "$HINT_PACKAGE_PREFIX.$packageName"
          val className = clazz.requireFqName()
              .asString()

          val directory = File(codeGenDir, generatedPackage.replace('.', File.separatorChar))
          val file = File(directory, "${className.replace('.', '_')}.kt")
              .also {
                check(it.parentFile.exists() || it.parentFile.mkdirs()) {
                  "Could not generate package directory: $this"
                }
              }

          file.writeText(
              """
          package $generatedPackage
          
          val ${className.replace('.', '_')} = $className::class
        """.trimIndent()
          )
        }

    // This restarts the analysis phase and will include our file.
    return RetryWithAdditionalRoots(
        bindingTrace.bindingContext, module, emptyList(), listOf(codeGenDir), true
    )
  }

  private fun KtFile.classesAndInnerClasses(): Sequence<KtClassOrObject> {
    val children = findChildrenByClass(KtClassOrObject::class.java)

    return generateSequence(children.toList()) { list ->
      list.flatMap {
            it.declarations.filterIsInstance<KtClassOrObject>()
          }
          .ifEmpty { null }
    }.flatMap { it.asSequence() }
  }

  private fun KtClassOrObject.requireFqName(): FqName = requireNotNull(fqName) {
    "fqName was null for $this, $nameAsSafeName"
  }

  private fun KtClassOrObject.isInterface(): Boolean = this is KtClass && this.isInterface()

  private fun KtClassOrObject.hasAnnotation(fqName: FqName): Boolean {
    val annotationEntries = annotationEntries
    if (annotationEntries.isEmpty()) return false

    // Check first if the fully qualified name is used, e.g. `@dagger.Module`.
    val containsFullyQualifiedName = annotationEntries.any {
      it.text.startsWith("@${fqName.asString()}")
    }
    if (containsFullyQualifiedName) return true

    // Check if the simple name is used, e.g. `@Module`.
    val containsShortName = annotationEntries.any {
      it.shortName == fqName.shortName()
    }
    if (!containsShortName) return false

    // If the simple name is used, check that the annotation is imported.
    return containingKtFile.importDirectives
        .mapNotNull { it.importPath }
        .any {
          it.fqName == fqName
        }
  }
}
