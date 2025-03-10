package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

public fun FirAnnotationContainer.requireAnnotationCall(
  classId: ClassId,
  session: FirSession,
): FirAnnotationCall {
  return requireAnnotation(classId, session) as FirAnnotationCall
}

public fun FirAnnotationContainer.requireAnnotation(
  classId: ClassId,
  session: FirSession,
): FirAnnotation {
  return getAnnotationByClassId(classId, session)
    ?: errorWithAttachment("Annotation with classId $classId not found on $this") {
      withFirEntry("annotation container", this@requireAnnotation)
    }
}
