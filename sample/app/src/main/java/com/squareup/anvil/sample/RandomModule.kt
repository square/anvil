package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.scopes.AppScope
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
object RandomModule {
  @Provides @Named("random") fun provideString(): String = "Hello!"
}

@ContributesTo(AppScope::class)
interface RandomComponent {
  @Named("random") fun string(): String
}
