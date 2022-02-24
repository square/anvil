package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.asTypeNameOrNull
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.TypeReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TypeReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.types.DefinitelyNotNullType
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class TypeReference {

  public abstract val declaringClass: ClassReference

  /**
   * If the type refers to class and is not a generic type, then the returned reference
   * is non-null.
   */
  protected abstract val classReference: ClassReference?

  protected abstract val typeName: TypeName?

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public fun asClassReferenceOrNull(): ClassReference? = classReference
  public fun asClassReference(): ClassReference = classReference
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a class reference."
    )

  public fun asTypeNameOrNull(): TypeName? = typeName
  public fun asTypeName(): TypeName = typeName
    ?: throw AnvilCompilationExceptionTypReference(
      typeReference = this,
      message = "Unable to convert a type reference to a type reference."
    )

  // Keep this internal, because we need help from other classes for the descriptor
  // implementation, e.g. FunctionReference resolves the return type or ParameterReference.
  internal abstract fun resolveGenericTypeOrNull(
    implementingClass: ClassReference.Psi
  ): TypeReference?

  internal fun resolveGenericTypeNameOrNull(
    implementingClass: ClassReference.Psi
  ): TypeName? {
    asClassReferenceOrNull()?.let {
      // Then it's a normal type and not a generic type.
      return asTypeName()
    }

    return resolveGenericTypeOrNull(implementingClass)
      ?.asTypeNameOrNull()
      ?: asTypeNameOrNull()
  }

  override fun toString(): String {
    return "${this::class.qualifiedName}(declaringClass=$declaringClass, " +
      "classReference=$classReference)"
  }

  public class Psi internal constructor(
    public val type: KtTypeReference,
    override val declaringClass: ClassReference.Psi
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      resolveGenericTypeOrNull(declaringClass)
        ?.type
        ?.requireFqName(module)
        ?.toClassReference(module)
    }

    override val typeName: TypeName? by lazy(NONE) {
      try {
        type.requireTypeName(module).lambdaFix()
      } catch (e: AnvilCompilationException) {
        // The goal is to inline the function above and then stop throwing an exception.
        null
      }
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): Psi? {
      return resolveTypeReference(implementingClass)
    }

    /**
     * Safely resolves a PSI [KtTypeReference], when that type reference may be a generic
     * expressed by a type variable name. This is done by inspecting the class hierarchy to
     * find where the generic type is declared, then resolving *that* reference.
     *
     * For instance, given:
     *
     * ```
     * interface Factory<T> {
     *   fun create(): T
     * }
     *
     * interface ServiceFactory : Factory<Service>
     * ```
     *
     * The KtTypeReference `T` will fail to resolve, since it isn't a type. This function will
     * instead look to the `ServiceFactory` interface, then look at the supertype declaration
     * in order to determine the type.
     *
     * @param implementingClass The class which actually references the type. In the above
     * example, this would be `ServiceFactory`.
     */
    internal fun resolveTypeReference(
      implementingClass: ClassReference.Psi,
    ): Psi? {
      // If the element isn't a type variable name like `T`, it can be resolved through imports.
      type.typeElement?.fqNameOrNull(module)
        ?.let { return this }

      return resolveGenericTypeReference(implementingClass, declaringClass, type.text)
    }
  }

  public class Descriptor internal constructor(
    public val type: KotlinType,
    override val declaringClass: ClassReference.Descriptor
  ) : TypeReference() {
    override val classReference: ClassReference? by lazy(NONE) {
      type.classDescriptorOrNull()?.toClassReference(module)
    }

    override val typeName: TypeName? by lazy(NONE) {
      type.asTypeNameOrNull()?.lambdaFix()
    }

    override fun resolveGenericTypeOrNull(implementingClass: ClassReference.Psi): TypeReference? {
      return resolveGenericKotlinTypeOrNull(implementingClass)
    }

    /**
     * Safely resolves a [KotlinType], when that type reference is a generic expressed by a type
     * variable name. This is done by inspecting the class hierarchy to find where the generic
     * type is declared, then resolving *that* reference.
     *
     * For instance, given:
     *
     * ```
     * interface SomeFactory : Function1<String, SomeClass>
     * ```
     *
     * There's an invisible `fun invoke(p1: P1): R`. If `SomeFactory` is parsed using PSI (such
     * as if it's generated), then the function can only be parsed to have the projected types
     * of `P1` and `R`
     *
     * @param implementingClass The class which actually references the type. In the above example,
     * this would be `SomeFactory`.
     */
    private fun resolveGenericKotlinTypeOrNull(
      implementingClass: ClassReference.Psi
    ): Psi? {
      val parameterKotlinType = when (type) {
        is FlexibleType -> {
          // A FlexibleType comes from Java code where the compiler doesn't know whether it's a
          // nullable or non-nullable type. toString() returns something like "(T..T?)". To get
          // proper results we use the type that's not nullable, "T" in this example.
          if (type.lowerBound.isMarkedNullable) {
            type.upperBound
          } else {
            type.lowerBound
          }
        }
        is DefinitelyNotNullType -> {
          // This is known to be not null in Java, such as something annotated `@NotNull` or
          // controlled by a JSR305 or jSpecify annotation.
          // This is a special type and this logic appears to match how kotlinc is handling it here
          // https://github.com/JetBrains/kotlin/blob/9ee0d6b60ac4f0ea0ccc5dd01146bab92fabcdf2/core/descriptors/src/org/jetbrains/kotlin/types/TypeUtils.java#L455-L458
          type.original
        }
        else -> type
      }

      return resolveGenericTypeReference(
        implementingClass = implementingClass,
        declaringClass = declaringClass,
        parameterName = parameterKotlinType.toString()
      )
    }
  }
}

