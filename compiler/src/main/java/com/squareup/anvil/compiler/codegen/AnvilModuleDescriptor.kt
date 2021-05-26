package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.safePackageString
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

internal class AnvilModuleDescriptor(
  private val delegate: ModuleDescriptor,
  generatedKtFiles: List<KtFile>
) : ModuleDescriptor by delegate {

  private val generatedKtFiles: MutableList<KtFile> = generatedKtFiles.toMutableList()

  fun addFiles(newFiles: Collection<KtFile>) {
    generatedKtFiles.addAll(newFiles)
  }

  fun resolveFqNameOrNull(
    packageName: FqName,
    className: String
  ): FqName? {

    val maybeClassFqName = FqName("${packageName.safePackageString()}$className")

    resolveClassByFqName(maybeClassFqName, FROM_BACKEND)
      ?.let { return it.fqNameSafe }

    findTypeAliasAcrossModuleDependencies(ClassId(packageName, Name.identifier(className)))
      ?.let { return it.fqNameSafe }

    return generatedKtFiles
      .asSequence()
      .flatMap { it.classesAndInnerClasses() }
      .firstOrNull { it.fqName == maybeClassFqName }
      ?.fqName
  }
}
