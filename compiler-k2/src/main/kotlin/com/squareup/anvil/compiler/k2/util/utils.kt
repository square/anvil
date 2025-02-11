package com.squareup.anvil.compiler.k2.util

import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public fun FqName.classId(): ClassId = ClassId.topLevel(this)

internal fun FqName.toFirAnnotation() = buildAnnotation {
  argumentMapping = FirEmptyAnnotationArgumentMapping
  annotationTypeRef = buildResolvedTypeRef {
    coneType = classId().constructClassLikeType()
  }
}
