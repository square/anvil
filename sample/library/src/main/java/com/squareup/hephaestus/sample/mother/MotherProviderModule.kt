package com.squareup.hephaestus.sample.mother

import com.squareup.hephaestus.annotations.ContributesTo
import com.squareup.scopes.AppScope
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class MotherProviderModule {
  @Binds abstract fun bindMotherProvider(provider: RealMotherProvider): MotherProvider
}
