package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.sample.father.FatherProvider
import com.squareup.anvil.sample.mother.MotherProvider
import com.squareup.scopes.AppScope

@ContributesTo(AppScope::class)
interface DescriptionComponent {
  fun fatherProvider(): FatherProvider
  fun motherProvider(): MotherProvider
}

@ContributesSubcomponent(
  scope = FatherProvider::class,
  parentScope = AppScope::class,
)
public interface FatherProviderComponent {
  @ContributesSubcomponent.Factory
  public interface Factory {
    public fun create(): FatherProviderComponent
  }

  @ContributesTo(AppScope::class)
  public interface ParentComponent {
    public fun fatherProviderComponentFactory(): Factory
  }
}

@ContributesSubcomponent(
  scope = MotherProvider::class,
  parentScope = AppScope::class,
)
public interface MotherProviderComponent {
  @ContributesSubcomponent.Factory
  public interface Factory {
    public fun create(): MotherProviderComponent
  }

  @ContributesTo(AppScope::class)
  public interface ParentComponent {
    public fun motherProviderComponentFactory(): Factory
  }
}
