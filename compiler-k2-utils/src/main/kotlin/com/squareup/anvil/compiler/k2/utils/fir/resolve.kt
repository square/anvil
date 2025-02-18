package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId

/**
 * Retrieves the [FirRegularClassSymbol] associated with this [ClassId].
 *
 * @param session The [FirSession] used for symbol resolution.
 * @return The [FirRegularClassSymbol] corresponding to the given [ClassId].
 * @throws ClassCastException if the resolved symbol is not a [FirRegularClassSymbol].
 */
public fun ClassId.requireRegularClassSymbol(session: FirSession): FirRegularClassSymbol =
  requireClassLikeSymbol(session) as FirRegularClassSymbol

/**
 * Retrieves the [FirClassLikeSymbol] associated with this [ClassId].
 *
 * @param session The [FirSession] used for symbol resolution.
 * @return The [FirClassLikeSymbol] corresponding to the given [ClassId].
 * @throws IllegalArgumentException if no symbol is found for the given [ClassId].
 */
public fun ClassId.requireClassLikeSymbol(session: FirSession): FirClassLikeSymbol<*> =
  requireNotNull(session.symbolProvider.getClassLikeSymbolByClassId(this)) {
    "No class like symbol found for class ID: $this"
  }

/**
 * Attempts to retrieve the [FirRegularClassSymbol] associated with this [ClassId].
 *
 * @param session The [FirSession] used for symbol resolution.
 * @return The [FirRegularClassSymbol] if found, or `null` if not present.
 */
public fun ClassId.regularClassSymbolOrNull(session: FirSession): FirRegularClassSymbol? =
  session.symbolProvider.getClassLikeSymbolByClassId(this) as? FirRegularClassSymbol

/**
 * Attempts to retrieve the [FirClassLikeSymbol] associated with this [ClassId].
 *
 * @param session The [FirSession] used for symbol resolution.
 * @return The [FirClassLikeSymbol] if found, or `null` if not present.
 */
public fun ClassId.classLikeSymbolOrNull(session: FirSession): FirClassLikeSymbol<*>? =
  session.symbolProvider.getClassLikeSymbolByClassId(this)
