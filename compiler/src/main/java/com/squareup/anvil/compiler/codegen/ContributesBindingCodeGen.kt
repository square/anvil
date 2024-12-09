package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
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
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.qualifierKey
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
