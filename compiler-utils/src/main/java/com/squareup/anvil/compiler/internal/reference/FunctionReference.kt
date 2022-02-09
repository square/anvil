package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.resolveTypeReference
import org.jetbrains.kotlin.descriptors.EffectiveVisibility.Public
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PUBLIC_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/**
 * Used to create a common type between [KtNamedFunction] class references and
 * [FunctionDescriptor] references, to streamline parsing.
 */
@ExperimentalAnvilApi
public sealed class FunctionReference {

  public abstract val fqName: FqName
  public abstract val declaringClass: ClassReference

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  override fun toString(): String = "$fqName()"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }

  public class Psi internal constructor(
    public val function: KtNamedFunction,
    override val declaringClass: ClassReference.Psi,
    override val fqName: FqName = function.requireFqName()
  ) : FunctionReference()

  public class Descriptor internal constructor(
    public val function: FunctionDescriptor,
    override val declaringClass: ClassReference.Descriptor,
    override val fqName: FqName = function.fqNameSafe
  ) : FunctionReference()
}

@ExperimentalAnvilApi
public fun KtNamedFunction.toFunctionReference(
  declaringClass: ClassReference.Psi
): Psi {
  return Psi(this, declaringClass)
}

@ExperimentalAnvilApi
public fun FunctionDescriptor.toFunctionReference(
  declaringClass: ClassReference.Descriptor,
): Descriptor {
  return Descriptor(this, declaringClass)
}

@ExperimentalAnvilApi
public fun FunctionReference.isAbstract(): Boolean {
  return when (this) {
    is Psi -> function.hasModifier(ABSTRACT_KEYWORD) || declaringClass.isInterface()
    is Descriptor -> function.modality == ABSTRACT
  }
}

@ExperimentalAnvilApi
public fun FunctionReference.isPublic(): Boolean {
  return when (this) {
    is Psi -> function.visibilityModifierTypeOrDefault() == PUBLIC_KEYWORD
    is Descriptor -> function.effectiveVisibility() is Public
  }
}

public fun FunctionReference.returnType(): ClassReference {
  return when (this) {
    is Psi ->
      declaringClass.clazz
        .resolveTypeReference(module, function.requireTypeReference(module))
        ?.requireFqName(module)
        ?.toClassReference(module)
    is Descriptor -> function.returnType?.classDescriptor()?.toClassReference(module)
  } ?: throw AnvilCompilationException(message = "Couldn't find return type for $fqName.")
}
