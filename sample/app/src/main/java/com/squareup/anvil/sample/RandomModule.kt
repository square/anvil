package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.scopes.AppScope
import com.squareup.scopes.SingleIn
import dagger.Module
import dagger.Provides
import javax.inject.Named
import kotlin.random.Random

@Module
@ContributesTo(AppScope::class)
object RandomModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideRandomNumber() = Random.nextInt()

  @Provides @Named("random") fun provideString(number: Int): String = "Hello! $number"
}

@ContributesTo(AppScope::class)
interface RandomComponent {
  @Named("random") fun string(): String
}
