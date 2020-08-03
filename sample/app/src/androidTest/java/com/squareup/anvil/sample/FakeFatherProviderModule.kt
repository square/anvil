package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.sample.father.FatherProvider
import com.squareup.anvil.sample.father.FatherProviderModule
import com.squareup.scopes.AppScope
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [FatherProviderModule::class]
)
abstract class FakeFatherProviderModule {
  @Binds abstract fun bindFatherProvider(provider: FakeFatherProvider): FatherProvider
}
