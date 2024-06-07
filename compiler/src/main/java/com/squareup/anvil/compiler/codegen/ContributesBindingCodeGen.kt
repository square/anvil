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
import com.squareup.anvil.compiler.checkNotGeneric
import com.squareup.anvil.compiler.codegen.Contribution.Companion.generateFileSpecs
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.checkClassExtendsBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.ksp.checkNoDuplicateScopeAndBoundType
import com.squareup.anvil.compiler.codegen.ksp.checkNotMoreThanOneQualifier
import com.squareup.anvil.compiler.codegen.ksp.checkSingleSuperType
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.ignoreQualifier
import com.squareup.anvil.compiler.codegen.ksp.qualifierAnnotation
import com.squareup.anvil.compiler.codegen.ksp.rank
import com.squareup.anvil.compiler.codegen.ksp.replaces
import com.squareup.anvil.compiler.codegen.ksp.resolveBoundType
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
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
 * Generates binding modules for every [ContributesBinding]-annotated class. If a class has repeated
 * annotations, a binding module will be generated for each contribution. Each generated module is
 * annotated with [ContributesTo] for merging.
 *
 * [ContributesBinding.rank] is conveyed in the generated module via [InternalBindingMarker]
 * annotation generated onto the binding module.
 */
internal object ContributesBindingCodeGen : AnvilApplicabilityChecker {

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
          val contributions = clazz.getKSAnnotationsByType(ContributesBinding::class)
            .toList()
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .map {
              val scope = it.scope().toClassName()
              val boundTypeDeclaration = it.resolveBoundType(resolver, clazz)
              boundTypeDeclaration.checkNotGeneric(clazz)
              val boundType = boundTypeDeclaration.toClassName()
              val rank = it.rank()
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
              Contribution.Binding(
                clazz.toClassName(),
                scope,
                clazz.classKind == ClassKind.OBJECT,
                boundType,
                rank,
                replaces,
                qualifierData,
              )
            }

          contributions
            .generateFileSpecs(generateProviderFactories = !willHaveDaggerFactories)
            .forEach { spec ->
              spec.writeTo(
                env.codeGenerator,
                aggregating = false,
                originatingKSFiles = listOf(clazz.containingFile!!),
              )
            }
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
        .flatMap { clazz ->
          val contributions = clazz.annotations
            .find(contributesBindingFqName)
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .map {
              val scope = it.scope().asClassName()
              // TODO if we support generic bound types in the future, we would change the below
              //  to use asTypeName() + remove the checkNotGeneric call.
              val boundTypeReference = it.resolveBoundType()
              boundTypeReference.checkNotGeneric(clazz)
              val boundType = boundTypeReference.asClassName()
              val rank = it.rank()
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
              Contribution.Binding(
                origin = clazz.asClassName(),
                scope = scope,
                isObject = clazz.isObject(),
                boundType = boundType,
                rank = rank,
                replaces = replaces,
                qualifier = qualifierData,
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
