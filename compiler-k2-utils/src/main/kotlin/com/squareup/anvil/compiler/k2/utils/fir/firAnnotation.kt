package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId

/**
 * Creates a [FirAnnotation] instance with the specified type, argument mapping, source, and use-site target.
 *
 * @param type The [ClassId] representing the annotation type.
 * @param argumentMapping The mapping of arguments for the annotation. Defaults to an empty mapping.
 * @param source The optional [KtSourceElement] representing the source of the annotation.
 * @param useSiteTarget The optional [AnnotationUseSiteTarget] specifying the use-site target of the annotation.
 * @return A [FirAnnotation] instance.
 */
public fun createFirAnnotation(
  type: ClassId,
  argumentMapping: FirAnnotationArgumentMapping = FirEmptyAnnotationArgumentMapping,
  source: KtSourceElement? = null,
  useSiteTarget: AnnotationUseSiteTarget? = null,
): FirAnnotation = buildAnnotation {
  this.argumentMapping = argumentMapping
  this.source = source
  this.useSiteTarget = useSiteTarget
  annotationTypeRef = buildResolvedTypeRef {
    coneType = type.constructClassLikeType()
  }
}
