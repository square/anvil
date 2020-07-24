package com.squareup.anvil.sample

import com.squareup.anvil.sample.Parent.FATHER
import com.squareup.anvil.sample.Parent.MOTHER

enum class God(val parent: Parent) {
  ZEUS(FATHER),
  HERA(MOTHER),
  HEPHAESTUS(FATHER),
}

enum class Parent {
  FATHER, MOTHER
}
