package com.squareup.anvil.compiler.k2.fir.utils

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.FqName

internal fun FirSession.resolvedSymbol(fqName: FqName): FirClassLikeSymbol<*> {
  return symbolProvider.getClassLikeSymbolByClassId(fqName.classId)!!
}
