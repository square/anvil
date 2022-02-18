package com.squareup.anvil.compiler.codegen.reference

import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor.ClassReferenceCacheKey.Companion.toClassReferenceCacheKey
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor.ClassReferenceCacheKey.Type.DESCRIPTOR
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor.ClassReferenceCacheKey.Type.PSI
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.AnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.requireClassId
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.toClassId
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

  private val ktFileToClassReferenceMap = mutableMapOf<String, List<Psi>>()
  private val allPsiClassReferences: Sequence<Psi>
    get() = ktFileToClassReferenceMap.values.asSequence().flatten()

  private val resolveDescriptorCache = mutableMapOf<FqName, ClassDescriptor?>()
  private val resolveClassIdCache = mutableMapOf<ClassId, FqName?>()
  private val classReferenceCache = mutableMapOf<ClassReferenceCacheKey, ClassReference>()

  internal val allFiles: Sequence<KtFile>
    get() = allPsiClassReferences
      .map { it.clazz.containingKtFile }
      .distinctBy { it.identifier }

  fun addFiles(files: Collection<KtFile>) {
    files.forEach { ktFile ->
      val classReferences = ktFile.classesAndInnerClasses().map { ktClass ->
        Psi(ktClass, ktClass.toClassId(), this)
      }

      classReferences.forEach { classReference ->
        // A `FlushingCodeGenerator` can generate new KtFiles for already generated KtFiles.
        // This can be problematic, because we might have ClassReferences for the old files and
        // KtClassOrObject instances cached. We need to override the entries in the caches for
        // these new files and classes.
        classReferenceCache[classReference.clazz.toClassReferenceCacheKey()] = classReference
        resolveClassIdCache[classReference.classId] = classReference.fqName
      }

      ktFileToClassReferenceMap[ktFile.identifier] = classReferences
    }
  }

  override fun getClassAndInnerClassReferences(ktFile: KtFile): List<Psi> {
    return ktFileToClassReferenceMap.getOrPut(ktFile.identifier) {
      ktFile.classesAndInnerClasses().map { getClassReference(it) }
    }
  }

  override fun resolveClassIdOrNull(classId: ClassId): FqName? =
    resolveClassIdCache.getOrPut(classId) {
      val fqName = classId.asSingleFqName()

      resolveFqNameOrNull(fqName)?.fqNameSafe
        ?: allPsiClassReferences.firstOrNull { it.fqName == fqName }?.fqName
    }

  override fun resolveFqNameOrNull(
    fqName: FqName,
    lookupLocation: LookupLocation
  ): ClassDescriptor? {
    return resolveDescriptorCache.getOrPut(fqName) {
      // In the case of a typealias, we need to look up the original reference instead.
      resolveClassByFqName(fqName, lookupLocation)
        ?: findTypeAliasAcrossModuleDependencies(fqName.classIdBestGuess())?.classDescriptor
    }
  }

  override fun getClassReference(clazz: KtClassOrObject): Psi {
    return classReferenceCache.getOrPut(clazz.toClassReferenceCacheKey()) {
      Psi(clazz, clazz.toClassId(), this)
    } as Psi
  }

  override fun getClassReference(descriptor: ClassDescriptor): Descriptor {
    return classReferenceCache.getOrPut(descriptor.toClassReferenceCacheKey()) {
      Descriptor(descriptor, descriptor.requireClassId(), this)
    } as Descriptor
  }

  override fun getClassReferenceOrNull(fqName: FqName): ClassReference? {
    // Note that we don't cache the result, because all function calls get objects from caches.
    // There's no need to optimize that.
    fun psiClassReference(): Psi? = allPsiClassReferences.firstOrNull { it.fqName == fqName }
    fun descriptorClassReference(): Descriptor? =
      resolveFqNameOrNull(fqName)?.let { getClassReference(it) }

    return descriptorClassReference() ?: psiClassReference()
  }

  private val KtFile.identifier: String
    get() = packageFqName.asString() + name

  private data class ClassReferenceCacheKey(
    private val fqName: FqName,
    private val type: Type
  ) {
    enum class Type {
      PSI,
      DESCRIPTOR,
    }

    companion object {
      fun KtClassOrObject.toClassReferenceCacheKey(): ClassReferenceCacheKey =
        ClassReferenceCacheKey(requireFqName(), PSI)

      fun ClassDescriptor.toClassReferenceCacheKey(): ClassReferenceCacheKey =
        ClassReferenceCacheKey(fqNameSafe, DESCRIPTOR)
    }
  }
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
