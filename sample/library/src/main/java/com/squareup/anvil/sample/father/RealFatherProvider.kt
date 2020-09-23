package com.squareup.anvil.sample.father

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.sample.God
import com.squareup.anvil.sample.God.HEPHAESTUS
import com.squareup.anvil.sample.God.HERA
import com.squareup.anvil.sample.God.ZEUS
import com.squareup.scopes.AppScope

@ContributesBinding(AppScope::class)
object RealFatherProvider : FatherProvider {
  override fun father(god: God): String =
    when (god) {
      ZEUS, HERA -> "Cronus"
      HEPHAESTUS -> "Zeus"
    }
}
