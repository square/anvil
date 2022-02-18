package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

@ExperimentalAnvilApi
public interface AnvilModuleDescriptor : ModuleDescriptor {
  public fun resolveClassIdOrNull(classId: ClassId): FqName?

  public fun resolveFqNameOrNull(
    fqName: FqName,
    lookupLocation: LookupLocation = FROM_BACKEND
  ): ClassDescriptor?

  public fun getClassAndInnerClassReferences(ktFile: KtFile): List<Psi>

  public fun getClassReference(clazz: KtClassOrObject): Psi

  public fun getClassReference(descriptor: ClassDescriptor): Descriptor

  /**
   * Attempts to resolve the [FqName] to a [ClassDescriptor] first, then falls back to a
   * [KtClassOrObject] if the descriptor resolution fails. This will happen if the code being
   * parsed was generated as part of the compilation round for this module.
   */
  public fun getClassReferenceOrNull(fqName: FqName): ClassReference?
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ModuleDescriptor.asAnvilModuleDescriptor(): AnvilModuleDescriptor =
  this as AnvilModuleDescriptor

@ExperimentalAnvilApi
public fun FqName.canResolveFqName(
  module: ModuleDescriptor
): Boolean = module.asAnvilModuleDescriptor().resolveClassIdOrNull(classIdBestGuess()) != null

@ExperimentalAnvilApi
public fun Collection<KtFile>.classAndInnerClassReferences(
  module: ModuleDescriptor
): Sequence<Psi> {
  return asSequence().flatMap {
    module.asAnvilModuleDescriptor().getClassAndInnerClassReferences(it)
  }
}
