package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import java.io.File
import kotlin.reflect.KClass
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates a hint for each contributed class in the `anvil.hint.multibinding` package. This
 * allows the compiler plugin to find all contributed multibindings a lot faster when merging
 * modules and component interfaces.
 */
internal object ContributesMultibindingCodeGen : AnvilApplicabilityChecker {

  fun generate(
    className: ClassName,
    scopes: List<ClassName>
  ): FileSpec {
    val fileName = className.generateClassName().simpleName
    val generatedPackage = HINT_MULTIBINDING_PACKAGE_PREFIX +
            className.packageName.safePackageString(dotPrefix = true)
    val classFqName = className.canonicalName
    val propertyName = classFqName.replace('.', '_')

    return FileSpec.createAnvilSpec(generatedPackage, fileName) {
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

      scopes.forEachIndexed { index, scope ->
        addProperty(
          PropertySpec
            .builder(
              name = propertyName + SCOPE_SUFFIX + index,
              type = KClass::class.asClassName().parameterizedBy(scope)
            )
            .initializer("%T::class", scope)
            .addModifiers(PUBLIC)
            .build()
        )
      }
    }
  }

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = ContributesMultibindingCodeGen.isApplicable(context)

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>
    ): Collection<GeneratedFile> {
      return projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(contributesMultibindingFqName) }
        .onEach { clazz ->
          clazz.checkClassIsPublic {
            "${clazz.fqName} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(contributesMultibindingFqName)
          clazz.checkNotMoreThanOneMapKey()
          clazz.checkSingleSuperType(contributesMultibindingFqName)
          clazz.checkClassExtendsBoundType(contributesMultibindingFqName)
        }
        .map { clazz ->
          val className = clazz.asClassName()
          val scopes = clazz.annotations
            .find(contributesMultibindingFqName)
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .distinctBy { it.scope() }
            // Give it a stable sort.
            .sortedBy { it.scope() }
            .map { it.scope().asClassName() }


          val spec = generate(className, scopes)

          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = spec.packageName,
            fileName = spec.name,
            content = spec.toString()
          )
        }
        .toList()
    }
  }
}
