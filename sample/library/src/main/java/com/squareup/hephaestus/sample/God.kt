package com.squareup.hephaestus.sample

import com.squareup.hephaestus.sample.Parent.FATHER
import com.squareup.hephaestus.sample.Parent.MOTHER

enum class God(val parent: Parent) {
  ZEUS(FATHER),
  HERA(MOTHER),
  HEPHAESTUS(FATHER),
}

enum class Parent {
  FATHER, MOTHER
}
