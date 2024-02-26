package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.reference.TypeParameterReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.TypeParameterReference.Psi
import com.squareup.anvil.compiler.internal.reference.TypeVariance.Companion.toTypeVariance
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import kotlin.LazyThreadSafetyMode.NONE

@ExperimentalAnvilApi
public sealed class TypeParameterReference {

  public abstract val name: String
  public abstract val upperBounds: List<TypeReference>
  public abstract val declaringClass: ClassReference
  public abstract val variance: TypeVariance

  public val typeVariableName: TypeVariableName by lazy(NONE) {
    TypeVariableName(name, upperBounds.map { it.asTypeName() })
  }

  public class Psi internal constructor(
    override val name: String,
    override val upperBounds: List<TypeReference>,
    override val declaringClass: ClassReference.Psi,
    override val variance: TypeVariance,
  ) : TypeParameterReference()

  public class Descriptor internal constructor(
    override val name: String,
    override val upperBounds: List<TypeReference.Descriptor>,
    override val declaringClass: ClassReference.Descriptor,
    override val variance: TypeVariance,
  ) : TypeParameterReference()

  override fun toString(): String {
    return "${this::class.qualifiedName}(name='$name', declaringClass=$declaringClass)"
  }
}

@ExperimentalAnvilApi
public fun ClassReference.Psi.getTypeParameterReferences(): List<Psi> {
  // Any type which is constrained in a `where` clause is also defined as a type parameter.
  // It's also technically possible to have one constraint in the type parameter spot, like this:
  // class MyClass<T : Any> where T : Set<*>, T : MutableCollection<*>
  // Merge both groups of type parameters in order to get the full list of bounds.

  val boundsByVariableName = mutableMapOf<String, MutableList<TypeReference>>()
  val varianceByVariableName = mutableMapOf<String, TypeVariance>()

  clazz.typeParameterList
    ?.parameters
    ?.filter { it.fqNameOrNull(module) == null }
    ?.forEach { parameter ->
      val variableName = parameter.nameAsSafeName.asString()

      val extendsBound = parameter.extendsBound?.toTypeReference(this, module)

      boundsByVariableName[variableName] = listOfNotNull(extendsBound).toMutableList()
      varianceByVariableName[variableName] = parameter.variance.toTypeVariance()
    }

  clazz.typeConstraintList
    ?.constraints
    ?.filter { it.fqNameOrNull(module) == null }
    ?.forEach { constraint ->
      val variableName = constraint.subjectTypeParameterName
        ?.getReferencedName()
        ?: return@forEach
      val extendsBound = constraint.boundTypeReference?.toTypeReference(this, module)
        ?: return@forEach

      boundsByVariableName
        .getValue(variableName)
        .add(extendsBound)
    }

  return boundsByVariableName.map { (name, types) ->
    Psi(
      name = name,
      upperBounds = types,
      declaringClass = this,
      variance = varianceByVariableName.getValue(name),
    )
  }
}

@ExperimentalAnvilApi
public fun TypeParameterDescriptor.toTypeParameterReference(
  declaringClass: ClassReference.Descriptor,
): Descriptor {
  val typeVariance = variance.toTypeVariance()
  return Descriptor(
    name = name.asString(),
    upperBounds = upperBounds.map {
      TypeReference.Descriptor(it, typeVariance, declaringClass, declaringClass.module)
    },
    declaringClass = declaringClass,
    variance = typeVariance,
  )
}
