package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.scopes.AppScope
import dagger.Module
import dagger.Provides

sealed interface GodScope

@MergeModules(GodScope::class)
@ContributesTo(AppScope::class)
interface GodModule

@Module
@ContributesTo(GodScope::class)
object ZeusGodModule {
  @Provides
  fun god(): God = God.ZEUS
}
