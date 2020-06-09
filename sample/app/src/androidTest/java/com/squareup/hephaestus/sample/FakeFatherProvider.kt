package com.squareup.hephaestus.sample

import com.squareup.hephaestus.sample.father.FatherProvider
import javax.inject.Inject

class FakeFatherProvider @Inject constructor() : FatherProvider {
  override fun father(god: God): String = "(No Father)"
}
