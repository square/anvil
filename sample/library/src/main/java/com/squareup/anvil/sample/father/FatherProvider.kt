package com.squareup.anvil.sample.father

import com.squareup.anvil.sample.God

interface FatherProvider {
  fun father(god: God): String
}