@ExperimentalAnvilApi
public fun KtTypeReference.toTypeReference(declaringClass: ClassReference.Psi): Psi {
  return Psi(this, declaringClass)
}

@ExperimentalAnvilApi
public fun KotlinType.toTypeReference(declaringClass: ClassReference.Descriptor): Descriptor {
  return Descriptor(this, declaringClass)
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionTypReference(
  typeReference: TypeReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (typeReference) {
  is Psi -> AnvilCompilationException(
    element = typeReference.type,
    message = message,
    cause = cause
  )
  is Descriptor -> {
    AnvilCompilationException(
      classDescriptor = typeReference.declaringClass.clazz,
      message = message + " Hint: ${typeReference.type}",
      cause = cause
    )
  }
}

private fun resolveGenericTypeReference(
  implementingClass: ClassReference.Psi,
  declaringClass: ClassReference,
  parameterName: String
): Psi? {
  // If the class/interface declaring the generic is the receiver class,
  // then the generic hasn't been set to a concrete type and can't be resolved.
  if (implementingClass == declaringClass) return null

  // Used to determine which parameter to look at in a KtTypeArgumentList.
  val indexOfType = declaringClass.indexOfTypeParameter(parameterName)

  // Find where the supertype is actually declared by matching the FqName of the
  // SuperTypeListEntry to the type which declares the generic we're trying to resolve.
  // After finding the SuperTypeListEntry, just take the TypeReference from its type
  // argument list.
  val resolvedTypeReference = implementingClass.superTypeListEntryOrNull(declaringClass.fqName)
    ?.typeReference
    ?.typeElement
    ?.getChildOfType<KtTypeArgumentList>()
    ?.arguments
    ?.get(indexOfType)
    ?.typeReference

  return if (resolvedTypeReference != null) {
    // This will check that the type can be imported.
    val resolvedDeclaringClass = resolvedTypeReference.containingClass()
      ?.toClassReference(implementingClass.module)
      ?: return null

    resolvedTypeReference
      .toTypeReference(resolvedDeclaringClass)
      .resolveTypeReference(implementingClass)
  } else {
    null
  }
}

/**
 * Find where a super type is extended/implemented by matching the FqName of a SuperTypeListEntry to
 * the FqName of the target super type.
 *
 * For instance, given:
 *
 * ```
 * interface Base<T> {
 *   fun create(): T
 * }
 *
 * abstract class Middle : Comparable<MyClass>, Provider<Something>, Base<Something>
 *
 * class InjectClass : Middle()
 * ```
 *
 * We start at the declaration of `InjectClass`, looking for a super declaration of `Base<___>`.
 * Since `InjectClass` doesn't declare it, we look through the supers of `Middle` and find it, then
 * resolve `T` to `Something`.
 */
private fun ClassReference.superTypeListEntryOrNull(
  superTypeFqName: FqName
): KtSuperTypeListEntry? {
  return allSuperTypeClassReferences(includeSelf = true)
    .filterIsInstance<ClassReference.Psi>()
    .firstNotNullOfOrNull { classReference ->
      classReference.clazz
        .superTypeListEntries
        .firstOrNull { it.requireFqName(module) == superTypeFqName }
    }
}

/**
 * Converts a lambda `TypeName` to a standard `kotlin.Function*` type.
 *
 * given the lambda type: `(kotlin.String) -> kotlin.Unit`
 * returns the function type: `kotlin.Function1<String, Unit>`
 */
private fun TypeName.lambdaFix(): TypeName {
  // This is a special case when mixing parsing between descriptors and PSI.  Descriptors
  // will always represent lambda types as kotlin.Function* types, so lambda arguments
  // must be converted or their signatures won't match.
  // see https://github.com/square/anvil/issues/400
  val lambdaTypeName = this as? LambdaTypeName ?: return this

  val allTypes = listOfNotNull(lambdaTypeName.receiver) +
    lambdaTypeName.parameters.map { it.type } +
    lambdaTypeName.returnType

  return ClassName("kotlin", "Function${allTypes.size - 1}")
    .parameterizedBy(allTypes)
}
