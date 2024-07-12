package com.squareup.anvil.compiler.k2

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer

public class CanaryTransformer : FirDefaultTransformer<Unit>() {

  override fun <E : FirElement> transformElement(element: E, data: Unit): E {
    TODO("Not yet implemented, element: $element")
  }
}
