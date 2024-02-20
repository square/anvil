package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.internal.InternalBindingMarker
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
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.qualifierKey
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates binding modules for every [ContributesBinding]-annotated class. If a class has repeated
 * annotations, a binding module will be generated for each contribution. Each generated module is
 * annotated with [ContributesTo] for merging.
 *
 * [ContributesBinding.priority] is conveyed in the generated module via [InternalBindingMarker]
 * annotation generated onto the binding module.
 */
internal object ContributesBindingCodeGen : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  private data class Contribution(
    val scope: ClassName,
    val isObject: Boolean,
    val boundType: ClassName,
    val priority: ContributesBinding.Priority,
    val replaces: List<ClassName>,
    val qualifier: QualifierData?,
  ) {
    data class QualifierData(
      val annotationSpec: AnnotationSpec,
      val key: String,
    )
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

    val specs = contributions.map { contribution ->
      // Combination name of origin, scope, and boundType
      val suffix = "As" +
        contribution.boundType.simpleName.capitalize() +
        "To" +
        contribution.scope.simpleName.capitalize() +
        "BindingModule"

      val contributionName =
        originClass.generateClassName(suffix = suffix).simpleName
      TypeSpec.interfaceBuilder(contributionName).apply {
        addAnnotation(Module::class)
        addAnnotation(
          AnnotationSpec.builder(ContributesTo::class)
            .addMember("scope = %T::class", contribution.scope)
            .apply {
              if (contribution.replaces.isNotEmpty()) {
                addMember(
                  "replaces = %L",
                  contribution.replaces.map { CodeBlock.of("%T::class", it) }
                    .joinToCode(prefix = "[", suffix = "]"),
                )
              }
            }
            .build(),
        )
        addAnnotation(
          AnnotationSpec.builder(
            InternalBindingMarker::class.asClassName(),
          )
            .addMember("isMultibinding = false")
            .apply {
              contribution.qualifier?.key?.let { qualifierKey ->
                addMember("qualifierKey = %S", qualifierKey)
              }
            }
            .addMember(
              "priority = %S",
              contribution.priority.name,
            )
            .build(),
        )

        addFunction(
          FunSpec.builder("bind")
            .addModifiers(KModifier.ABSTRACT)
            .addAnnotation(Binds::class)
            .apply {
              contribution.qualifier?.let { addAnnotation(it.annotationSpec) }
            }
            .addParameter("real", originClass)
            .returns(contribution.boundType)
            .build(),
        )

        if (contribution.isObject) {
          addType(
            TypeSpec.companionObjectBuilder()
              .addFunction(
                FunSpec.builder("provide${originClass.simpleName.capitalize()}")
                  .addAnnotation(Provides::class)
                  .apply {
                    contribution.qualifier?.let { addAnnotation(it.annotationSpec) }
                  }
                  .returns(originClass)
                  .addStatement("return %T", originClass)
                  .build(),
              )
              .build(),
          )
        }
      }
        .build()
    }
    return FileSpec.createAnvilSpec(generatedPackage, fileName) {
      addTypes(specs.sortedBy { it.name })
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
              val qualifierData = if (it.ignoreQualifier()) {
                null
              } else {
                clazz.qualifierAnnotation()?.let { qualifierAnnotation ->
                  val annotationSpec = qualifierAnnotation.toAnnotationSpec()
                  val key = qualifierAnnotation.qualifierKey()
                  Contribution.QualifierData(annotationSpec, key)
                }
              }
              Contribution(
                scope,
                clazz.classKind == ClassKind.OBJECT,
                boundType,
                priority,
                replaces,
                qualifierData,
              )
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
            .map {
              val scope = it.scope().asClassName()
              val boundType = it.resolveBoundType().asClassName()
              val priority = it.priority()
              val replaces = it.replaces().map { it.asClassName() }
              val qualifierData = if (it.ignoreQualifier()) {
                null
              } else {
                clazz.qualifierAnnotation()?.let { qualifierAnnotation ->
                  val annotationSpec = qualifierAnnotation.toAnnotationSpec()
                  val key = qualifierAnnotation.qualifierKey()
                  Contribution.QualifierData(annotationSpec, key)
                }
              }
              Contribution(scope, clazz.isObject(), boundType, priority, replaces, qualifierData)
            }
            .distinct()
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
