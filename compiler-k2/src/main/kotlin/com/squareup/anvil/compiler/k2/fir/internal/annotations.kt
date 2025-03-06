package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.fir.argumentAt
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isResolved
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.exceptions.KotlinIllegalArgumentExceptionWithAttachments
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

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

internal fun FirClassLikeSymbol<*>.getContributesBindingAnnotations(
  session: FirSession,
): List<FirAnnotationCall> {
  return annotations.filter { it.classId(session) == ClassIds.anvilContributesBinding }
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

internal fun FirClassLikeSymbol<*>.contributesToScope(
  scope: ClassId,
  session: FirSession,
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): Boolean = contributesToAnnotations(session)
  .any {
    it.requireScopeArgument()
      .resolveConeType(typeResolveService)
      .requireClassId() == scope
  }

internal fun FirBasedSymbol<*>.hasAnnotation(classId: ClassId, session: FirSession): Boolean {
  return annotations.any { it.classId(session) == classId }
}

internal fun FirBasedSymbol<*>.hasAnnotation(fqName: FqName, session: FirSession): Boolean {
  return annotations.any { it.fqName(session) == fqName }
}

internal fun FirAnnotationCall.boundTypeArgumentOrNull(session: FirSession): ConeKotlinType? {
  return getKClassArgument(Names.boundType, session)
}

internal fun FirAnnotationCall.requireScopeArgument(session: FirSession): ConeKotlinType {

  val expression = requireArgumentAt(
    name = Names.scope,
    index = 0,
    unwrapNamedArguments = true,
  )
  checkWithAttachment(
    expression is FirGetClassCall,
    { "Scope argument is not a FirGetClassCall" },
  ) {
    withFirEntry("scope argument expression", expression)
  }
  check(expression.isResolved) { "getClassCall is not resolved -- ${render()}" }
  val evaluated = expression.evaluateAs<FirGetClassCall>(session)
    ?: errorWithAttachment("Scope argument is not a FirGetClassCall: $expression") {
      withFirEntry("scope argument expression", expression)
    }
  return evaluated.requireTargetType()
}

/** For `kotlin.Unit::class`, returns `kotlin.Unit`. */
internal fun FirGetClassCall.requireTargetClassId(): ClassId = requireTargetType().requireClassId()

internal fun FirGetClassCall.requireTargetType(): ConeKotlinType {
  checkWithAttachment(isResolved, { "Type is not yet resolved" }) {
    withFirEntry("FirGetClassCall", this@requireTargetType)
  }
  return getTargetType() ?: errorWithAttachment("Target type is null") {
    withFirEntry("FirGetClassCall", this@requireTargetType)
  }
}

internal fun FirAnnotationCall.requireScopeArgument(): FirGetClassCall = requireArgumentAt(
  name = Names.scope,
  index = 0,
  unwrapNamedArguments = true,
) as FirGetClassCall

internal fun FirAnnotationCall.requireScopeArgument(
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): ConeKotlinType = requireScopeArgument().resolveConeType(typeResolveService)

internal fun FirAnnotationCall.replacesArgumentOrNull(session: FirSession): List<FirGetClassCall>? {
  return classListArgumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
  )
}

internal fun FirAnnotationCall.rankArgumentOrNull(session: FirSession): Int? {
  val arg = argumentAt(
    name = Names.rank,
    index = rankIndex(requireClassId(session)),
    unwrapNamedArguments = true,
  ) ?: return null
  return arg.evaluateAs<FirLiteralExpression>(session)?.value as? Int
}

internal fun FirAnnotationCall.requireReplacesArgument(session: FirSession): List<FirGetClassCall> {
  return classListArgumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
  ).orEmpty()
}

private fun rankIndex(annotationClassId: ClassId): Int {
  return when (annotationClassId) {
    ClassIds.anvilContributesBinding, ClassIds.anvilContributesMultibinding -> 6
    else -> throw NotImplementedError(
      "Couldn't find index of rank argument for $annotationClassId.",
    )
  }
}

private fun replacesIndex(annotationClassId: ClassId): Int {
  return when (annotationClassId) {
    ClassIds.anvilContributesTo -> 1
    ClassIds.anvilContributesBinding, ClassIds.anvilContributesMultibinding -> 2
    ClassIds.anvilContributesSubcomponent -> 4
    else -> errorWithAttachment(
      "Couldn't find index of replaces argument for $annotationClassId.",
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

/**
 * Returns the argument expression for the given name or index, or throws if it cannot be found.
 *
 * If the argument is specified by name, the [index] is ignored.
 *
 * If the argument is named and [unwrapNamedArguments] is `false`, the [FirNamedArgumentExpression]
 * is returned. If it is `true`, the argument is unwrapped.
 *
 * | unwrapNamedArguments | Result                   |
 * |----------------------|--------------------------|
 * | true                 | `scope = MyScope::class` |
 * | false                | `MyScope::class`         |
 *
 * @param name The name of the argument.
 * @param index The index of the argument if no name is used.
 * @param unwrapNamedArguments If true, the argument is unwrapped if it is a [FirNamedArgumentExpression].
 * @return The argument expression.
 * @throws KotlinIllegalArgumentExceptionWithAttachments If the argument cannot be found.
 */
internal fun FirAnnotationCall.requireArgumentAt(
  name: Name,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression = argumentAt(
  name = name,
  index = index,
  unwrapNamedArguments = unwrapNamedArguments,
)
  ?: errorWithAttachment(
    """
      |Could not find required argument for annotation: $this
      |                      Required name: $name
      |Required index (if no name is used): $index
      |existing arguments:
      |${arguments.joinToString("\n") { "    $it" }}
    """.trimMargin(),
  )

/**
 * Returns the argument expression for the given name or index, or throws if it cannot be found.
 *
 * If the argument is specified by name, the [index] is ignored.
 *
 * If the argument is named and [unwrapNamedArguments] is `false`, the [FirNamedArgumentExpression]
 * is returned. If it is `true`, the argument is unwrapped.
 *
 * | unwrapNamedArguments | Result                   |
 * |----------------------|--------------------------|
 * | true                 | `scope = MyScope::class` |
 * | false                | `MyScope::class`         |
 *
 * @param name The name of the argument.
 * @param index The index of the argument if no name is used.
 * @param unwrapNamedArguments If true, the argument is unwrapped if it is a [FirNamedArgumentExpression].
 * @return The argument expression.
 * @throws KotlinIllegalArgumentExceptionWithAttachments If the argument cannot be found.
 */
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
