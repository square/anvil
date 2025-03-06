package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.ClassId

public fun createUserTypeRef(
  classId: ClassId,
  sourceElement: KtSourceElement?,
  nullable: Boolean = false,
): FirUserTypeRef {
  return buildUserTypeRef {
    isMarkedNullable = nullable
    source = sourceElement
    classId.asSingleFqName().pathSegments()
      .mapTo(qualifier) { name ->
        FirQualifierPartImpl(
          source = null,
          name = name,
          typeArgumentList = FirTypeArgumentListImpl(source = null),
        )
      }
  }
}
