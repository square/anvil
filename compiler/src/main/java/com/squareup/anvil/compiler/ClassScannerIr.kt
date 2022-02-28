package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.toClassReference
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
  moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory
): Sequence<ClassReferenceIr> {
  val module = moduleDescriptorFactory.create(moduleFragment.descriptor)

  return findContributedClasses(module, annotation, scope?.fqName)
    .map {
      pluginContext.requireReferenceClass(it.fqName).toClassReference(pluginContext)
    }
}
