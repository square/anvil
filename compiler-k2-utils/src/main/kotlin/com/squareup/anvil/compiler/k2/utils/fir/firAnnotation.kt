package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

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

public fun FirClassLikeSymbol<*>.contributesToAnnotations(
  session: FirSession,
): List<FirAnnotationCall> {
  return annotations.filter { it.fqName(session) == ClassIds.anvilContributesTo.asSingleFqName() }
    .map { it as FirAnnotationCall }
}

public fun FirClassLikeSymbol<*>.contributesToScope(
  scope: FqName,
  session: FirSession,
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): Boolean = contributesToAnnotations(session)
  .any {
    it.requireScopeArgument()
      .resolveConeType(typeResolveService)
      .requireFqName() == scope
  }

public fun FirBasedSymbol<*>.hasAnnotation(fqName: FqName, session: FirSession): Boolean {
  return annotations.any { it.fqName(session) == fqName }
}

public fun FirAnnotationCall.requireScopeArgument(): FirGetClassCall {
  return requireArgumentAt(name = Names.scope, index = 0, true) as FirGetClassCall
}

public fun FirAnnotationCall.classListArgumentAt(name: Name, index: Int): List<FirGetClassCall>? {
  val arrayArg = argumentAt(name = name, index = index, true) as? FirArrayLiteral
    ?: return null

  return arrayArg.arguments.map { it as FirGetClassCall }
}

public fun FirAnnotationCall.requireArgumentAt(
  name: String,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression = requireArgumentAt(
  name = Name.identifier(name),
  index = index,
  unwrapNamedArguments = unwrapNamedArguments,
)

public fun FirAnnotationCall.requireArgumentAt(
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

public fun FirAnnotation.argumentAt(
  name: String,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression? = argumentAt(name = Name.identifier(name), index = index, unwrapNamedArguments)

public fun FirAnnotation.argumentAt(
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

public fun ClassId.toFirAnnotation() = buildAnnotation {
  argumentMapping = FirEmptyAnnotationArgumentMapping
  annotationTypeRef = buildResolvedTypeRef {
    coneType = constructClassLikeType()
  }
}
