package com.squareup.hephaestus.sample

import com.squareup.hephaestus.annotations.ContributesTo
import com.squareup.hephaestus.sample.father.FatherProvider
import com.squareup.hephaestus.sample.mother.MotherProvider
import com.squareup.scopes.AppScope

@ContributesTo(AppScope::class)
interface DescriptionComponent {
  fun fatherProvider(): FatherProvider
  fun motherProvider(): MotherProvider
}
