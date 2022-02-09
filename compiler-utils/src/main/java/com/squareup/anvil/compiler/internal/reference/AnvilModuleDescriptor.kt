package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
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

  public fun getClassesAndInnerClasses(ktFile: KtFile): List<KtClassOrObject>

  public fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject?

  public fun getClassReference(clazz: KtClassOrObject): Psi

  public fun getClassReference(descriptor: ClassDescriptor): Descriptor

  public fun getClassReferenceOrNull(fqName: FqName): ClassReference?
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ModuleDescriptor.asAnvilModuleDescriptor(): AnvilModuleDescriptor =
  this as AnvilModuleDescriptor

@ExperimentalAnvilApi
public fun FqName.getKtClassOrObjectOrNull(
  module: ModuleDescriptor
): KtClassOrObject? = module.asAnvilModuleDescriptor().getKtClassOrObjectOrNull(this)

@ExperimentalAnvilApi
public fun FqName.getKtClassOrObject(
  module: ModuleDescriptor
): KtClassOrObject = getKtClassOrObjectOrNull(module)
  ?: throw AnvilCompilationException("Couldn't resolve KtClassOrObject for ${asString()}")

@ExperimentalAnvilApi
public fun FqName.canResolveFqName(
  module: ModuleDescriptor
): Boolean = module.asAnvilModuleDescriptor().resolveClassIdOrNull(classIdBestGuess()) != null

@ExperimentalAnvilApi
public fun KtFile.classesAndInnerClasses(
  module: ModuleDescriptor
): List<KtClassOrObject> {
  return module.asAnvilModuleDescriptor().getClassesAndInnerClasses(this)
}

@ExperimentalAnvilApi
public fun Collection<KtFile>.classesAndInnerClasses(
  module: ModuleDescriptor
): Sequence<KtClassOrObject> {
  return asSequence().flatMap { it.classesAndInnerClasses(module) }
}
