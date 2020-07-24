package com.squareup.anvil.sample

import com.squareup.anvil.sample.father.FatherProvider
import javax.inject.Inject

class FakeFatherProvider @Inject constructor() : FatherProvider {
  override fun father(god: God): String = "(No Father)"
}
