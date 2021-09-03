package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
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
public fun ModuleDescriptor.resolveFqNameOrNull(
  fqName: FqName
): FqName? = asAnvilModuleDescriptor().resolveClassIdOrNull(fqName.classIdBestGuess())

@ExperimentalAnvilApi
public fun ModuleDescriptor.getKtClassOrObjectOrNull(
  fqName: FqName
): KtClassOrObject? = asAnvilModuleDescriptor().getKtClassOrObjectOrNull(fqName)

@ExperimentalAnvilApi
public fun ModuleDescriptor.canResolveFqName(
  fqName: FqName
): Boolean = resolveFqNameOrNull(fqName) != null

@ExperimentalAnvilApi
public fun ModuleDescriptor.resolveFqNameOrNull(
  packageName: FqName,
  className: String
): FqName? = asAnvilModuleDescriptor()
  .resolveClassIdOrNull(ClassId(packageName, Name.identifier(className)))

@ExperimentalAnvilApi
public fun ModuleDescriptor.canResolveFqName(
  packageName: FqName,
  className: String
): Boolean = resolveFqNameOrNull(packageName, className) != null

@ExperimentalAnvilApi
public fun KtFile.classesAndInnerClasses(
  module: ModuleDescriptor
): List<KtClassOrObject> {
  return module.asAnvilModuleDescriptor().getClassesAndInnerClasses(this)
}

@ExperimentalAnvilApi
public fun Collection<KtFile>.classesAndInnerClass(
  module: ModuleDescriptor
): Sequence<KtClassOrObject> {
  return asSequence().flatMap { it.classesAndInnerClasses(module) }
}
