package com.squareup.anvil.sample

import com.squareup.anvil.sample.Parent.FATHER
import com.squareup.anvil.sample.Parent.MOTHER
import com.squareup.scopes.ComponentHolder
import java.util.Locale

object Description {
  fun of(god: God): String {
    val name = god.name.toLowerCase(Locale.US)
        .capitalize(Locale.US)

    val child = when (god.parent) {
      FATHER -> "son"
      MOTHER -> "daughter"
    }

    return "$name, $child of " +
        "${ComponentHolder.component<DescriptionComponent>().fatherProvider().father(god)} and " +
        ComponentHolder.component<DescriptionComponent>().motherProvider().mother(god)
  }
}
