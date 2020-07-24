package com.squareup.anvil.sample

import com.squareup.anvil.annotations.MergeComponent
import com.squareup.scopes.AppScope
import com.squareup.scopes.SingleIn

@SingleIn(AppScope::class)
@MergeComponent(AppScope::class)
interface AppComponent {
  fun loggedInComponent(): LoggedInComponent
}
