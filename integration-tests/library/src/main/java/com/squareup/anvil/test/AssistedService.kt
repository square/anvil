package com.squareup.anvil.test

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

public class AssistedService @AssistedInject constructor(
  public val integer: Int,
  @Assisted public val string: String
) {

  @AssistedFactory
  public interface Factory {
    public fun create(string: String): AssistedService
  }
}
