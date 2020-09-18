package com.squareup.anvil.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/**
 * Returns a sequence of contributed classes from the dependency graph. Note that the result
 * includes inner classes already.
 */
internal fun ClassScanner.findContributedClasses(
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  packageName: String,
  annotation: FqName,
  scope: FqName
): Sequence<IrClassSymbol> {
  return findContributedClasses(moduleFragment.descriptor, packageName, annotation, scope)
      .map {
        pluginContext.requireReferenceClass(it.fqNameSafe)
      }
}
