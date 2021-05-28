package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.internal.AnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.classesAndInnerClasses
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class RealAnvilModuleDescriptor(
  delegate: ModuleDescriptor
) : AnvilModuleDescriptor(), ModuleDescriptor by delegate {

  private val classesMap = mutableMapOf<KtFile, List<KtClassOrObject>>()
  private val allClasses: Sequence<KtClassOrObject>
    get() = classesMap.values.asSequence().flatMap { it }

  fun addFiles(files: Collection<KtFile>) {
    files.forEach { ktFile ->
      classesMap[ktFile] = ktFile.classesAndInnerClasses().toList()
    }
  }

  override fun resolveClassIdOrNull(classId: ClassId): FqName? {
    val fqName = classId.asSingleFqName()

    resolveClassByFqName(fqName, FROM_BACKEND)
      ?.let { return it.fqNameSafe }

    findTypeAliasAcrossModuleDependencies(classId)
      ?.let { return it.fqNameSafe }

    return allClasses
      .firstOrNull { it.fqName == fqName }
      ?.fqName
  }
}
