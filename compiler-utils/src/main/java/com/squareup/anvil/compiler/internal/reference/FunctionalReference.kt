package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.FunctionalReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.FunctionalReference.Psi
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFunction

@ExperimentalAnvilApi
public sealed interface FunctionalReference {
  public val fqName: FqName
  public val name: String get() = fqName.shortName().asString()

  public val module: AnvilModuleDescriptor

  public val parameters: List<ParameterReference>

  public fun returnTypeOrNull(): TypeReference?
  public fun returnType(): TypeReference = returnTypeOrNull()
    ?: throw AnvilCompilationExceptionFunctionalReference(
      functionalReference = this,
      message = "Unable to get the return type for function $fqName."
    )

  public fun visibility(): Visibility

  public sealed interface Psi : FunctionalReference {
    public val function: KtFunction
  }

  public sealed interface Descriptor : FunctionalReference {
    public val function: FunctionDescriptor
  }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionFunctionalReference(
  functionalReference: FunctionalReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (functionalReference) {
  is Psi -> AnvilCompilationException(
    element = functionalReference.function,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    functionDescriptor = functionalReference.function,
    message = message,
    cause = cause
  )
}
