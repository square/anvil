package com.squareup.hephaestus.sample.father

import com.squareup.hephaestus.annotations.ContributesTo
import com.squareup.scopes.AppScope
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class FatherProviderModule {
  @Binds abstract fun bindFatherProvider(provider: RealFatherProvider): FatherProvider
}
