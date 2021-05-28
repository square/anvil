package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@ExperimentalAnvilApi
public abstract class AnvilModuleDescriptor : ModuleDescriptor {
  public abstract fun resolveClassIdOrNull(classId: ClassId): FqName?
}

public fun ModuleDescriptor.resolveFqNameOrNull(
  fqName: FqName
): FqName? = (this as AnvilModuleDescriptor).resolveClassIdOrNull(fqName.classIdBestGuess())

public fun ModuleDescriptor.canResolveFqName(
  fqName: FqName
): Boolean = resolveFqNameOrNull(fqName) != null

public fun ModuleDescriptor.resolveFqNameOrNull(
  packageName: FqName,
  className: String
): FqName? = (this as AnvilModuleDescriptor)
  .resolveClassIdOrNull(ClassId(packageName, Name.identifier(className)))

public fun ModuleDescriptor.canResolveFqName(
  packageName: FqName,
  className: String
): Boolean = resolveFqNameOrNull(packageName, className) != null
