package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public fun ConeKotlinType.wrapInProvider(
  symbolProvider: FirSymbolProvider,
): ConeClassLikeType = symbolProvider.getClassLikeSymbolByClassId(ClassIds.javaxProvider)!!
  .constructType(
    typeArguments = arrayOf(this@wrapInProvider),
    isMarkedNullable = false,
  )

public fun ConeKotlinType.requireClassId(): ClassId =
  requireNotNull(classId) { "ClassId is null: $this" }

public fun ConeKotlinType.requireFqName(): FqName {
  return this@requireFqName.requireClassId().asSingleFqName()
}
