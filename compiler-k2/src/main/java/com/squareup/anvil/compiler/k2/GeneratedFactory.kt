package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol

internal class GeneratedFactory(
  val matchedConstructor: FirConstructorSymbol,
  val firExtension: FirExtension,
)
