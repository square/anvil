package com.squareup.anvil.sample.mother

import com.squareup.anvil.sample.God

interface MotherProvider {
  fun mother(god: God): String
}
