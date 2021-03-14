package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.codegen.CodeGenerator.GeneratedFile
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.isMapKey
import com.squareup.anvil.compiler.safePackageString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed class in the `anvil.hint.multibinding` package. This
 * allows the compiler plugin to find all contributed multibindings a lot faster when merging
 * modules and component interfaces.
 */
internal class ContributesMultibindingGenerator : CodeGenerator {
  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles.asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .filter { it.hasAnnotation(contributesMultibindingFqName) }
      .onEach { clazz ->
        clazz.checkClassIsPublic()
        clazz.checkNotMoreThanOneQualifier(module, contributesMultibindingFqName)
        clazz.checkNotMoreThanOneMapKey(module)
        clazz.checkSingleSuperType(contributesMultibindingFqName)
        clazz.checkClassExtendsBoundType(module, contributesMultibindingFqName)
      }
      .map { clazz ->
        val generatedPackage = HINT_MULTIBINDING_PACKAGE_PREFIX +
          clazz.containingKtFile.packageFqName.safePackageString(dotPrefix = true)
        val className = clazz.asClassName()
        val classFqName = clazz.requireFqName().toString()
        val propertyName = classFqName.replace('.', '_')
        val scope = clazz.scope(contributesMultibindingFqName, module).asClassName(module)

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
          fileName = propertyName,
          content = content
        )
      }
      .toList()
  }
}

private fun KtClassOrObject.checkNotMoreThanOneMapKey(
  module: ModuleDescriptor
) {
  // The class is annotated with @ContributesMultibinding. If there is less than 2 further
  // annotations, then there can't be more than two map keys.
  if (annotationEntries.size <= 2) return

  val mapKeys = requireClassDescriptor(module).annotations.filter { it.isMapKey() }

  if (mapKeys.size > 1) {
    throw AnvilCompilationException(
      message = "Classes annotated with @${contributesMultibindingFqName.shortName()} may not " +
        "use more than one @MapKey.",
      element = this
    )
  }
}
