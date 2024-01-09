package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.reference.TopLevelFunctionReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TopLevelFunctionReference.Psi
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class TopLevelFunctionReference : AnnotatedReference, FunctionReference {

  protected abstract val returnType: TypeReference?

  public override fun returnTypeOrNull(): TypeReference? = returnType

  override fun toString(): String = "$fqName()"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TopLevelFunctionReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }

  public class Psi internal constructor(
    public override val function: KtFunction,
    override val fqName: FqName,
    override val module: AnvilModuleDescriptor,
  ) : TopLevelFunctionReference(), FunctionReference.Psi {

    override val annotations: List<AnnotationReference.Psi> by lazy(NONE) {
      function.annotationEntries.map {
        it.toAnnotationReference(declaringClass = null, module)
      }
    }

    override val returnType: TypeReference.Psi? by lazy(NONE) {
      function.typeReference?.toTypeReference(declaringClass = null, module)
    }

    override val parameters: List<ParameterReference.Psi> by lazy(NONE) {
      function.valueParameters.map { it.toParameterReference(this) }
    }

    override fun visibility(): Visibility {
      return when (val visibility = function.visibilityModifierTypeOrDefault()) {
        KtTokens.PUBLIC_KEYWORD -> PUBLIC
        KtTokens.INTERNAL_KEYWORD -> INTERNAL
        KtTokens.PROTECTED_KEYWORD -> PROTECTED
        KtTokens.PRIVATE_KEYWORD -> PRIVATE
        else -> throw AnvilCompilationExceptionFunctionReference(
          functionReference = this,
          message = "Couldn't get visibility $visibility for function $fqName.",
        )
      }
    }
  }

  public class Descriptor internal constructor(
    public override val function: FunctionDescriptor,
    override val fqName: FqName = function.fqNameSafe,
    override val module: AnvilModuleDescriptor,
  ) : TopLevelFunctionReference(), FunctionReference.Descriptor {

    override val annotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      function.annotations.map {
        it.toAnnotationReference(declaringClass = null, module)
      }
    }

    override val parameters: List<ParameterReference.Descriptor> by lazy(NONE) {
      function.valueParameters.map { it.toParameterReference(this) }
    }

    override val returnType: TypeReference.Descriptor? by lazy(NONE) {
      function.returnType?.toTypeReference(declaringClass = null, module)
    }

    override fun visibility(): Visibility {
      return when (val visibility = function.visibility) {
        DescriptorVisibilities.PUBLIC -> PUBLIC
        DescriptorVisibilities.INTERNAL -> INTERNAL
        DescriptorVisibilities.PROTECTED -> PROTECTED
        DescriptorVisibilities.PRIVATE -> PRIVATE
        else -> throw AnvilCompilationExceptionFunctionReference(
          functionReference = this,
          message = "Couldn't get visibility $visibility for function $fqName.",
        )
      }
    }
  }
}

@ExperimentalAnvilApi
public fun KtFunction.toTopLevelFunctionReference(
  module: AnvilModuleDescriptor,
): Psi {
  return Psi(function = this, fqName = requireFqName(), module = module)
}

@ExperimentalAnvilApi
public fun FunctionDescriptor.toTopLevelFunctionReference(
  module: AnvilModuleDescriptor,
): Descriptor {
  return Descriptor(function = this, module = module)
}
