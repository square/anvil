package com.squareup.anvil.sample.mother

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.sample.God
import com.squareup.anvil.sample.God.HEPHAESTUS
import com.squareup.anvil.sample.God.HERA
import com.squareup.anvil.sample.God.ZEUS
import com.squareup.scopes.AppScope

@ContributesBinding(AppScope::class)
object RealMotherProvider : MotherProvider {
  override fun mother(god: God): String =
    when (god) {
      HERA, ZEUS -> "Rhea"
      HEPHAESTUS -> "Hera"
    }
}
