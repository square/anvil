package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.IdSignature.CommonSignature
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal fun IrPluginContext.requireReferenceClass(fqName: FqName): IrClassSymbol {
  return referenceClass(fqName) ?: throw AnvilCompilationException(
    message = "Couldn't resolve reference for $fqName"
  )
}

internal fun ClassId.irClass(context: IrPluginContext): IrClassSymbol =
  context.requireReferenceClass(asSingleFqName())

internal fun ClassId.irClassOrNull(context: IrPluginContext): IrClassSymbol? =
  context.referenceClass(asSingleFqName())

internal fun IrClass.requireClassId(): ClassId {
  return classId ?: throw AnvilCompilationException(
    element = this,
    message = "Couldn't find a ClassId for $fqName."
  )
}

internal fun IrClassSymbol.requireClassId(): ClassId = owner.requireClassId()

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use a Reference class instead of depending directly on IR")
internal fun IrAnnotationContainer.annotationOrNull(
  annotationFqName: FqName,
  scope: FqName? = null
): IrConstructorCall? {
  return getAnnotation(annotationFqName)?.takeIf { annotation ->
    scope == null || annotation.scope() == scope
  }
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use a Reference class instead of depending directly on IR")
internal fun IrAnnotationContainer.annotation(
  annotationFqName: FqName,
  scope: FqName? = null
): IrConstructorCall = annotationOrNull(annotationFqName, scope)
  ?: throw AnvilCompilationException(
    message = "Couldn't find $annotationFqName with scope $scope.",
    element = (this as? IrType)?.getClass()
  )

internal val IrExpression.kclassUnwrapped: IrClassifierSymbol
  get() = (type as? IrSimpleType)?.arguments?.get(0)?.typeOrNull?.classifierOrNull
    ?: throw AnvilCompilationException(
      message = "Couldn't resolve wrapped class.",
      element = this
    )

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use a Reference class instead of depending directly on IR")
internal fun IrConstructorCall.scope(): FqName {
  val expression = argument("scope")?.second
    ?: throw AnvilCompilationException(
      message = "Couldn't find scope annotation.",
      element = this
    )

  val signature = expression.kclassUnwrapped.signature?.asPublic()
    ?: throw AnvilCompilationException(
      message = "Couldn't resolve scope signature.",
      element = this
    )

  return signature.fqName
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use a Reference class instead of depending directly on IR")
internal fun IrConstructorCall.parentScope(): FqName {
  val expression = argument("parentScope")?.second
    ?: throw AnvilCompilationException(
      message = "Couldn't find parent scope annotation.",
      element = this
    )

  val signature = expression.kclassUnwrapped.signature?.asPublic()
    ?: throw AnvilCompilationException(
      message = "Couldn't resolve parent scope signature.",
      element = this
    )

  return signature.fqName
}

private val CommonSignature.fqName: FqName
  // It's important that we use [declarationFqName] instead of [shortName] here to ensure that the
  // [FqName] being constructed handles nested classes if it's later used for finding IR symbols.
  //
  // e.g. we might have a signature like
  // 'com.squareup.anvil.test/ContributedSubcomponent.ParentScope' which gets broken up as
  // packageFqName     -> 'com.squareup.anvil.test'
  // declarationFqName -> 'ContributedSubcomponent.ParentScope'
  // shortName         -> 'ParentScope'
  //
  // Using [shortName] would produce the FqName 'com.squareup.anvil.test.ParentScope' which
  // prevents the IR lookup utils from finding the matching symbol.
  get() = FqName("$packageFqName.$declarationFqName")

internal val IrDeclarationWithName.fqName: FqName
  get() = fqNameWhenAvailable ?: throw AnvilCompilationException(
    message = "Couldn't find FqName for $name",
    element = this
  )

internal val IrClassSymbol.fqName: FqName get() = owner.fqName

internal fun IrMemberAccessExpression<*>.argument(
  name: String
): Pair<IrValueParameter, IrExpression>? {
  return getArgumentsWithIr()
    .singleOrNull {
      it.first.name.asString() == name
    }
}

internal fun IrConstructorCall.argumentClassArray(
  name: String
): List<IrClass> {
  val vararg = argument(name)?.second as? IrVararg ?: return emptyList()

  return vararg.elements
    .filterIsInstance<IrExpression>()
    .map { it.kclassUnwrapped.owner as IrClass }
}

internal fun IrConstructorCall.replaces(): List<IrClass> = argumentClassArray("replaces")
