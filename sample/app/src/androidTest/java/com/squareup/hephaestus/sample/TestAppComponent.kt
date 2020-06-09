package com.squareup.hephaestus.sample

import com.squareup.hephaestus.annotations.MergeComponent
import com.squareup.scopes.AppScope
import com.squareup.scopes.SingleIn

@SingleIn(AppScope::class)
@MergeComponent(AppScope::class)
interface TestAppComponent {
  fun loggedInComponent(): LoggedInComponent
}
