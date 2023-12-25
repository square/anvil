package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

public abstract class AssistedScope private constructor()

public interface AssistedService {

  public val string: String

  public interface Factory {
    public fun create(string: String): AssistedService
  }
}

public class AssistedServiceImpl @AssistedInject constructor(
  public val integer: Int,
  @Assisted public override val string: String,
) : AssistedService {

  @AssistedFactory
  @ContributesBinding(AssistedScope::class)
  public interface Factory : AssistedService.Factory {
    public override fun create(string: String): AssistedServiceImpl
  }
}
