package com.squareup.anvil.compiler.k2.internal

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.resolved
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.FirAnnotationCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.asCallableReferenceExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public fun String.fqn(): FqName = FqName(this)
public fun FqName.classId(): ClassId = ClassId.topLevel(this)
public fun String.classId(): ClassId = fqn().classId()

internal fun FirAnnotation.getGetKClassArgument(name: Name): FirGetClassCall? {
  return findArgumentByName(name) as? FirGetClassCall
}

internal fun FirAnnotation.findArgumentByName(
  name: Name,
  returnFirstWhenNotFound: Boolean = true,
): FirExpression? {
  argumentMapping.mapping[name]?.let { return it }
  if (this !is FirAnnotationCall) return null

  // NB: we have to consider both cases, because deserializer does not create argument mapping
  for (argument in arguments) {
    if (argument is FirNamedArgumentExpression && argument.name == name) {
      return argument.expression
    }
  }

  // The condition is required for annotation arguments that are not fully resolved. For example, CompilerRequiredAnnotations.
  // When the annotation is resolved, and we did not find an argument with the given name,
  // there is no argument and we should return null.
  return if (!resolved && returnFirstWhenNotFound) arguments.firstOrNull() else null
}

internal fun PsiElement.ktPsiFactory(): KtPsiFactory {
  return KtPsiFactory.contextual(
    context = this@ktPsiFactory,
    markGenerated = false,
    eventSystemEnabled = false,
  )
}

/**
 * Creates the type name symbol as you would see it in a type argument, like
 * `com.foo.Bar` in `List<com.foo.Bar>`.
 */
internal fun FirRegularClassSymbol.coneLookupTagBasedType(): ConeLookupTagBasedType {
  return classId.toLookupTag().constructType(
    typeArguments = emptyArray<ConeTypeProjection>(),
    isMarkedNullable = false,
  )
}

/**
 * Creates a `kotlin.reflect.KClass` reference to the class symbol, like `KClass<com.foo.Bar>`.
 */
internal fun FirRegularClassSymbol.toKClassRef(): ConeClassLikeType =
  StandardClassIds.KClass.constructClassLikeType(
    typeArguments = arrayOf(coneLookupTagBasedType()),
    isMarkedNullable = false,
  )

internal fun FirRegularClassSymbol.resolvedTypeRef(): FirResolvedTypeRef {
  return buildResolvedTypeRef { coneType = toKClassRef() }
}

internal fun FirAnnotationCallBuilder.setAnnotationType(
  newType: FqName,
  ktPsiFactoryOrNull: KtPsiFactory?,
) {
  val componentTypeRef = ktPsiFactoryOrNull
    ?.createTypeArgument(newType.asString())
    ?.typeReference

  annotationTypeRef = newType
    .createUserType(
      sourceElement = componentTypeRef?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated),
      nullable = false,
    )
  calleeReference = buildSimpleNamedReference {
    name = newType.shortName()
    source = componentTypeRef?.asCallableReferenceExpression()
      ?.callableReference
      ?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
  }
}

internal fun FqName.createUserType(
  sourceElement: KtSourceElement?,
  nullable: Boolean = false,
): FirUserTypeRef {
  return buildUserTypeRef b@{
    isMarkedNullable = nullable
    source = sourceElement
    pathSegments().mapTo(qualifier) { name ->
      FirQualifierPartImpl(
        source = null,
        name = name,
        typeArgumentList = FirTypeArgumentListImpl(source = null),
      )
    }
  }
}

internal fun buildGetClassCall(classSymbol: FirClassLikeSymbol<*>): FirGetClassCall {
  return buildGetClassCall {
    argumentList = buildArgumentList {
      arguments += buildResolvedQualifier qualifier@{
        val builder = this
        builder.symbol = classSymbol
        builder.packageFqName = classSymbol.packageFqName()
      }
    }
  }
}
