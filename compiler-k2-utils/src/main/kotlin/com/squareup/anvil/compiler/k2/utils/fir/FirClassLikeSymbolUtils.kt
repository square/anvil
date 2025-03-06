package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

public fun FirClassLikeSymbol<*>.contributesToAnnotations(
  session: FirSession,
): List<FirAnnotationCall> {
  return annotations.filter { it.classId(session) == ClassIds.anvilContributesTo }
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

public fun FirClassLikeSymbol<*>.contributesToScope(
  scope: ClassId,
  session: FirSession,
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): Boolean = contributesToAnnotations(session)
  .any {
    it.requireScopeArgument()
      .resolveConeType(typeResolveService)
      .requireClassId() == scope
  }

public fun FirClassLikeSymbol<*>.fqName(): FqName = classId.asSingleFqName()

/**
 * Creates the type name symbol as you would see it in a type argument, like
 * `com.foo.Bar` in `List<com.foo.Bar>`.
 */
public fun FirClassLikeSymbol<*>.coneLookupTagBasedType(): ConeLookupTagBasedType {
  return classId.toLookupTag().constructType(
    typeArguments = emptyArray<ConeTypeProjection>(),
    isMarkedNullable = false,
  )
}

/**
 * Creates a `kotlin.reflect.KClass` reference to the class symbol, like `KClass<com.foo.Bar>`.
 */
public fun FirClassLikeSymbol<*>.toKClassRef(): ConeClassLikeType =
  StandardClassIds.KClass.constructClassLikeType(
    typeArguments = arrayOf(coneLookupTagBasedType()),
    isMarkedNullable = false,
  )

public fun FirClassLikeSymbol<*>.toGetClassCall(): FirGetClassCall {
  return buildGetClassCall {
    coneTypeOrNull = this@toGetClassCall.toKClassRef()
    argumentList = buildUnaryArgumentList(
      buildClassReferenceExpression {
        val referencedType = this@toGetClassCall.coneLookupTagBasedType()
        val resolvedType = StandardClassIds.KClass
          .constructClassLikeType(arrayOf(referencedType), false)
        classTypeRef = buildResolvedTypeRef { coneType = referencedType }
        coneTypeOrNull = resolvedType
      },
    )
  }
}
