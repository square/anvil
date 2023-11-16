package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Psi
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFunction

@ExperimentalAnvilApi
public sealed interface FunctionReference {
  public val fqName: FqName
  public val name: String get() = fqName.shortName().asString()

  public val module: AnvilModuleDescriptor

  public val parameters: List<ParameterReference>

  public fun returnTypeOrNull(): TypeReference?
  public fun returnType(): TypeReference = returnTypeOrNull()
    ?: throw AnvilCompilationExceptionFunctionReference(
      functionReference = this,
      message = "Unable to get the return type for function $fqName.",
    )

  public fun visibility(): Visibility

  public sealed interface Psi : FunctionReference {
    public val function: KtFunction
  }

  public sealed interface Descriptor : FunctionReference {
    public val function: FunctionDescriptor
  }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionFunctionReference(
  functionReference: FunctionReference,
  message: String,
  cause: Throwable? = null,
): AnvilCompilationException = when (functionReference) {
  is Psi -> AnvilCompilationException(
    element = functionReference.function,
    message = message,
    cause = cause,
  )
  is Descriptor -> AnvilCompilationException(
    functionDescriptor = functionReference.function,
    message = message,
    cause = cause,
  )
}
