package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.reference.classesAndInnerClasses
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed class in the `anvil.hint.bindings` package. This allows
 * the compiler plugin to find all contributed bindings a lot faster when merging modules and
 * component interfaces.
 */
@AutoService(CodeGenerator::class)
internal class ContributesBindingGenerator : CodeGenerator {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    return projectFiles
      .classesAndInnerClasses(module)
      .filter { it.hasAnnotation(contributesBindingFqName, module) }
      .onEach { clazz ->
        clazz.checkClassIsPublic()
        clazz.checkNotMoreThanOneQualifier(module, contributesBindingFqName)
        clazz.checkSingleSuperType(module, contributesBindingFqName)
        clazz.checkClassExtendsBoundType(module, contributesBindingFqName)
      }
      .map { clazz ->
        val fileName = clazz.generateClassName()
        val generatedPackage = HINT_BINDING_PACKAGE_PREFIX +
          clazz.containingKtFile.packageFqName.safePackageString(dotPrefix = true)
        val className = clazz.asClassName()
        val classFqName = clazz.requireFqName().toString()
        val propertyName = classFqName.replace('.', '_')
        val scope = clazz.scope(contributesBindingFqName, module).asClassName(module)

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

internal fun KtClassOrObject.checkNotMoreThanOneQualifier(
  module: ModuleDescriptor,
  annotationFqName: FqName
) {
  // The class is annotated with @ContributesBinding. If there is less than 2 further
  // annotations, then there can't be more than two qualifiers.
  if (annotationEntries.size <= 2) return

  val qualifiers = annotationEntries.filter { it.isQualifier(module) }

  if (qualifiers.size > 1) {
    throw AnvilCompilationException(
      message = "Classes annotated with @${annotationFqName.shortName()} may not use more " +
        "than one @Qualifier.",
      element = this
    )
  }
}

internal fun KtClassOrObject.checkClassIsPublic() {
  if (visibilityModifierTypeOrDefault().value != KtTokens.PUBLIC_KEYWORD.value) {
    throw AnvilCompilationException(
      "${requireFqName()} is binding a type, but the class is not public. Only " +
        "public types are supported.",
      element = identifyingElement
    )
  }
}

internal fun KtClassOrObject.checkSingleSuperType(
  module: ModuleDescriptor,
  annotationFqName: FqName
) {
  // If the bound type exists, then you're allowed to have multiple super types. Without the bound
  // type there must be exactly one super type.
  if (boundTypeOrNull(annotationFqName, module) != null) return

  val superTypes = superTypeListEntries
  if (superTypes.size != 1) {
    throw AnvilCompilationException(
      element = this,
      message = "${requireFqName()} contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @${annotationFqName.shortName()} annotation."
    )
  }
}

internal fun KtClassOrObject.checkClassExtendsBoundType(
  module: ModuleDescriptor,
  annotationFqName: FqName
) {
  // Check the super type through the Psi APIs first.
  val boundType = boundTypeOrNull(annotationFqName, module)
    ?: superTypeListEntries.singleOrNull()?.requireFqName(module)
    ?: throw AnvilCompilationException(
      element = this,
      message = "Couldn't find the bound type."
    )

  val hasSuperTypePsi = superTypeListEntries.any { it.fqNameOrNull(module) == boundType }
  if (hasSuperTypePsi) return

  val descriptor = classDescriptor(module)
  val hasSuperType = descriptor.getAllSuperClassifiers()
    .any { it.fqNameSafe == boundType }

  if (!hasSuperType) {
    throw AnvilCompilationException(
      classDescriptor = descriptor,
      message = "${descriptor.fqNameSafe} contributes a binding for $boundType, but doesn't " +
        "extend this type."
    )
  }
}
