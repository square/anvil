package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.scopes.AppScope
import com.squareup.scopes.UserScope
import dagger.Module
import dagger.Provides
import javax.inject.Named

interface UserDescriptionProvider {
  @Named("userDesc")
  fun description(): String
}

@ContributesTo(UserScope::class)
@Module
object UserDescriptionModule {

  @Named("userName")
  @Provides
  fun provideName(): String = "Anvil User"

  @Named("userDesc")
  @Provides
  fun provideDescription(): String = "User description"
}

@ContributesSubcomponent(
  scope = UserScope::class,
  parentScope = AppScope::class,
)
interface UserComponent : UserDescriptionProvider {

  @Named("userName")
  fun username(): String

  @ContributesSubcomponent.Factory
  interface Factory {
    fun create(): UserDescriptionProvider
  }

  @ContributesTo(AppScope::class)
  interface Parent {
    fun user(): Factory
  }
}
