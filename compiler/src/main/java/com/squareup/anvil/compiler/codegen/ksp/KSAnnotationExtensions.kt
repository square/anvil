package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument

private const val DEFAULT_SCOPE_INDEX = 0

internal fun <T : KSAnnotation> List<T>.checkNoDuplicateScopeAndBoundType(
  annotatedType: KSClassDeclaration
) {
  // Exit early to avoid allocating additional collections.
  if (size < 2) return
  if (size == 2 && this[0].scope() != this[1].scope()) return

  val duplicateScopes = groupBy { it.scope() }
    .filterValues { it.size > 1 }
    .ifEmpty { return }

  duplicateScopes.values.forEach { duplicateScopeAnnotations ->
    val duplicateBoundTypes = duplicateScopeAnnotations
      .groupBy { it.boundTypeOrNull() }
      .filterValues { it.size > 1 }
      .ifEmpty { return }
      .keys

    throw KspAnvilException(
      message = "${annotatedType.qualifiedName?.asString()} contributes multiple times to " +
        "the same scope using the same bound type: " +
        duplicateBoundTypes.joinToString(prefix = "[", postfix = "]") {
          it?.declaration?.simpleName?.getShortName() ?: annotatedType.superTypes.single()
            .resolve().declaration.simpleName.getShortName()
        } +
        ". Contributing multiple times to the same scope with the same bound type is forbidden " +
        "and all scope - bound type combinations must be distinct.",
      annotatedType
    )
  }
}

internal fun KSAnnotation.scope(
  parameterIndex: Int = DEFAULT_SCOPE_INDEX
): KSType =
  scopeOrNull(parameterIndex)
    ?: throw KspAnvilException(
      message = "Couldn't find scope for ${annotationType.resolve().declaration.qualifiedName}.",
      this,
    )

internal fun KSAnnotation.scopeOrNull(parameterIndex: Int = DEFAULT_SCOPE_INDEX): KSType? {
  return argumentAt("scope", parameterIndex)?.value as? KSType?
}

internal fun KSAnnotation.boundTypeOrNull(): KSType? = argumentAt("boundType", 1)?.value as? KSType?

internal fun KSAnnotation.argumentAt(
  name: String,
  index: Int
): KSValueArgument? {
  return arguments.singleOrNull { it.name?.asString() == name }
    ?: arguments.elementAtOrNull(index)?.takeIf { it.name == null }
}
