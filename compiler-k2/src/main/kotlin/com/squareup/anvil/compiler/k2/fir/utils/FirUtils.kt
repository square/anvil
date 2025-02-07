package com.squareup.anvil.compiler.k2.fir.utils

import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.FqName

internal fun FqName.toFirAnnotation() = buildAnnotation {
  argumentMapping = FirEmptyAnnotationArgumentMapping
  annotationTypeRef = buildResolvedTypeRef {
    coneType = classId.constructClassLikeType()
  }
}
