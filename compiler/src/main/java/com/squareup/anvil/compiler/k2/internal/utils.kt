package com.squareup.anvil.compiler.k2.internal

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.FirAnnotationCallBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
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
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.asCallableReferenceExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public fun String.fqn(): FqName = FqName(this)
public fun FqName.classId(): ClassId = ClassId.topLevel(this)
public fun String.classId(): ClassId = fqn().classId()

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
    isNullable = false,
  )
}

/**
 * Creates a `kotlin.reflect.KClass` reference to the class symbol, like `KClass<com.foo.Bar>`.
 */
internal fun FirRegularClassSymbol.toKClassRef(): ConeClassLikeType =
  StandardClassIds.KClass.constructClassLikeType(
    typeArguments = arrayOf(coneLookupTagBasedType()),
    isNullable = false,
  )

internal fun FirRegularClassSymbol.resolvedTypeRef(): FirResolvedTypeRef {
  return buildResolvedTypeRef { type = toKClassRef() }
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

internal fun buildGetClassCall(classSymbol: FirRegularClassSymbol): FirGetClassCall {
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
