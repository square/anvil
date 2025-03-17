package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension

public abstract class AnvilFirDeclarationGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session)
