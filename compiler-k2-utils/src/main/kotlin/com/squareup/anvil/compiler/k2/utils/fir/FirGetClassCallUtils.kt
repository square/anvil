package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.isResolved
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

/** For `kotlin.Unit::class`, returns `kotlin.Unit`. */
public fun FirGetClassCall.requireTargetClassId(): ClassId = requireTargetType().requireClassId()

public fun FirGetClassCall.requireTargetType(): ConeKotlinType {
  checkWithAttachment(isResolved, { "Type is not yet resolved" }) {
    withFirEntry("FirGetClassCall", this@requireTargetType)
  }
  return getTargetType() ?: errorWithAttachment("Target type is null") {
    withFirEntry("FirGetClassCall", this@requireTargetType)
  }
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
