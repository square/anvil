package com.squareup.hephaestus.sample.mother

import com.squareup.hephaestus.sample.God

interface MotherProvider {
  fun mother(god: God): String
}
