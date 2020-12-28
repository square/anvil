package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.sample.father.FatherProvider
import com.squareup.anvil.sample.father.RealFatherProvider
import com.squareup.scopes.AppScope

@ContributesBinding(
  scope = AppScope::class,
  replaces = [RealFatherProvider::class]
)
object FakeFatherProvider : FatherProvider {
  override fun father(god: God): String = "(No Father)"
}
