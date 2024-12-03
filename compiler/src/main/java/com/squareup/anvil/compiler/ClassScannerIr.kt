package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.FqName

/**
 * Returns a sequence of contributed classes from the dependency graph. Note that the result
 * includes inner classes already.
 */
internal fun ClassScanner.findContributedClasses(
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  annotation: FqName,
  scope: ClassReferenceIr?,
  moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
): Sequence<ClassReferenceIr> {
  val module = moduleDescriptorFactory.create(moduleFragment.descriptor)

  return findContributedClasses(module, annotation, scope?.fqName)
    .map {
      pluginContext.requireReferenceClass(it.fqName).toClassReference(pluginContext)
    }
}

/**
 * Returns a sequence of contributed classes from the dependency graph. Note that the result
 * includes inner classes already.
 */
internal fun ClassScanner.findContributedClasses(
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  annotation: FqName,
  scopeFqName: FqName?,
  moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
): Sequence<ClassReference> {
  val module = moduleDescriptorFactory.create(moduleFragment.descriptor)

  return findContributedClasses(module, annotation, scopeFqName)
    .map {
      pluginContext.requireReferenceClass(it.fqName).toClassReference(pluginContext)
    }
}
