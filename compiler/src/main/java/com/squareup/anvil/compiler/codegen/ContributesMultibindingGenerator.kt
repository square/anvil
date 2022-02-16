package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.isAnnotatedWith
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed class in the `anvil.hint.multibinding` package. This
 * allows the compiler plugin to find all contributed multibindings a lot faster when merging
 * modules and component interfaces.
 */
@AutoService(CodeGenerator::class)
internal class ContributesMultibindingGenerator : CodeGenerator {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(contributesMultibindingFqName) }
      .onEach { clazz ->
        clazz.checkClassIsPublic()
        clazz.checkNotMoreThanOneQualifier(contributesMultibindingFqName)
        clazz.checkNotMoreThanOneMapKey()
        clazz.checkSingleSuperType(contributesMultibindingFqName)
        clazz.checkClassExtendsBoundType(contributesMultibindingFqName)
      }
      .map { clazz ->
        val fileName = clazz.generateClassName()
        val generatedPackage = HINT_MULTIBINDING_PACKAGE_PREFIX +
          clazz.packageFqName.safePackageString(dotPrefix = true)
        val className = clazz.asClassName()
        val classFqName = clazz.fqName.toString()
        val propertyName = classFqName.replace('.', '_')
        val scope = clazz.annotations.single { it.fqName == contributesMultibindingFqName }
          .scope()
          .asClassName()

        val content =
          FileSpec.buildFile(generatedPackage, fileName) {
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
                  type = KClass::class.asClassName().parameterizedBy(scope)
                )
                .initializer("%T::class", scope)
                .addModifiers(PUBLIC)
                .build()
            )
          }

        createGeneratedFile(
          codeGenDir = codeGenDir,
          packageName = generatedPackage,
          fileName = fileName,
          content = content
        )
      }
      .toList()
  }
}
