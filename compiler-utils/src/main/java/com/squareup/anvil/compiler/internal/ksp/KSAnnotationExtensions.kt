@file:Suppress("invisible_reference", "invisible_member")

package com.squareup.anvil.compiler.internal.ksp

import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.internal.daggerScopeFqName
import com.squareup.anvil.compiler.internal.mapKeyFqName
import com.squareup.anvil.compiler.internal.qualifierFqName
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.name.FqName

public fun <T : KSAnnotation> List<T>.checkNoDuplicateScope(
  annotatedType: KSClassDeclaration,
  isContributeAnnotation: Boolean,
) {
  // Exit early to avoid allocating additional collections.
  if (size < 2) return
  if (size == 2 && this[0].scope() != this[1].scope()) return

  // Check for duplicate scopes. Multiple contributions to the same scope are forbidden.
  val duplicates = groupBy { it.scope() }.filterValues { it.size > 1 }

  if (duplicates.isNotEmpty()) {
    val annotatedClass = annotatedType.qualifiedName!!.asString()
    val duplicateScopesMessage =
      duplicates.keys.joinToString(prefix = "[", postfix = "]") {
        it.contextualToClassName(annotatedType).simpleName
      }

    throw KspAnvilException(
      message = if (isContributeAnnotation) {
        "$annotatedClass contributes multiple times to the same scope: $duplicateScopesMessage. " +
          "Contributing multiple times to the same scope is forbidden and all scopes must " +
          "be distinct."
      } else {
        "$annotatedClass merges multiple times to the same scope: $duplicateScopesMessage. " +
          "Merging multiple times to the same scope is forbidden and all scopes must " +
          "be distinct."
      },
      node = annotatedType,
    )
  }
}

public fun <T : KSAnnotation> List<T>.checkNoDuplicateScopeAndBoundType(
  annotatedType: KSClassDeclaration,
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
      annotatedType,
    )
  }
}

public fun KSAnnotation.scopeClassName(): ClassName =
  classNameArgumentAt("scope")
    ?: throw KspAnvilException(
      message = "Couldn't find scope for ${annotationType.resolve().declaration.qualifiedName?.asString()}.",
      this,
    )

public fun KSAnnotation.scope(): KSType =
  scopeOrNull()
    ?: throw KspAnvilException(
      message = "Couldn't find scope for ${annotationType.resolve().declaration.qualifiedName?.asString()}.",
      this,
    )

public fun KSAnnotation.scopeOrNull(): KSType? {
  return argumentOfTypeAt<KSType>("scope")
}

public fun KSAnnotation.boundTypeOrNull(): KSType? = argumentOfTypeAt<KSType>("boundType")

public fun KSAnnotation.resolveBoundType(
  resolver: Resolver,
  declaringClass: KSClassDeclaration,
): KSClassDeclaration {
  val declaredBoundType = boundTypeOrNull()?.resolveKSClassDeclaration()
  if (declaredBoundType != null) return declaredBoundType
  // Resolve from the first and only supertype
  return declaringClass.superTypesExcludingAny(resolver, shallow = true)
    .single()
    .resolveKSClassDeclaration() ?: throw KspAnvilException(
    message = "Couldn't resolve bound type for ${declaringClass.qualifiedName}",
    node = declaringClass,
  )
}

public fun KSAnnotation.replaces(): List<KSClassDeclaration> = classArrayArgument("replaces")

public fun KSAnnotation.subcomponents(): List<KSClassDeclaration> = classArrayArgument(
  "subcomponents",
)

public fun KSAnnotation.exclude(): List<KSClassDeclaration> = classArrayArgument("exclude")

public fun KSAnnotation.modules(): List<KSClassDeclaration> = classArrayArgument("modules")

public fun KSAnnotation.includes(): List<KSClassDeclaration> = classArrayArgument("includes")

private fun KSAnnotation.classArrayArgument(name: String): List<KSClassDeclaration> =
  argumentOfTypeWithMapperAt<List<KSType>, List<KSClassDeclaration>>(
    name,
  ) { arg, value ->
    value.map {
      it.resolveKSClassDeclaration()
        ?: throw KspAnvilException("Could not resolve $name $it", arg)
    }
  }.orEmpty()

public fun KSAnnotation.parentScope(): KSClassDeclaration {
  return argumentOfTypeAt<KSType>("parentScope")
    ?.resolveKSClassDeclaration()
    ?: throw KspAnvilException(
      message = "Couldn't find parentScope for $shortName.",
      node = this,
    )
}

public fun KSAnnotation.classNameArrayArgumentAt(
  name: String,
): List<ClassName>? {
  return argumentOfTypeWithMapperAt<List<KSType>, List<ClassName>>(name) { arg, value ->
    value.map { it.contextualToClassName(arg) }
  }
}

public fun KSAnnotation.classNameArgumentAt(
  name: String,
): ClassName? {
  return argumentOfTypeWithMapperAt<KSType, ClassName>(name) { arg, value ->
    value.contextualToClassName(arg)
  }
}

public inline fun <reified T> KSAnnotation.argumentOfTypeAt(
  name: String,
): T? {
  return argumentOfTypeWithMapperAt<T, T>(name) { arg, value ->
    when {
      value is KSType -> {
        value.checkErrorType(arg)
      }
      value is List<*> && value.firstOrNull() is KSType -> {
        for (element in value) {
          (element as KSType).checkErrorType(arg)
        }
      }
    }
    value
  }
}

public inline fun <reified T, R> KSAnnotation.argumentOfTypeWithMapperAt(
  name: String,
  mapper: (arg: KSValueArgument, value: T) -> R,
): R? {
  return argumentAt(name)
    ?.let { arg ->
      val value = arg.value
      if (value !is T) {
        throw KspAnvilException(
          message = "Expected argument '$name' of type '${T::class.qualifiedName} but was '${arg.javaClass.name}'.",
          node = arg,
        )
      } else {
        value?.let { mapper(arg, it) }
      }
    }
}

public fun KSAnnotation.argumentAt(
  name: String,
): KSValueArgument? {
  return arguments.find { it.name?.asString() == name }
    ?.takeUnless { it.isDefault() }
}

private fun KSAnnotation.isTypeAnnotatedWith(
  annotationFqName: FqName,
): Boolean = annotationType.resolve()
  .declaration
  .isAnnotationPresent(annotationFqName.asString())

public fun KSAnnotation.isQualifier(): Boolean = isTypeAnnotatedWith(qualifierFqName)
public fun KSAnnotation.isMapKey(): Boolean = isTypeAnnotatedWith(mapKeyFqName)
public fun KSAnnotation.isDaggerScope(): Boolean = isTypeAnnotatedWith(daggerScopeFqName)

public fun KSAnnotated.qualifierAnnotation(): KSAnnotation? =
  resolvableAnnotations.singleOrNull { it.isQualifier() }

public fun KSAnnotation.ignoreQualifier(): Boolean =
  argumentOfTypeAt<Boolean>("ignoreQualifier") == true

public fun KSAnnotation.rank(): Int {
  return argumentOfTypeAt<Int>("rank")
    ?: priorityLegacy()
    ?: ContributesBinding.RANK_NORMAL
}

@Suppress("DEPRECATION")
public fun KSAnnotation.priorityLegacy(): Int? {
  val priorityEntry = argumentOfTypeAt<KSType>("priority") ?: return null
  val name = priorityEntry.resolveKSClassDeclaration()?.simpleName?.asString() ?: return null
  val priority = ContributesBinding.Priority.valueOf(name)
  return priority.value
}
