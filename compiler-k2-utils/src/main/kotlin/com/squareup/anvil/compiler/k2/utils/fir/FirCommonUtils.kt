package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.FirAnnotationCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
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
import org.jetbrains.kotlin.fir.types.isResolved
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.asCallableReferenceExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public fun ConeKotlinType.wrapInProvider(
  symbolProvider: FirSymbolProvider,
): ConeClassLikeType = symbolProvider.getClassLikeSymbolByClassId(ClassIds.javaxProvider)!!
  .constructType(
    typeArguments = arrayOf(this@wrapInProvider),
    isMarkedNullable = false,
  )

public fun ConeKotlinType.requireFqName(): FqName {
  return this@requireFqName.classId!!.asSingleFqName()
}

public fun FirClassLikeSymbol<*>.fqName(): FqName = classId.asSingleFqName()

public fun PsiElement.ktPsiFactory(): KtPsiFactory {
  return KtPsiFactory.contextual(
    context = this@ktPsiFactory,
    markGenerated = false,
    eventSystemEnabled = false,
  )
}

public fun FirPropertyAccessExpression.qualifierSegmentsWithSelf(): List<Name> = buildList<Name> {
  fun visitQualifiers(expression: FirExpression) {
    if (expression !is FirPropertyAccessExpression) return
    expression.explicitReceiver?.let { visitQualifiers(it) }
    expression.qualifierName?.let { add(it) }
  }
  visitQualifiers(this@qualifierSegmentsWithSelf)
}

public fun FirGetClassCall.userTypeRef(): FirUserTypeRef {
  return buildUserTypeFromQualifierParts(isMarkedNullable = false) {
    (argument as FirPropertyAccessExpression)
      .qualifierSegmentsWithSelf()
      .forEach(::part)
  }
}

// https://github.com/JetBrains/kotlin/blob/master/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt
public fun FirGetClassCall.resolveConeType(
  typeResolveService: TypeResolveService,
): ConeKotlinType = if (isResolved) {
  resolvedType
} else {
  typeResolveService.resolveUserType(userTypeRef()).coneType
}

public val FirPropertyAccessExpression.qualifierName: Name?
  get() = (calleeReference as? FirSimpleNamedReference)?.name

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

public fun FirRegularClassSymbol.resolvedTypeRef(): FirResolvedTypeRef {
  return buildResolvedTypeRef { coneType = toKClassRef() }
}

public fun FirAnnotationCallBuilder.setAnnotationType(
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

public fun FqName.createUserType(
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
