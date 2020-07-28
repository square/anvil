package com.squareup.anvil.sample.mother

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.sample.God
import com.squareup.anvil.sample.God.HEPHAESTUS
import com.squareup.anvil.sample.God.HERA
import com.squareup.anvil.sample.God.ZEUS
import com.squareup.scopes.AppScope
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealMotherProvider @Inject constructor() : MotherProvider {
  override fun mother(god: God): String =
    when (god) {
      HERA, ZEUS -> "Rhea"
      HEPHAESTUS -> "Hera"
    }
}
