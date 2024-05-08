package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.HINT_PACKAGE
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.ksp.checkNoDuplicateScope
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.generateHintFileName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Module
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed class in the `hint.anvil` packages. This allows the
 * compiler plugin to find all contributed classes a lot faster when merging modules and component
 * interfaces.
 */
internal object ContributesToCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  fun generate(
    className: ClassName,
    scopes: List<ClassName>,
  ): FileSpec {
    val fileName = className.generateHintFileName(separator = "_", capitalizePackage = false)

    val classFqName = className.canonicalName
    val propertyName = classFqName.replace('.', '_')

    return FileSpec.createAnvilSpec(HINT_PACKAGE, fileName) {
      addProperty(
        PropertySpec
          .builder(
            name = propertyName + REFERENCE_SUFFIX,
            type = KClass::class.asClassName().parameterizedBy(className),
          )
          .initializer("%T::class", className)
          .addModifiers(PUBLIC)
          .build(),
      )

      scopes.forEachIndexed { index, scope ->
        addProperty(
          PropertySpec
            .builder(
              name = propertyName + SCOPE_SUFFIX + index,
              type = KClass::class.asClassName().parameterizedBy(scope),
            )
            .initializer("%T::class", scope)
            .addModifiers(PUBLIC)
            .build(),
        )
      }
    }
  }

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.getSymbolsWithAnnotation(ContributesTo::class.qualifiedName!!)
        .forEach { clazz ->
          if (clazz !is KSClassDeclaration) {
            env.logger.error(
              "@${ContributesTo::class.simpleName} can only be applied to classes",
              clazz,
            )
            return@forEach
          }
          if (!clazz.isInterface() &&
            !clazz.isAnnotationPresent(daggerModuleFqName.toString()) &&
            !clazz.isAnnotationPresent(mergeModulesFqName.toString())
          ) {
            throw KspAnvilException(
              message = "${clazz.qualifiedName!!.asString()} is annotated with " +
                "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
                "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
              node = clazz,
            )
          }
          clazz.checkClassIsPublic {
            "${clazz.qualifiedName!!.asString()} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported."
          }

          val scopes = clazz.getKSAnnotationsByType(ContributesTo::class)
            .toList()
            .also { it.checkNoDuplicateScope(annotatedType = clazz, isContributeAnnotation = true) }
            .map { it.scope().toClassName() }
            .distinct()
            // Give it a stable sort.
            .sortedBy { it.canonicalName }

          generate(clazz.toClassName(), scopes)
            .writeTo(
              codeGenerator = env.codeGenerator,
              aggregating = false,
              originatingKSFiles = listOf(clazz.containingFile!!),
            )
        }

      return emptyList()
    }

    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(ContributesToCodeGen, ::KspGenerator)
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean {
      return ContributesToCodeGen.isApplicable(context)
    }

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFileWithSources> {
      return projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(contributesToFqName) }
        .onEach { clazz ->
          if (!clazz.isInterface() &&
            !clazz.isAnnotatedWith(daggerModuleFqName) &&
            !clazz.isAnnotatedWith(mergeModulesFqName)
          ) {
            throw AnvilCompilationExceptionClassReference(
              classReference = clazz,
              message = "${clazz.fqName} is annotated with " +
                "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
                "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            )
          }

          if (clazz.visibility() != Visibility.PUBLIC) {
            throw AnvilCompilationExceptionClassReference(
              classReference = clazz,
              message = "${clazz.fqName} is contributed to the Dagger graph, but the " +
                "module is not public. Only public modules are supported.",
            )
          }
        }
        .map { clazz ->
          val scopes = clazz.annotations
            .find(contributesToFqName)
            .also { it.checkNoDuplicateScope(contributeAnnotation = true) }
            // Give it a stable sort.
            .sortedBy { it.scope() }
            .map { it.scope().asClassName() }

          val spec = generate(clazz.asClassName(), scopes)

          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = spec.packageName,
            fileName = spec.name,
            content = spec.toString(),
            sourceFile = clazz.containingFileAsJavaFile,
          )
        }
        .toList()
    }
  }
}
