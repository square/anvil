package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.checkNotGeneric
import com.squareup.anvil.compiler.codegen.Contribution.Companion.generateFileSpecs
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.ksp.checkClassExtendsBoundType
import com.squareup.anvil.compiler.internal.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.internal.ksp.checkNoDuplicateScopeAndBoundType
import com.squareup.anvil.compiler.internal.ksp.checkNotMoreThanOneMapKey
import com.squareup.anvil.compiler.internal.ksp.checkNotMoreThanOneQualifier
import com.squareup.anvil.compiler.internal.ksp.checkSingleSuperType
import com.squareup.anvil.compiler.internal.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.internal.ksp.ignoreQualifier
import com.squareup.anvil.compiler.internal.ksp.isMapKey
import com.squareup.anvil.compiler.internal.ksp.qualifierAnnotation
import com.squareup.anvil.compiler.internal.ksp.replaces
import com.squareup.anvil.compiler.internal.ksp.resolvableAnnotations
import com.squareup.anvil.compiler.internal.ksp.resolveBoundType
import com.squareup.anvil.compiler.internal.ksp.scopeClassName
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.qualifierKey
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.properties.Delegates

/**
 * Generates binding modules for every [ContributesMultibinding]-annotated class. If a class has repeated
 * annotations, a binding module will be generated for each contribution. Each generated module is
 * annotated with [ContributesTo] for merging.
 */
internal object ContributesMultibindingCodeGen : AnvilApplicabilityChecker {

  // Used to determine if this generator needs to take responsibility
  // for generating a factory for `@Provides` functions.
  // https://github.com/square/anvil/issues/948
  private var willHaveDaggerFactories: Boolean by Delegates.notNull()

  override fun isApplicable(context: AnvilContext): Boolean {
    willHaveDaggerFactories = context.willHaveDaggerFactories
    return !context.generateFactoriesOnly
  }

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
          val contributions = clazz.getKSAnnotationsByType(ContributesMultibinding::class)
            .toList()
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .map {
              val scope = it.scopeClassName()

              val boundTypeDeclaration = it.resolveBoundType(resolver, clazz)
              boundTypeDeclaration.checkNotGeneric(clazz)
              val boundType = boundTypeDeclaration.toClassName()
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
              val mapKey = clazz.resolvableAnnotations
                .filter { it.isMapKey() }
                .singleOrNull()
                ?.toAnnotationSpec()
              Contribution.MultiBinding(
                clazz.toClassName(),
                scope,
                clazz.classKind == ClassKind.OBJECT,
                boundType,
                replaces,
                qualifierData,
                mapKey,
              )
            }

          contributions
            .generateFileSpecs(generateProviderFactories = !willHaveDaggerFactories)
            .forEach { spec ->
              spec.writeTo(
                codeGenerator = env.codeGenerator,
                aggregating = false,
                originatingKSFiles = listOf(clazz.containingFile!!),
              )
            }
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
        .flatMap { clazz ->
          val contributions = clazz.annotations
            .find(contributesMultibindingFqName)
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .map {
              val scope = it.scope().asClassName()
              // TODO if we support generic bound types in the future, we would change the below
              //  to use asTypeName() + remove the checkNotGeneric call.
              val boundTypeReference = it.resolveBoundType()
              boundTypeReference.checkNotGeneric(clazz)
              val boundType = boundTypeReference.asClassName()
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
              Contribution.MultiBinding(
                clazz.asClassName(),
                scope,
                clazz.isObject(),
                boundType,
                replaces,
                qualifierData,
                mapKey,
              )
            }

          contributions.generateFileSpecs(generateProviderFactories = !willHaveDaggerFactories)
            .map { spec ->
              createGeneratedFile(
                codeGenDir = codeGenDir,
                packageName = spec.packageName,
                fileName = spec.name,
                content = spec.toString(),
                sourceFile = clazz.containingFileAsJavaFile,
              )
            }
        }
        .toList()
    }
  }
}
