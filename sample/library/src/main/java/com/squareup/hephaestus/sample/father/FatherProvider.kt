package com.squareup.hephaestus.sample.father

import com.squareup.hephaestus.sample.God

interface FatherProvider {
  fun father(god: God): String
}
