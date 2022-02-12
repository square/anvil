package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

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
  val visibilityName = requiredVisibility.name.toLowerCaseAsciiOnly()

  checkHasVisibility(
    requiredVisibility,
    "$fqName is binding a type, but the class is not $visibilityName. " +
      "Only $visibilityName types are supported."
  )
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
