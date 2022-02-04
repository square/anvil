package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.classIdBestGuess
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

@ExperimentalAnvilApi
public interface AnvilModuleDescriptor : ModuleDescriptor {
  public fun resolveClassIdOrNull(classId: ClassId): FqName?
  public fun getClassesAndInnerClasses(ktFile: KtFile): List<KtClassOrObject>
  public fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject?
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ModuleDescriptor.asAnvilModuleDescriptor(): AnvilModuleDescriptor =
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
