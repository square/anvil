package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.StandardClassIds

public fun FirRegularClassSymbol.resolvedTypeRef(): FirResolvedTypeRef {
  return buildResolvedTypeRef { coneType = toKClassRef() }
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
