package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.PropertyReference.Psi
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

@ExperimentalAnvilApi
public sealed class PropertyReference {

  public abstract val fqName: FqName
  public abstract val declaringClass: ClassReference

  public val module: AnvilModuleDescriptor get() = declaringClass.module

  public val name: String get() = fqName.shortName().asString()
  public val memberName: MemberName get() = MemberName(declaringClass.asClassName(), name)

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
    public val property: KtProperty,
    override val declaringClass: ClassReference.Psi,
    override val fqName: FqName = property.requireFqName()
  ) : PropertyReference()

  public class Descriptor internal constructor(
    public val property: PropertyDescriptor,
    override val declaringClass: ClassReference.Descriptor,
    override val fqName: FqName = property.fqNameSafe
  ) : PropertyReference()
}

@ExperimentalAnvilApi
public fun KtProperty.toPropertyReference(
  declaringClass: ClassReference.Psi
): Psi = Psi(this, declaringClass)

@ExperimentalAnvilApi
public fun PropertyDescriptor.toPropertyReference(
  declaringClass: ClassReference.Descriptor
): Descriptor = Descriptor(this, declaringClass)
