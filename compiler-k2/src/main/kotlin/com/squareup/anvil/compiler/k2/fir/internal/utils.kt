package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

public fun String.fqn(): FqName = FqName(this)
public fun FqName.classId(): ClassId = ClassId.topLevel(this)
public fun String.classId(): ClassId = fqn().classId()

public fun ConeKotlinType.requireClassId(): ClassId =
  requireNotNull(classId) { "ClassId is null: $this" }

internal fun ConeKotlinType.wrapInProvider(
  symbolProvider: FirSymbolProvider,
) = symbolProvider.getClassLikeSymbolByClassId(ClassIds.javaxProvider)!!
  .constructType(
    typeArguments = arrayOf(this@wrapInProvider),
    isMarkedNullable = false,
  )

internal fun FirPropertyAccessExpression.qualifierSegmentsWithSelf() = buildList<Name> {
  fun visitQualifiers(expression: FirExpression) {
    if (expression !is FirPropertyAccessExpression) return
    expression.explicitReceiver?.let { visitQualifiers(it) }
    expression.qualifierName?.let { add(it) }
  }
  visitQualifiers(this@qualifierSegmentsWithSelf)
}

internal fun FirGetClassCall.userTypeRef(): FirUserTypeRef {
  return buildUserTypeFromQualifierParts(isMarkedNullable = false) {
    (argument as FirPropertyAccessExpression)
      .qualifierSegmentsWithSelf()
      .forEach(::part)
  }
}

internal val FirPropertyAccessExpression.qualifierName: Name?
  get() = (calleeReference as? FirSimpleNamedReference)?.name

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
