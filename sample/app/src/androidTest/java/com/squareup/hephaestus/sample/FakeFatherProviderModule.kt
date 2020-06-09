package com.squareup.hephaestus.sample

import com.squareup.hephaestus.annotations.ContributesTo
import com.squareup.hephaestus.sample.father.FatherProvider
import com.squareup.hephaestus.sample.father.FatherProviderModule
import com.squareup.scopes.AppScope
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = FatherProviderModule::class
)
abstract class FakeFatherProviderModule {
  @Binds abstract fun bindFatherProvider(provider: FakeFatherProvider): FatherProvider
}
