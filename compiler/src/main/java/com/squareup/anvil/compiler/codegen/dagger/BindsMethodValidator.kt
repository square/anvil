package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.daggerBindsFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.toTypeReference
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
@AutoService(CodeGenerator::class)
internal class BindsMethodValidator : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext) = context.generateFactories

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    projectFiles
      .classAndInnerClassReferences(module)
      .filter { it.isAnnotatedWith(daggerModuleFqName) }
      .forEach { clazz ->
        (clazz.companionObjects() + clazz)
          .asSequence()
          .flatMap { it.functions }
          .filter { it.isAnnotatedWith(daggerBindsFqName) }
          .also { functions ->
            assertNoDuplicateFunctions(clazz, functions)
          }
          .forEach { function ->
            validateBindsFunction(function)
          }
      }
  }

  private fun validateBindsFunction(function: FunctionReference.Psi) {
    if (!function.isAbstract()) {
      throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods must be abstract",
        functionReference = function
      )
    }

    val hasSingleBindingParameter =
      (function.parameters.size == 1 && !function.function.isExtensionDeclaration()) ||
        (function.parameters.isEmpty() && function.function.isExtensionDeclaration())
    if (!hasSingleBindingParameter) {
      throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type",
        functionReference = function
      )
    }

    function.returnTypeOrNull() ?: throw AnvilCompilationExceptionFunctionReference(
      message = "@Binds methods must return a value (not void)",
      functionReference = function
    )

    if (!function.parameterMatchesReturnType() && !function.receiverMatchesReturnType()) {
      val returnType = function.returnType().asClassReference().shortName
      val paramSuperTypes = (function.parameterSuperTypes() ?: function.receiverSuperTypes())!!
        .map { it.shortName }
        .toList()

      throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods' parameter type must be assignable to the return type. " +
          "Expected return type of $returnType but impl parameter of type " +
          "${paramSuperTypes.first()} only has the following supertypes: $paramSuperTypes",
        functionReference = function
      )
    }
  }

  private fun FunctionReference.Psi.parameterMatchesReturnType(): Boolean {
    return parameterSuperTypes()
      ?.contains(returnType().asClassReference())
      ?: false
  }

  private fun FunctionReference.Psi.parameterSuperTypes(): Sequence<ClassReference>? {
    return parameters.singleOrNull()
      ?.type()
      ?.asClassReference()
      ?.allSuperTypeClassReferences(includeSelf = true)
  }

  private fun FunctionReference.Psi.receiverMatchesReturnType(): Boolean {
    return receiverSuperTypes()
      ?.contains(returnType().asClassReference())
      ?: false
  }

  private fun FunctionReference.Psi.receiverSuperTypes(): Sequence<ClassReference>? {
    return function.receiverTypeReference
      ?.toTypeReference(declaringClass)
      ?.asClassReference()
      ?.allSuperTypeClassReferences(includeSelf = true)
  }
}
