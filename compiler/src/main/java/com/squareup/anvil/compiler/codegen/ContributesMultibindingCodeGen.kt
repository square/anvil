package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesMultibinding
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
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneMapKey
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneQualifier
import com.squareup.anvil.compiler.codegen.ksp.checkSingleSuperType
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.ignoreQualifier
import com.squareup.anvil.compiler.codegen.ksp.isMapKey
import com.squareup.anvil.compiler.codegen.ksp.qualifierAnnotation
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveBoundType
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesMultibindingFqName
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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Generates binding modules for every [ContributesMultibinding]-annotated class. If a class has repeated
 * annotations, a binding module will be generated for each contribution. Each generated module is
 * annotated with [ContributesTo] for merging.
 */
internal object ContributesMultibindingCodeGen : AnvilApplicabilityChecker {

  private data class Contribution(
    val scope: ClassName,
    val boundType: ClassName,
    val replaces: List<ClassName>,
    val qualifier: QualifierData?,
    val mapKey: AnnotationSpec?,
  ) {
    data class QualifierData(
      val annotationSpec: AnnotationSpec,
      val key: String,
    )
    companion object {
      val COMPARATOR = compareBy<Contribution> { it.scope.canonicalName }
        .thenComparing(compareBy { it.boundType.canonicalName })
        .thenComparing(compareBy { it.replaces.joinToString { it.canonicalName } })
    }
  }

  private fun generate(
    className: ClassName,
    contributions: List<Contribution>,
  ): FileSpec {
    val fileName = className.generateClassName(suffix = "MultiBindingModule").simpleName
    val generatedPackage = className.packageName.safePackageString(dotPrefix = true)

    val specs = contributions.map { contribution ->
      // Combination name of origin, scope, and boundType
      val suffix = "As" +
        contribution.boundType.simpleName.capitalize() +
        "To" +
        contribution.scope.simpleName.capitalize() +
        "MultiBindingModule"

      val contributionName =
        className.generateClassName(suffix = suffix).simpleName
      TypeSpec.interfaceBuilder(contributionName).apply {
        addAnnotation(Module::class)
        addAnnotation(
          AnnotationSpec.builder(ContributesTo::class)
            .addMember("scope = %T::class", contribution.scope)
            .apply {
              if (contribution.replaces.isNotEmpty()) {
                addMember(
                  "replaces = %L",
                  contribution.replaces.map { CodeBlock.of("%T::class") }
                    .joinToCode(prefix = "[", suffix = "]"),
                )
              }
            }
            .build(),
        )

        addAnnotation(
          AnnotationSpec.builder(
            InternalBindingMarker::class.asClassName()
              .parameterizedBy(contribution.boundType, className),
          )
            .apply {
              contribution.qualifier?.key?.let { qualifierKey ->
                addMember("qualifierKey = %S", qualifierKey)
              }
            }
            .build(),
        )

        addFunction(
          FunSpec.builder("bind")
            .addModifiers(KModifier.ABSTRACT)
            .addAnnotation(Binds::class)
            .apply {
              if (contribution.mapKey == null) {
                addAnnotation(IntoSet::class)
              } else {
                addAnnotation(contribution.mapKey)
              }
              contribution.qualifier?.let { addAnnotation(it.annotationSpec) }
            }
            .addParameter("real", className)
            .returns(contribution.boundType)
            .build(),
        )
      }.build()
    }
    return FileSpec.createAnvilSpec(generatedPackage, fileName) {
      addTypes(specs.sortedBy { it.name })
    }
  }

  override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.getSymbolsWithAnnotation(ContributesMultibinding::class.java.canonicalName)
        .forEach { clazz ->
          if (clazz !is KSClassDeclaration) {
            env.logger.error(
              "@${ContributesMultibinding::class.simpleName} can only be applied to classes",
              clazz,
            )
            return@forEach
          }
          clazz.checkClassIsPublic {
            "${clazz.qualifiedName!!.asString()} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(contributesMultibindingFqName)
          clazz.checkNotMoreThanOneMapKey()
          clazz.checkSingleSuperType(contributesMultibindingFqName, resolver)
          clazz.checkClassExtendsBoundType(contributesMultibindingFqName, resolver)

          // All good, generate away
          val className = clazz.toClassName()
          val contributions = clazz.getKSAnnotationsByType(ContributesMultibinding::class)
            .toList()
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .map {
              val scope = it.scope().toClassName()
              val boundType = it.resolveBoundType(resolver, clazz).toClassName()
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
              val mapKey = clazz.getKSAnnotationsByType(ContributesMultibinding::class)
                .filter { it.isMapKey() }
                .singleOrNull()
                ?.toAnnotationSpec()
              Contribution(scope, boundType, replaces, qualifierData, mapKey)
            }
            .distinct()
            // Give it a stable sort.
            .sortedWith(Contribution.COMPARATOR)

          generate(className, contributions)
            .writeTo(
              codeGenerator = env.codeGenerator,
              aggregating = false,
              originatingKSFiles = listOf(clazz.containingFile!!),
            )
        }

      return emptyList()
    }

    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(ContributesMultibindingCodeGen, ::KspGenerator)
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean =
      ContributesMultibindingCodeGen.isApplicable(context)

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): List<GeneratedFileWithSources> {
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
            .map {
              val scope = it.scope().asClassName()
              val boundType = it.resolveBoundType().asClassName()
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
              val mapKey = clazz.annotations
                .find { it.isMapKey() }
                ?.toAnnotationSpec()
              Contribution(scope, boundType, replaces, qualifierData, mapKey)
            }
            .distinct()
            // Give it a stable sort.
            .sortedWith(Contribution.COMPARATOR)

          val spec = generate(className, scopes)

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
