package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.CheckOnlyCodeGenerator
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.getAnnotatedFunctions
import com.squareup.anvil.compiler.codegen.ksp.getAnnotatedSymbols
import com.squareup.anvil.compiler.codegen.ksp.isExtensionDeclaration
import com.squareup.anvil.compiler.codegen.ksp.resolveKSClassDeclaration
import com.squareup.anvil.compiler.codegen.ksp.returnTypeOrNull
import com.squareup.anvil.compiler.codegen.ksp.superTypesExcludingAny
import com.squareup.anvil.compiler.codegen.ksp.withCompanion
import com.squareup.anvil.compiler.daggerBindsFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.MemberFunctionReference
import com.squareup.anvil.compiler.internal.reference.TypeReference
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.asTypeName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.ksp.toTypeName
import dagger.Binds
import dagger.Module
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import java.io.File

/**
 * We do not need to generate any code for @Binds methods in the way that we do for @Provides
 * methods, but we still need to validate them since we are taking Dagger's place in evaluating them
 * for all compile-time needs. Without performing some validation here, it's possible a binding
 * that Dagger would consider a compile time failure would instead manifest as a runtime failure
 * when Anvil generates Dagger factories.
 */
internal object BindsMethodValidator : AnvilApplicabilityChecker {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  internal object Errors {
    internal const val BINDS_CANT_BE_AN_EXTENSION =
      "@Binds methods can not be an extension function"
    internal const val BINDS_MUST_BE_ABSTRACT = "@Binds methods must be abstract"
    internal const val BINDS_MUST_HAVE_SINGLE_PARAMETER =
      "@Binds methods must have exactly one parameter, " +
        "whose type is assignable to the return type"
    internal const val BINDS_MUST_RETURN_A_VALUE = "@Binds methods must return a value (not void)"
    internal fun bindsParameterMustBeAssignable(
      bindingParameterSuperTypeNames: List<String>,
      returnTypeName: String,
      parameterType: String?,
    ): String {
      val superTypesMessage = if (bindingParameterSuperTypeNames.isEmpty()) {
        "has no supertypes."
      } else {
        "only has the following supertypes: $bindingParameterSuperTypeNames"
      }

      return "@Binds methods' parameter type must be assignable to the return type. " +
        "Expected binding of type $returnTypeName but impl parameter of type " +
        "$parameterType $superTypesMessage"
    }
  }

  internal class KspValidator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(BindsMethodValidator, ::KspValidator)

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      val actualValidator = ActualValidator(resolver)
      resolver
        .getAnnotatedSymbols<Module>()
        .filterIsInstance<KSClassDeclaration>()
        .forEach { clazz ->
          clazz
            .withCompanion()
            .flatMap { it.getAnnotatedFunctions<Binds>() }
            .also {
              assertNoDuplicateFunctions(clazz, it)
            }
            .forEach { function ->
              actualValidator.validateBindsFunction(function)
            }
        }
      return emptyList()
    }

    internal class ActualValidator(
      private val resolver: Resolver,
    ) {
      internal fun validateBindsFunction(function: KSFunctionDeclaration) {
        if (!function.isAbstract) {
          throw KspAnvilException(
            message = Errors.BINDS_MUST_BE_ABSTRACT,
            node = function,
          )
        }

        if (function.isExtensionDeclaration()) {
          throw KspAnvilException(
            message = Errors.BINDS_CANT_BE_AN_EXTENSION,
            node = function,
          )
        }

        val bindingParameter = function.singleParameterOrNull()

        bindingParameter ?: throw KspAnvilException(
          message = Errors.BINDS_MUST_HAVE_SINGLE_PARAMETER,
          node = function,
        )

        val returnType = function.returnTypeOrNull()

        returnType ?: throw KspAnvilException(
          message = Errors.BINDS_MUST_RETURN_A_VALUE,
          node = function,
        )

        if (!returnType.isAssignableFrom(bindingParameter.resolve())) {
          val superTypeNames =
            bindingParameter
              .resolveSuperTypesExcludingAny()
              .map { it.toTypeName().toString() }
              .toList()

          throw KspAnvilException(
            message = Errors.bindsParameterMustBeAssignable(
              bindingParameterSuperTypeNames = superTypeNames,
              returnTypeName = returnType.toTypeName().toString(),
              parameterType = bindingParameter.toTypeName().toString(),
            ),
            node = function,
          )
        }
      }

      private fun KSTypeReference.resolveSuperTypesExcludingAny(): Sequence<KSType> {
        val clazz = resolve().resolveKSClassDeclaration() ?: return emptySequence()
        return clazz
          .superTypesExcludingAny(resolver)
          .map { it.resolve() }
      }

      private fun KSFunctionDeclaration.singleParameterOrNull(): KSTypeReference? =
        parameters.singleOrNull()?.type
    }
  }

  @AutoService(CodeGenerator::class)
  internal class Embedded : CheckOnlyCodeGenerator() {
    override fun isApplicable(context: AnvilContext): Boolean =
      BindsMethodValidator.isApplicable(context)

    override fun checkCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ) {
      projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(daggerModuleFqName) }
        .forEach { clazz ->
          (clazz.companionObjects() + clazz)
            .asSequence()
            .flatMap { it.declaredMemberFunctions }
            .filter { it.isAnnotatedWith(daggerBindsFqName) }
            .also { functions ->
              assertNoDuplicateFunctions(clazz, functions)
            }
            .forEach { function ->
              validateBindsFunction(function)
            }
        }
    }

    private fun validateBindsFunction(function: MemberFunctionReference.Psi) {
      if (!function.isAbstract()) {
        throw AnvilCompilationExceptionFunctionReference(
          message = Errors.BINDS_MUST_BE_ABSTRACT,
          functionReference = function,
        )
      }

      if (function.function.isExtensionDeclaration()) {
        throw AnvilCompilationExceptionFunctionReference(
          message = Errors.BINDS_CANT_BE_AN_EXTENSION,
          functionReference = function,
        )
      }

      val bindingParameter = function.singleParameterTypeOrNull()

      bindingParameter ?: throw AnvilCompilationExceptionFunctionReference(
        message = Errors.BINDS_MUST_HAVE_SINGLE_PARAMETER,
        functionReference = function,
      )

      val returnType = function.returnTypeOrNull()?.asClassReference()

      returnType ?: throw AnvilCompilationExceptionFunctionReference(
        message = Errors.BINDS_MUST_RETURN_A_VALUE,
        functionReference = function,
      )

      val superTypes = bindingParameter
        .asClassReference()
        .allSuperTypeClassReferences(includeSelf = true)

      if (returnType !in superTypes) {
        val superTypeNames = superTypes.map { it.asTypeName().toString() }

        throw AnvilCompilationExceptionFunctionReference(
          message = Errors.bindsParameterMustBeAssignable(
            bindingParameterSuperTypeNames = superTypeNames.drop(1).toList(),
            returnTypeName = returnType.asTypeName().toString(),
            parameterType = superTypeNames.first(),
          ),
          functionReference = function,
        )
      }
    }

    private fun MemberFunctionReference.Psi.singleParameterTypeOrNull(): TypeReference? {
      return parameters.singleOrNull()?.type()
    }
  }
}
