package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.daggerBindsFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionFunctionReference
import com.squareup.anvil.compiler.internal.reference.FunctionReference
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
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

    if (function.parameters.size != 1) {
      throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods must have exactly one parameter, " +
          "whose type is assignable to the return type",
        functionReference = function
      )
    }

    val returnType =
      function.returnTypeOrNull() ?: throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods must return a value (not void)",
        functionReference = function
      )
    val parameterMatchesReturnType = function.parameters.single()
      .type()
      .asClassReference()
      .allSuperTypeClassReferences(includeSelf = true)
      .contains(returnType.asClassReference())

    if (!parameterMatchesReturnType) {
      throw AnvilCompilationExceptionFunctionReference(
        message = "@Binds methods' parameter type must be assignable to the return type",
        functionReference = function
      )
    }
  }
}
