package com.squareup.hephaestus.sample.mother

import com.squareup.hephaestus.sample.God
import com.squareup.hephaestus.sample.God.HEPHAESTUS
import com.squareup.hephaestus.sample.God.HERA
import com.squareup.hephaestus.sample.God.ZEUS
import javax.inject.Inject

class RealMotherProvider @Inject constructor() : MotherProvider {
  override fun mother(god: God): String =
    when (god) {
      HERA, ZEUS -> "Rhea"
      HEPHAESTUS -> "Hera"
    }
}
