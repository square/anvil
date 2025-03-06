package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.expressions.builder.FirAnnotationCallBuilder
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.asCallableReferenceExpression
import org.jetbrains.kotlin.toKtPsiSourceElement

public fun FirAnnotationCallBuilder.setAnnotationType(
  newType: ClassId,
  ktPsiFactoryOrNull: KtPsiFactory?,
) {
  val componentTypeRef = ktPsiFactoryOrNull
    ?.createTypeArgument(newType.asFqNameString())
    ?.typeReference

  annotationTypeRef = createUserTypeRef(
    classId = newType,
    sourceElement = componentTypeRef?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated),
    nullable = false,
  )
  calleeReference = buildSimpleNamedReference {
    name = newType.shortClassName
    source = componentTypeRef?.asCallableReferenceExpression()
      ?.callableReference
      ?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
  }
}
