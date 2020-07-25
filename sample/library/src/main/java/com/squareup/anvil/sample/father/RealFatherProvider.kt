package com.squareup.anvil.sample.father

import com.squareup.anvil.sample.God
import com.squareup.anvil.sample.God.HEPHAESTUS
import com.squareup.anvil.sample.God.HERA
import com.squareup.anvil.sample.God.ZEUS
import javax.inject.Inject

class RealFatherProvider @Inject constructor() : FatherProvider {
  override fun father(god: God): String =
    when (god) {
      ZEUS, HERA -> "Cronus"
      HEPHAESTUS -> "Zeus"
    }
}
