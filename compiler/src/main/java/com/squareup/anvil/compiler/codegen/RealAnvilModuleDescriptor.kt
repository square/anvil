package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.internal.AnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.takeIfNotEmpty
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
) : AnvilModuleDescriptor, ModuleDescriptor by delegate {

  internal val allFiles = mutableListOf<KtFile>()

  private val classesMap = mutableMapOf<String, List<KtClassOrObject>>()
  private val allClasses: Sequence<KtClassOrObject>
    get() = classesMap.values.asSequence().flatMap { it }

  fun addFiles(files: Collection<KtFile>) {
    allFiles += files

    files.forEach { ktFile ->
      classesMap[ktFile.identifier] = ktFile.classesAndInnerClasses()
    }
  }

  override fun getClassesAndInnerClasses(ktFile: KtFile): List<KtClassOrObject> {
    return classesMap.getOrPut(ktFile.identifier) {
      ktFile.classesAndInnerClasses()
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

  override fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject? {
    return allClasses
      .firstOrNull { it.fqName == fqName }
  }

  private val KtFile.identifier: String
    get() = packageFqName.asString() + name
}

private fun KtFile.classesAndInnerClasses(): List<KtClassOrObject> {
  val children = findChildrenByClass(KtClassOrObject::class.java)

  return generateSequence(children.toList()) { list ->
    list
      .flatMap {
        it.declarations.filterIsInstance<KtClassOrObject>()
      }
      .takeIfNotEmpty()
  }.flatten().toList()
}
