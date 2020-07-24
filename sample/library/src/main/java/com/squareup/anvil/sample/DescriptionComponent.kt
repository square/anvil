package com.squareup.anvil.sample

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.sample.father.FatherProvider
import com.squareup.anvil.sample.mother.MotherProvider
import com.squareup.scopes.AppScope

@ContributesTo(AppScope::class)
interface DescriptionComponent {
  fun fatherProvider(): FatherProvider
  fun motherProvider(): MotherProvider
}
