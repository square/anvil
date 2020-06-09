package com.squareup.hephaestus.sample.father

import com.squareup.hephaestus.sample.God
import com.squareup.hephaestus.sample.God.HEPHAESTUS
import com.squareup.hephaestus.sample.God.HERA
import com.squareup.hephaestus.sample.God.ZEUS
import javax.inject.Inject

class RealFatherProvider @Inject constructor() : FatherProvider {
  override fun father(god: God): String =
    when (god) {
      ZEUS, HERA -> "Cronus"
      HEPHAESTUS -> "Zeus"
    }
}
