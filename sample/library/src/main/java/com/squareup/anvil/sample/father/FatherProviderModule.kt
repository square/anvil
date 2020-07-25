package com.squareup.anvil.sample.father

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.scopes.AppScope
import dagger.Binds
import dagger.Module

@Module
@ContributesTo(AppScope::class)
interface FatherProviderModule {
  @Binds fun bindFatherProvider(provider: RealFatherProvider): FatherProvider
}
