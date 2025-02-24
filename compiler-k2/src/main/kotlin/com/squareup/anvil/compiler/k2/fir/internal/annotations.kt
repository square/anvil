package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.fir.argumentAt
import com.squareup.anvil.compiler.k2.utils.fir.classListArgumentAt
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun FirAnnotation.requireFqName(session: FirSession): FqName {
  return requireNotNull(fqName(session)) { "FqName not found for $this" }
}

internal fun FirAnnotation.classId(session: FirSession): ClassId? = fqName(session)?.classId()
internal fun FirAnnotation.requireClassId(session: FirSession): ClassId {
  return requireFqName(session).classId()
}

internal fun FirClassLikeSymbol<*>.contributesToAnnotations(
  session: FirSession,
): List<FirAnnotationCall> {
  return annotations.filter { it.classId(session) == ClassIds.anvilContributesTo }
    .map { it as FirAnnotationCall }
}

public fun <D> FirBasedSymbol<D>.requireAnnotation(
  classId: ClassId,
  session: FirSession,
): FirAnnotationCall where D : FirAnnotationContainer,
                           D : FirDeclaration {
  return getAnnotationByClassId(classId, session).also {
    requireNotNull(it) { "Annotation with classId $classId not found on $this" }
  } as FirAnnotationCall
}

public fun <D> FirBasedSymbol<D>.requireAnnotation(
  annotationFqName: FqName,
  session: FirSession,
): FirAnnotationCall where D : FirAnnotationContainer,
                           D : FirDeclaration {
  return requireAnnotation(annotationFqName.classId(), session)
}

internal fun FirAnnotationCall.requireReplacesArgument(session: FirSession): List<FirGetClassCall> {
  return classListArgumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
  ).orEmpty()
}

private fun replacesIndex(annotationClassId: ClassId): Int {
  return when (annotationClassId) {
    ClassIds.anvilContributesTo -> 1
    ClassIds.anvilContributesBinding, ClassIds.anvilContributesMultibinding -> 2
    ClassIds.anvilContributesSubcomponent -> 4
    else -> throw NotImplementedError(
      "Couldn't find index of replaces argument for $annotationClassId.",
    )
  }
}

internal fun FirAnnotationCall.requireArgumentAt(
  name: String,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression = requireArgumentAt(
  name = Name.identifier(name),
  index = index,
  unwrapNamedArguments = unwrapNamedArguments,
)

internal fun FirAnnotationCall.requireArgumentAt(
  name: Name,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression = argumentAt(
  name = name,
  index = index,
  unwrapNamedArguments = unwrapNamedArguments,
)
  ?: error(
    """
      |Could not find required argument for annotation: $this
      |                      Required name: $name
      |Required index (if no name is used): $index
      |existing arguments:
      |${arguments.joinToString("\n") { "    $it" }}
    """.trimMargin(),
  )

internal fun FirAnnotation.argumentAt(
  name: String,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression? = argumentAt(name = Name.identifier(name), index = index, unwrapNamedArguments)
