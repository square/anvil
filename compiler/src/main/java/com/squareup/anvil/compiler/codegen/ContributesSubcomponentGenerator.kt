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
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isInterface
import com.squareup.anvil.compiler.internal.parentScope
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.psi.KtFile
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
        val parentScope = clazz.parentScope(contributesSubcomponentFqName, module)
          .asClassName(module)

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
}
