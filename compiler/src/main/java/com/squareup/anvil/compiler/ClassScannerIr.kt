package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.singleOrEmpty
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/**
 * Returns a sequence of contributed hints from the dependency graph. Note that the result
 * already includes inner classes.
 */
internal fun ClassScanner.findContributedHints(
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  annotation: FqName
): Sequence<ContributedHintIr> {
  return findContributedHints(moduleFragment.descriptor, annotation)
    .map {
      ContributedHintIr(
        irClass = pluginContext.requireReferenceClass(it.descriptor.fqNameSafe),
        annotationFqName = it.annotationFqName,
        scopes = it.scopes
      )
    }
}

internal class ContributedHintIr(
  val irClass: IrClassSymbol,
  val annotationFqName: FqName,
  private val scopes: List<FqName>
) {
  val fqName = irClass.fqName

  fun isContributedToScope(scope: FqName): Boolean = scopes.any { it == scope }

  fun contributedAnnotations(scope: FqName? = null): List<IrConstructorCall> =
    irClass.owner.annotations(annotationFqName, scope)

  // TODO Repeatable: do we need a cache?
  // There must only one annotation, because we only allow one contribution to the same scope.
  fun contributedAnnotationOrNull(scope: FqName): IrConstructorCall? {
    return contributedAnnotations(scope).singleOrEmpty()
  }

  fun contributedAnnotation(scope: FqName): IrConstructorCall =
    contributedAnnotationOrNull(scope) ?: throw AnvilCompilationException(
      element = irClass,
      message = "Couldn't find $annotationFqName with scope $scope."
    )
}
