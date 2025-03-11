package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

public fun FirBasedSymbol<*>.hasAnnotation(
  session: FirSession,
  classId: ClassId,
): Boolean {
  return annotations.any { it.classId(session) == classId }
}

public fun <D> FirBasedSymbol<D>.requireAnnotationCall(
  classId: ClassId,
  session: FirSession,
  resolveArguments: Boolean,
): FirAnnotationCall where D : FirAnnotationContainer,
                           D : FirDeclaration {
  return requireAnnotation(classId, session, resolveArguments) as FirAnnotationCall
}

public fun <D> FirBasedSymbol<D>.requireAnnotation(
  classId: ClassId,
  session: FirSession,
  resolveArguments: Boolean,
): FirAnnotation where D : FirAnnotationContainer,
                       D : FirDeclaration {
  return getResolvedAnnotation(classId, session, resolveArguments = resolveArguments)
    ?: errorWithAttachment("Annotation with classId $classId not found on $this") {
      withFirSymbolEntry("symbol", this@requireAnnotation)
    }
}

public fun <D> FirBasedSymbol<D>.getResolvedAnnotation(
  classId: ClassId,
  session: FirSession,
  resolveArguments: Boolean,
): FirAnnotation? where D : FirAnnotationContainer,
                        D : FirDeclaration {
  val resolved = if (resolveArguments) {
    resolvedAnnotationsWithArguments
  } else {
    resolvedCompilerAnnotationsWithClassIds
  }
  return resolved.getAnnotationByClassId(classId, session)
}
