package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName

/**
 * Returns a sequence of contributed classes from the dependency graph. Note that the result
 * includes inner classes already.
 */
// TODO: Change to return [ClassReferenceIr]
internal fun ClassScanner.findContributedClasses(
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  packageName: String,
  annotation: FqName,
  scope: FqName?,
  moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory
): Sequence<IrClassSymbol> {
  val module = moduleDescriptorFactory.create(moduleFragment.descriptor)

  return findContributedClasses(module, packageName, annotation, scope)
    .map {
      pluginContext.requireReferenceClass(it.fqName)
    }
}
