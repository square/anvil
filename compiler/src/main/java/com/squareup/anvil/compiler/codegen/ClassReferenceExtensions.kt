package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import org.jetbrains.kotlin.name.FqName

internal fun ClassReference.checkNotMoreThanOneQualifier(
  annotationFqName: FqName
) {
  // The class is annotated with @ContributesBinding, @ContributesMultibinding, or another Anvil annotation.
  // If there is less than 2 further annotations, then there can't be more than two qualifiers.
  if (annotations.size <= 2) return

  val qualifierCount = annotations.count { it.isQualifier() }
  if (qualifierCount > 1) {
    throw AnvilCompilationExceptionClassReference(
      message = "Classes annotated with @${annotationFqName.shortName()} may not use more " +
        "than one @Qualifier.",
      classReference = this
    )
  }
}

internal fun ClassReference.checkClassIsPublic() {
  val requiredVisibility = Visibility.PUBLIC
  val visibilityName = requiredVisibility.name.lowercase()

  if (visibility() != requiredVisibility) {
    throw AnvilCompilationExceptionClassReference(
      classReference = this,
      message = "$fqName is binding a type, but the class is not $visibilityName. " +
        "Only $visibilityName types are supported."
    )
  }
}

internal fun ClassReference.checkNotMoreThanOneMapKey() {
  // The class is annotated with @ContributesMultibinding. If there is less than 2 further
  // annotations, then there can't be more than two map keys.
  if (annotations.size <= 2) return

  val mapKeys = annotations.filter { it.isMapKey() }

  if (mapKeys.size > 1) {
    throw AnvilCompilationExceptionClassReference(
      message = "Classes annotated with @${contributesMultibindingFqName.shortName()} may not " +
        "use more than one @MapKey.",
      classReference = this
    )
  }
}

internal fun ClassReference.checkSingleSuperType(
  annotationFqName: FqName
) {
  // If the bound type exists, then you're allowed to have multiple super types. Without the bound
  // type there must be exactly one super type.
  if (annotations.find { it.fqName == annotationFqName }?.boundTypeOrNull() != null) return

  if (directSuperClassReferences().count() != 1) {
    throw AnvilCompilationExceptionClassReference(
      message = "$fqName contributes a binding, but does not " +
        "specify the bound type. This is only allowed with exactly one direct super type. " +
        "If there are multiple or none, then the bound type must be explicitly defined in " +
        "the @${annotationFqName.shortName()} annotation.",
      classReference = this
    )
  }
}

internal fun ClassReference.checkClassExtendsBoundType(
  annotationFqName: FqName
) {
  val boundType = annotations.find { it.fqName == annotationFqName }?.boundTypeOrNull()
    ?: directSuperClassReferences().singleOrNull()
    ?: throw AnvilCompilationExceptionClassReference(
      message = "Couldn't find the bound type.",
      classReference = this
    )

  if (allSuperTypeClassReferences().none { it.fqName == boundType.fqName }) {
    throw AnvilCompilationExceptionClassReference(
      message = "${this.fqName} contributes a binding for ${boundType.fqName}, but doesn't " +
        "extend this type.",
      classReference = this
    )
  }
}
