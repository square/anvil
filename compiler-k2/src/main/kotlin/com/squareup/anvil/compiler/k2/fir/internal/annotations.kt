package com.squareup.anvil.compiler.k2.fir.internal

import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
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
  return annotations.filter { it.fqName(session) == Names.anvil.contributesTo }
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

internal fun FirClassLikeSymbol<*>.contributesToScope(
  scope: FqName,
  session: FirSession,
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): Boolean = contributesToAnnotations(session)
  .any {
    it.requireScopeArgument()
      .resolveConeType(typeResolveService)
      .requireFqName() == scope
  }

internal fun FirBasedSymbol<*>.hasAnnotation(fqName: FqName, session: FirSession): Boolean {
  return annotations.any { it.fqName(session) == fqName }
}

internal fun FirAnnotationCall.requireScopeArgument(): FirGetClassCall {
  return requireArgumentAt(
    name = Names.identifiers.scope,
    index = 0,
    unwrapNamedArguments = true,
  ) as FirGetClassCall
}

internal fun FirAnnotationCall.requireReplacesArgument(session: FirSession): List<FirGetClassCall> {
  return classListArgumentAt(
    name = Names.identifiers.replaces,
    index = replacesIndex(requireFqName(session)),
  ).orEmpty()
}

private fun replacesIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    Names.anvil.contributesTo -> 1
    Names.anvil.contributesBinding, Names.anvil.contributesMultibinding -> 2
    Names.anvil.contributesSubcomponent -> 4
    else -> throw NotImplementedError(
      "Couldn't find index of replaces argument for $annotationFqName.",
    )
  }
}

internal fun FirAnnotationCall.classListArgumentAt(name: Name, index: Int): List<FirGetClassCall>? {
  val arrayArg = argumentAt(name = name, index = index, true) as? FirArrayLiteral
    ?: return null

  return arrayArg.arguments.map { it as FirGetClassCall }
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

internal fun FirAnnotation.argumentAt(
  name: Name,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression? {
  argumentMapping.mapping[name]?.let { return it }

  if (this !is FirAnnotationCall) return null

  var nameUsed = false
  // NB: we have to consider both cases, because deserializer does not create argument mapping
  for ((i, argument) in arguments.withIndex()) {
    if (argument is FirNamedArgumentExpression) {
      nameUsed = true
      if (argument.name == name) {
        return if (unwrapNamedArguments) {
          argument.unwrapArgument()
        } else {
          argument
        }
      }
    } else if (!nameUsed && i == index) {
      return argument
    }
  }

  return null
}
