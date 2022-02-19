package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.FunctionReference.Psi
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.requireFqName
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PUBLIC_KEYWORD
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Used to create a common type between [KtNamedFunction] class references and
 * [FunctionDescriptor] references, to streamline parsing.
 */
@ExperimentalAnvilApi
public sealed class FunctionReference {

  public abstract val fqName: FqName
  public abstract val declaringClass: ClassReference
  public val name: String get() = fqName.shortName().asString()

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public abstract val annotations: List<AnnotationReference>
  public abstract val parameters: List<ParameterReference>

  public abstract fun isAbstract(): Boolean
  public abstract fun isConstructor(): Boolean
  public abstract fun visibility(): Visibility

  /**
   * The return type can be null for generic types like `T`. In this case try to resolve the
   * return type with [resolveGenericReturnTypeOrNull].
   */
  public abstract fun returnTypeOrNull(): ClassReference?

  public fun returnType(): ClassReference = returnTypeOrNull()
    ?: throw AnvilCompilationExceptionFunctionReference(
      functionReference = this,
      message = "Unable to get the return type for function $fqName."
    )

  public abstract fun resolveGenericReturnTypeOrNull(
    implementingClass: ClassReference.Psi
  ): ClassReference?

  public fun resolveGenericReturnType(implementingClass: ClassReference.Psi): ClassReference =
    resolveGenericReturnTypeOrNull(implementingClass)
      ?: throw AnvilCompilationExceptionFunctionReference(
        functionReference = this,
        message = "Unable to resolve return type for function $fqName with the implementing " +
          "class ${implementingClass.fqName}."
      )

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
    public val function: KtFunction,
    override val declaringClass: ClassReference.Psi,
    override val fqName: FqName
  ) : FunctionReference() {

    override val annotations: List<AnnotationReference.Psi> by lazy(NONE) {
      function.annotationEntries.map {
        it.toAnnotationReference(declaringClass = null, module)
      }
    }

    override val parameters: List<ParameterReference.Psi> by lazy(NONE) {
      function.valueParameters.map { it.toParameterReference(this) }
    }

    override fun isAbstract(): Boolean =
      function.hasModifier(ABSTRACT_KEYWORD) ||
        (declaringClass.isInterface() && !function.hasBody())

    override fun isConstructor(): Boolean = function is KtConstructor<*>

    override fun visibility(): Visibility {
      return when (val visibility = function.visibilityModifierTypeOrDefault()) {
        PUBLIC_KEYWORD -> PUBLIC
        KtTokens.INTERNAL_KEYWORD -> INTERNAL
        KtTokens.PROTECTED_KEYWORD -> PROTECTED
        KtTokens.PRIVATE_KEYWORD -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = declaringClass,
          message = "Couldn't get visibility $visibility for function $fqName."
        )
      }
    }

    override fun returnTypeOrNull(): ClassReference? {
      return function.typeReference
        ?.let {
          declaringClass.resolveTypeReference(it)
        }
        ?.requireFqName(module)
        ?.toClassReference(module)
    }

    override fun resolveGenericReturnTypeOrNull(
      implementingClass: ClassReference.Psi
    ): ClassReference? {
      returnTypeOrNull()?.let { return it }

      val typeReference = function.typeReference
        ?.let { typeReference ->
          implementingClass.resolveTypeReference(typeReference)
        }
        ?: function.typeReference

      return typeReference?.fqNameOrNull(module)?.toClassReference(module)
    }
  }

  public class Descriptor internal constructor(
    public val function: FunctionDescriptor,
    override val declaringClass: ClassReference.Descriptor,
    override val fqName: FqName = function.fqNameSafe
  ) : FunctionReference() {

    override val annotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      function.annotations.map {
        it.toAnnotationReference(declaringClass = null, module)
      }
    }

    override val parameters: List<ParameterReference.Descriptor> by lazy(NONE) {
      function.valueParameters.map { it.toParameterReference(this) }
    }

    override fun isAbstract(): Boolean = function.modality == ABSTRACT

    override fun isConstructor(): Boolean = function is ClassConstructorDescriptor

    override fun visibility(): Visibility {
      return when (val visibility = function.visibility) {
        DescriptorVisibilities.PUBLIC -> PUBLIC
        DescriptorVisibilities.INTERNAL -> INTERNAL
        DescriptorVisibilities.PROTECTED -> PROTECTED
        DescriptorVisibilities.PRIVATE -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = declaringClass,
          message = "Couldn't get visibility $visibility for function $fqName."
        )
      }
    }

    override fun returnTypeOrNull(): ClassReference? {
      return function.returnType?.classDescriptorOrNull()?.toClassReference(module)
    }

    override fun resolveGenericReturnTypeOrNull(
      implementingClass: ClassReference.Psi
    ): ClassReference? {
      returnTypeOrNull()?.let { return it }

      return function.returnType?.let {
        implementingClass
          .resolveGenericKotlinTypeOrNull(declaringClass, it)
          ?.fqNameOrNull(module)
          ?.toClassReferenceOrNull(module)
      }
    }
  }
}

@ExperimentalAnvilApi
public fun FunctionReference.isAnnotatedWith(fqName: FqName): Boolean =
  annotations.any { it.fqName == fqName }

@ExperimentalAnvilApi
public fun KtFunction.toFunctionReference(
  declaringClass: ClassReference.Psi
): Psi {
  val fqName = if (this is KtConstructor<*>) {
    declaringClass.fqName.child(Name.identifier("<init>"))
  } else {
    requireFqName()
  }

  return Psi(this, declaringClass, fqName)
}

@ExperimentalAnvilApi
public fun FunctionDescriptor.toFunctionReference(
  declaringClass: ClassReference.Descriptor,
): Descriptor {
  return Descriptor(this, declaringClass)
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionFunctionReference(
  functionReference: FunctionReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (functionReference) {
  is Psi -> AnvilCompilationException(
    element = functionReference.function,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    functionDescriptor = functionReference.function,
    message = message,
    cause = cause
  )
}
