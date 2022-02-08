package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.AnvilModuleDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.LookupLocation
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

  private val resolveDescriptorCache = mutableMapOf<FqName, ClassDescriptor?>()
  private val resolveClassIdCache = mutableMapOf<ClassId, FqName?>()

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

  override fun resolveClassIdOrNull(classId: ClassId): FqName? =
    resolveClassIdCache.computeIfAbsent(classId) {
      val fqName = classId.asSingleFqName()

      resolveFqNameOrNull(fqName)?.fqNameSafe
        ?: allClasses.firstOrNull { it.fqName == fqName }?.fqName
    }

  override fun resolveFqNameOrNull(
    fqName: FqName,
    lookupLocation: LookupLocation
  ): ClassDescriptor? {
    return resolveDescriptorCache.computeIfAbsent(fqName) {
      // In the case of a typealias, we need to look up the original reference instead.
      resolveClassByFqName(fqName, lookupLocation)
        ?: findTypeAliasAcrossModuleDependencies(fqName.classIdBestGuess())?.classDescriptor
    }
  }

  override fun getKtClassOrObjectOrNull(fqName: FqName): KtClassOrObject? {
    return allClasses.firstOrNull { it.fqName == fqName }
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
      .ifEmpty { null }
  }.flatten().toList()
}
