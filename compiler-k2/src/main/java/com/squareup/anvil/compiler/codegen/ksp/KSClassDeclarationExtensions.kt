package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Visibility.PUBLIC
import com.squareup.anvil.compiler.contributesMultibindingFqName
import org.jetbrains.kotlin.name.FqName

internal fun KSClassDeclaration.checkNotMoreThanOneQualifier(
  annotationFqName: FqName,
) {
  val annotationsList = annotations.toList()
  // The class is annotated with @ContributesBinding, @ContributesMultibinding, or another Anvil annotation.
  // If there is less than 2 further annotations, then there can't be more than two qualifiers.
  if (annotationsList.size <= 2) return

  val qualifierCount = annotations.count { it.isQualifier() }
  if (qualifierCount > 1) {
    throw KspAnvilException(
      message = "Classes annotated with @${annotationFqName.shortName()} may not use more " +
        "than one @Qualifier.",
      node = this,
    )
  }
}

internal inline fun KSClassDeclaration.checkClassIsPublic(message: () -> String) {
  if (getVisibility() != PUBLIC) {
    throw KspAnvilException(
      message = message(),
      node = this,
    )
  }
}

internal fun KSClassDeclaration.checkNotMoreThanOneMapKey() {
  // The class is annotated with @ContributesMultibinding. If there is less than 2 further
  // annotations, then there can't be more than two map keys.
  val annotationsList = annotations.toList()
  if (annotationsList.size <= 2) return

  val mapKeysCount = annotationsList.count { it.isMapKey() }

  if (mapKeysCount > 1) {
    throw KspAnvilException(
      message = "Classes annotated with @${contributesMultibindingFqName.shortName()} may not " +
        "use more than one @MapKey.",
      node = this,
    )
  }
}

internal fun KSClassDeclaration.checkSingleSuperType(
  annotationFqName: FqName,
  resolver: Resolver,
) {
  // If the bound type exists, then you're allowed to have multiple super types. Without the bound
  // type there must be exactly one super type.
  val hasExplicitBoundType = getKSAnnotationsByQualifiedName(annotationFqName.asString())
    .firstOrNull()
    ?.boundTypeOrNull() != null
  if (hasExplicitBoundType) return

  if (superTypesExcludingAny(resolver).count() != 1) {
    throw KspAnvilException(
      message = "${qualifiedName?.asString()} contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @${annotationFqName.shortName()} annotation.",
      node = this,
    )
  }
}

internal fun KSClassDeclaration.checkClassExtendsBoundType(
  annotationFqName: FqName,
  resolver: Resolver,
) {
  val boundType = getKSAnnotationsByQualifiedName(annotationFqName.asString())
    .firstOrNull()
    ?.boundTypeOrNull()
    ?: superTypesExcludingAny(resolver).singleOrNull()?.resolve()
    ?: throw KspAnvilException(
      message = "Couldn't find the bound type.",
      node = this,
    )

  // The boundType is declared explicitly in the annotation. Since all classes extend Any, we can
  // stop here.
  if (boundType == resolver.builtIns.anyType) return

  if (!boundType.isAssignableFrom(asType(emptyList()))) {
    throw KspAnvilException(
      message = "${this.qualifiedName?.asString()} contributes a binding " +
        "for ${boundType.declaration.qualifiedName?.asString()}, but doesn't " +
        "extend this type.",
      node = this,
    )
  }
}

internal fun KSClassDeclaration.superTypesExcludingAny(
  resolver: Resolver,
): Sequence<KSTypeReference> = superTypes
  .filterNot { it.resolve() == resolver.builtIns.anyType }

internal fun KSClassDeclaration.isInterface(): Boolean {
  return classKind == ClassKind.INTERFACE
}
