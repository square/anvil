package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.BindingPriority
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.checkClassExtendsBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.ksp.checkNoDuplicateScopeAndBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneQualifier
import com.squareup.anvil.compiler.codegen.ksp.checkSingleSuperType
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.ignoreQualifier
import com.squareup.anvil.compiler.codegen.ksp.priority
import com.squareup.anvil.compiler.codegen.ksp.qualifierAnnotation
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveBoundType
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Binds
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates binding modules for every [ContributesBinding]-annotated class. If a class has repeated
 * annotations, a binding module will be generated for each contribution. Each generated module is
 * annotated with [ContributesTo] for merging.
 *
 * [ContributesBinding.priority] is conveyed in the generated module via [BindingPriority]
 * annotation generated onto the binding module.
 */
internal object ContributesBindingCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  private data class Contribution(
    val scope: ClassName,
    val boundType: ClassName,
    val priority: ContributesBinding.Priority,
    val replaces: List<ClassName>,
    val qualifier: AnnotationSpec?
  ) {
    companion object {
      val COMPARATOR = compareBy<Contribution> { it.scope.canonicalName }
        .thenComparing(compareBy { it.boundType.canonicalName })
        .thenComparing(compareBy { it.priority })
        .thenComparing(compareBy { it.replaces.joinToString { it.canonicalName } })
    }
  }

  private fun generate(
    originClass: ClassName,
    contributions: Iterable<Contribution>,
  ): FileSpec {
    val fileName = originClass.generateClassName(suffix = "BindingModule").simpleName
    val generatedPackage = originClass.packageName.safePackageString(dotPrefix = true)

    return FileSpec.createAnvilSpec(generatedPackage, fileName) {
      for (contribution in contributions) {
        // Combination name of origin, scope, and boundType
        val suffix = originClass.simpleName.capitalize() +
          contribution.scope.simpleName.capitalize() +
          contribution.boundType.simpleName.capitalize()

        val contributionName =
          originClass.generateClassName(suffix = "${suffix}BindingModule").simpleName
        TypeSpec.interfaceBuilder(contributionName).apply {
          addAnnotation(
            AnnotationSpec.builder(ContributesTo::class)
              .addMember("scope = %T", contribution.scope)
              .addMember("replaces = %L", contribution.replaces.map { CodeBlock.of("%T::class") }.joinToCode(prefix = "[", suffix = "]"))
              .build(),
          )
          addAnnotation(
            AnnotationSpec.builder(BindingPriority::class)
              .addMember(
                "priority = %T.%L",
                ContributesBinding.Priority::class,
                contribution.priority.name,
              )
              .build(),
          )

          addFunction(
            FunSpec.builder("bind")
              .addModifiers(KModifier.ABSTRACT)
              .addAnnotation(Binds::class)
              .apply {
                contribution.qualifier?.let { addAnnotation(it) }
              }
              .addParameter("real", originClass)
              .returns(contribution.boundType)
              .build(),
          )
        }
      }
    }
  }

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver
        .getSymbolsWithAnnotation(contributesBindingFqName.asString())
        .mapNotNull { annotated ->
          when {
            annotated !is KSClassDeclaration -> {
              env.logger.error(
                "Only classes can be annotated with @ContributesBinding.",
                annotated,
              )
              return@mapNotNull null
            }

            else -> annotated
          }
        }
        .onEach {
          it.checkClassIsPublic {
            "${it.qualifiedName?.asString()} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          it.checkNotMoreThanOneQualifier(contributesBindingFqName)
          it.checkSingleSuperType(contributesBindingFqName, resolver)
          it.checkClassExtendsBoundType(contributesBindingFqName, resolver)
        }
        .forEach { clazz ->
          val className = clazz.toClassName()

          val contributions = clazz.getKSAnnotationsByType(ContributesBinding::class)
            .toList()
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .map {
              val scope = it.scope().toClassName()
              val boundType = it.resolveBoundType(resolver, clazz).toClassName()
              val priority = it.priority()
              val replaces = it.replaces().map { it.toClassName() }
              val qualifier = if (it.ignoreQualifier()) {
                null
              } else {
                clazz.qualifierAnnotation()?.toAnnotationSpec()
              }
              Contribution(scope, boundType, priority, replaces, qualifier)
            }
            .distinct()
            // Give it a stable sort.
            .sortedWith(Contribution.COMPARATOR)

          val spec = generate(className, contributions)

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
    ): List<GeneratedFileWithSources> {
      return projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(contributesBindingFqName) }
        .onEach { clazz ->
          clazz.checkClassIsPublic {
            "${clazz.fqName} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(contributesBindingFqName)
          clazz.checkSingleSuperType(contributesBindingFqName)
          clazz.checkClassExtendsBoundType(contributesBindingFqName)
        }
        .map { clazz ->
          val className = clazz.asClassName()

          val contributions = clazz.annotations
            .find(contributesBindingFqName)
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .distinctBy { it.scope() }
            .map {
              val scope = it.scope().asClassName()
              val boundType = it.boundTypeOrNull()?.asClassName()
                ?: clazz.directSuperTypeReferences()
                  .singleOrNull()
                  ?.asClassReference()
                  ?.asClassName()
                ?: throw AnvilCompilationExceptionClassReference(
                  message = "Couldn't resolve bound type for ${clazz.fqName}",
                  classReference = clazz,
                )
              val priority = it.priority()
              val replaces = it.replaces().map { it.asClassName() }
              val qualifier = if (it.ignoreQualifier()) {
                null
              } else {
                clazz.qualifierAnnotation()?.toAnnotationSpec()
              }
              Contribution(scope, boundType, priority, replaces, qualifier)
            }
            // Give it a stable sort.
            .sortedWith(Contribution.COMPARATOR)

          val spec = generate(className, contributions)

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
