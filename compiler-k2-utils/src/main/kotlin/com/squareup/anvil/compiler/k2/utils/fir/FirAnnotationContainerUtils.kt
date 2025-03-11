package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.name.ClassId

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Resolve annotations from the container's symbol instead",
  level = DeprecationLevel.ERROR,
)
public fun FirAnnotationContainer.requireAnnotationCall(
  classId: ClassId,
  session: FirSession,
): FirAnnotationCall =
  throw UnsupportedOperationException("Resolve annotations from the container's symbol instead")

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Resolve annotations from the container's symbol instead",
  level = DeprecationLevel.ERROR,
)
public fun FirAnnotationContainer.requireAnnotation(
  classId: ClassId,
  session: FirSession,
): FirAnnotation =
  throw UnsupportedOperationException("Resolve annotations from the container's symbol instead")
