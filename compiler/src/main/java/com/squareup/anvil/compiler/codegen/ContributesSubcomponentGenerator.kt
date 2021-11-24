package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.compiler.HINT_SUBCOMPONENTS_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesSubcomponentFactoryFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isInterface
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed subcomponent in the `anvil.hint.subcomponent` packages.
 * This allows the compiler plugin to find all contributed classes a lot faster.
 */
@AutoService(CodeGenerator::class)
internal class ContributesSubcomponentGenerator : CodeGenerator {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classesAndInnerClass(module)
      .filter { it.hasAnnotation(contributesSubcomponentFqName, module) }
      .onEach { clazz ->
        if (!clazz.isInterface() && !clazz.hasModifier(ABSTRACT_KEYWORD)) {
          throw AnvilCompilationException(
            "${clazz.requireFqName()} is annotated with " +
              "@${ContributesSubcomponent::class.simpleName}, but this class is not an interface.",
            element = clazz.identifyingElement
          )
        }

        if (clazz.visibilityModifierTypeOrDefault().value != KtTokens.PUBLIC_KEYWORD.value) {
          throw AnvilCompilationException(
            "${clazz.requireFqName()} is contributed to the Dagger graph, but the " +
              "interface is not public. Only public interfaces are supported.",
            element = clazz.identifyingElement
          )
        }
      }
      .map { clazz ->
        val generatedPackage = HINT_SUBCOMPONENTS_PACKAGE_PREFIX +
          clazz.containingKtFile.packageFqName.safePackageString(dotPrefix = true)
        val className = clazz.asClassName()
        val classFqName = clazz.requireFqName().toString()
        val propertyName = classFqName.replace('.', '_')
        val parentScopeFqName = clazz.parentScope(contributesSubcomponentFqName, module)
        val parentScope = parentScopeFqName.asClassName(module)

        val innerClasses = clazz.childrenInBody.filterIsInstance<KtClassOrObject>()
        clazz.checkParentComponentInterface(innerClasses, module, parentScopeFqName)
        clazz.checkFactory(innerClasses, module)

        val content =
          FileSpec.buildFile(generatedPackage, propertyName) {
            addProperty(
              PropertySpec
                .builder(
                  name = propertyName + REFERENCE_SUFFIX,
                  type = KClass::class.asClassName().parameterizedBy(className)
                )
                .initializer("%T::class", className)
                .addModifiers(PUBLIC)
                .build()
            )

            addProperty(
              PropertySpec
                .builder(
                  name = propertyName + SCOPE_SUFFIX,
                  type = KClass::class.asClassName().parameterizedBy(parentScope)
                )
                .initializer("%T::class", parentScope)
                .addModifiers(PUBLIC)
                .build()
            )
          }

        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = generatedPackage,
          fileName = propertyName,
          content = content
        )
      }
      .toList()
  }

  private fun KtClassOrObject.checkParentComponentInterface(
    innerClasses: List<KtClassOrObject>,
    module: ModuleDescriptor,
    parentScope: FqName
  ) {
    val fqName = requireFqName()

    val parentComponents = innerClasses
      .filter {
        if (!it.hasAnnotation(contributesToFqName, module)) return@filter false
        it.scope(contributesToFqName, module) == parentScope
      }

    val componentInterface = when (parentComponents.size) {
      0 -> return
      1 -> parentComponents[0]
      else -> throw AnvilCompilationException(
        element = this,
        message = "Expected zero or one parent component interface within " +
          "${requireFqName()} being contributed to the parent scope."
      )
    }

    val functions = componentInterface.childrenInBody
      .filterIsInstance<KtFunction>()
      .filter { it.typeReference?.fqNameOrNull(module) == fqName }

    if (functions.size >= 2) {
      throw AnvilCompilationException(
        element = this,
        message = "Expected zero or one function returning the subcomponent $fqName."
      )
    }
  }

  private fun KtClassOrObject.checkFactory(
    innerClasses: List<KtClassOrObject>,
    module: ModuleDescriptor
  ) {
    val fqName = requireFqName()

    val factories = innerClasses
      .filter { it.hasAnnotation(contributesSubcomponentFactoryFqName, module) }

    val factory = when (factories.size) {
      0 -> return
      1 -> factories[0]
      else -> throw AnvilCompilationException(
        element = this,
        message = "Expected zero or one factory within ${requireFqName()}."
      )
    }

    if (!factory.isInterface() && !factory.hasModifier(ABSTRACT_KEYWORD)) {
      throw AnvilCompilationException(
        element = factory,
        message = "A factory must be an interface or an abstract class."
      )
    }

    val functions = factory.childrenInBody
      .filterIsInstance<KtFunction>()
      .let { functions ->
        if (factory.isInterface()) {
          functions
        } else {
          functions.filter { it.hasModifier(ABSTRACT_KEYWORD) }
        }
      }

    if (functions.size != 1 || functions[0].typeReference?.fqNameOrNull(module) != fqName) {
      throw AnvilCompilationException(
        element = factory,
        message = "A factory must have exactly one abstract function returning the " +
          "subcomponent $fqName."
      )
    }
  }

  private val KtClassOrObject.childrenInBody: List<PsiElement>
    get() = children.filterIsInstance<KtClassBody>().singleOrNull()?.children?.toList()
      ?: emptyList()
}
