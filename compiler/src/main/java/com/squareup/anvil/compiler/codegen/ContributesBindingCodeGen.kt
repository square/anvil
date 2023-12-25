package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.internal.BindingPriority
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.GeneratedMethod.BindingMethod
import com.squareup.anvil.compiler.codegen.GeneratedMethod.ProviderMethod
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.checkClassExtendsBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.ksp.checkNoDuplicateScopeAndBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneMapKey
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneQualifier
import com.squareup.anvil.compiler.codegen.ksp.checkSingleSuperType
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationPresent
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Companion
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Module
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * @see ContributesBinding
 * @see ContributesMultibinding
 */
internal object ContributesBindingCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  /**
   * @param scopesAndReplaces a mapping of scopes to [ContributesBinding.replaces] and [ContributesMultibinding.replaces] elements
   */
  fun generate(
    originClass: ClassName,
    scopesAndReplaces: Map<ClassName, List<ClassName>>,
    contributedBinding: ContributedBinding,
  ): FileSpec {
    val moduleClassName = originClass.generateClassName(suffix = "Module")
    val generatedPackage = generatePackageName(moduleClassName)
    val generatedMethod = contributedBinding.toGeneratedMethod()

    return FileSpec.createAnvilSpec(generatedPackage, moduleClassName.simpleName) {
      val builder = if (generatedMethod is ProviderMethod) {
        TypeSpec.objectBuilder(moduleClassName)
          .addFunction(generatedMethod.spec)
      } else {
        TypeSpec.classBuilder(moduleClassName)
          .addModifiers(ABSTRACT)
          .addFunction(generatedMethod.spec)
      }
      addType(
        builder
          .apply {
            for ((scope, replaces) in scopesAndReplaces) {
              addAnnotation(
                AnnotationSpec.builder(ContributesTo::class)
                  .addMember("%T::class", scope)
                  .apply {
                    if (replaces.isNotEmpty()) {
                      addMember(
                        "replaces = %L",
                        replaces.map { CodeBlock.of("%T::class", it) }.joinToCode(
                          separator = ", ",
                          prefix = "[",
                          suffix = "]"
                        ),
                      )
                    }
                  }
                  .build()
              )
              if (!contributedBinding.isMultibinding) {
                addAnnotation(
                  AnnotationSpec.builder(BindingPriority::class)
                    .addMember("%T::class", contributedBinding.boundType)
                    .addMember("%T.%N", Priority::class.asClassName(), contributedBinding.priority.name)
                    .build()
                )
              }
            }
          }
          .build()
      )
    }
  }

  private fun generatePackageName(clazz: ClassName): String = MODULE_PACKAGE_PREFIX +
    clazz.packageName.safePackageString(dotPrefix = true)

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      val contributesBindingNodes =
        resolver
          .getSymbolsWithAnnotation(contributesBindingFqName.asString())
          .map { it to false }
      val contributesMultibindingNodes =
        resolver
          .getSymbolsWithAnnotation(contributesMultibindingFqName.asString())
          .map { it to true }

      (contributesBindingNodes + contributesMultibindingNodes)
        .mapNotNull { (annotated, isMultibinding) ->
          val annotationString = if (isMultibinding) {
            contributesMultibindingFqName.shortName().asString()
          } else {
            contributesBindingFqName.shortName().asString()
          }
          when {
            annotated !is KSClassDeclaration -> {
              env.logger.error(
                "Only classes can be annotated with @$annotationString.",
                annotated,
              )
              return@mapNotNull null
            }
            else -> annotated to isMultibinding
          }
        }
        .forEach { (clazz, isMultibinding) ->
          val targetAnnotation = if (isMultibinding) {
            ContributesMultibinding::class
          } else {
            ContributesBinding::class
          }

          // Checks
          clazz.checkClassIsPublic {
            "${clazz.qualifiedName?.asString()} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(targetAnnotation.fqName)
          clazz.checkSingleSuperType(targetAnnotation.fqName, resolver)
          clazz.checkClassExtendsBoundType(targetAnnotation.fqName, resolver)
          if (isMultibinding) {
            clazz.checkNotMoreThanOneMapKey()
          }

          val annotations = clazz.getKSAnnotationsByType(targetAnnotation)
            .toList()
            .ifEmpty { return@forEach }

          val scopesAndReplaces = annotations
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .associate {
              it.scope().toClassName() to it.replaces().map { it.toClassName() }
            }
            // Give it a stable sort.
            .toSortedMap(compareBy { it.canonicalName })

          val contributedBinding = annotations[0].toContributedBinding(isMultibinding, resolver)

          val className = clazz.toClassName()
          val spec = generate(className, scopesAndReplaces, contributedBinding)

          spec.writeTo(
            env.codeGenerator,
            aggregating = false,
            originatingKSFiles = listOf(clazz.containingFile!!),
          )
        }

      return emptyList()
    }

    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(ContributesBindingCodeGen, ::KspGenerator)
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext) =
      ContributesBindingCodeGen.isApplicable(context)

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFile> {

      return projectFiles
        .classAndInnerClassReferences(module)
        .mapNotNull {
          when {
            it.isAnnotatedWith(contributesBindingFqName) -> it to false
            it.isAnnotatedWith(contributesMultibindingFqName) -> it to true
            else -> null
          }
        }
        .mapNotNull { (clazz, isMultibinding) ->
          val targetAnnotation = if (isMultibinding) {
            contributesMultibindingFqName
          } else {
            contributesBindingFqName
          }

          // Checks
          clazz.checkClassIsPublic {
            "${clazz.fqName} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(targetAnnotation)
          clazz.checkSingleSuperType(targetAnnotation)
          clazz.checkClassExtendsBoundType(targetAnnotation)
          if (isMultibinding) {
            clazz.checkNotMoreThanOneMapKey()
          }

          val annotations = clazz.annotations
            .find(targetAnnotation)
            .ifEmpty { return@mapNotNull null }

          val scopesAndReplaces = annotations
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .associate {
              it.scope().asClassName() to it.replaces().map { it.asClassName() }
            }
            // Give it a stable sort.
            .toSortedMap(compareBy { it.canonicalName })

          val contributedBinding = annotations[0].toContributedBinding(isMultibinding, module)
          val className = clazz.asClassName()
          val spec = generate(className, scopesAndReplaces, contributedBinding)

          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = spec.packageName,
            fileName = spec.name,
            content = spec.toString(),
          )
        }
        .toList()
    }
  }
}
