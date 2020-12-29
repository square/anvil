package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.HINT_CONTRIBUTES_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import dagger.Module
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File

/**
 * Generates a hint for each contributed class in the `hint.anvil` packages. This allows the
 * compiler plugin to find all contributed classes a lot faster when merging modules and component
 * interfaces.
 */
internal class ContributesToGenerator : CodeGenerator {
  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles.asSequence()
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
      .map { clazz ->
        val packageName = clazz.containingKtFile.packageFqName.asString()
        val generatedPackage = "$HINT_CONTRIBUTES_PACKAGE_PREFIX.$packageName"
        val className = clazz.requireFqName()
          .asString()

        val directory = File(codeGenDir, generatedPackage.replace('.', File.separatorChar))
        val file = File(directory, "${className.replace('.', '_')}.kt")
        check(file.parentFile.exists() || file.parentFile.mkdirs()) {
          "Could not generate package directory: ${file.parentFile}"
        }

        val scope = clazz.scope(contributesToFqName, module)

        val content =
          """
              package $generatedPackage
              
              public val ${className.replace(
            '.',
            '_'
          )}$REFERENCE_SUFFIX: kotlin.reflect.KClass<$className> = $className::class
              public val ${className.replace(
            '.',
            '_'
          )}$SCOPE_SUFFIX: kotlin.reflect.KClass<$scope> = $scope::class
          """.trimIndent()
        file.writeText(content)

        GeneratedFile(file, content)
      }
      .toList()
  }
}
