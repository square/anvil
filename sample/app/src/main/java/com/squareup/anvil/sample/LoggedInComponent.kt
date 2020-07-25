package com.squareup.anvil.sample

import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.scopes.LoggedInScope
import com.squareup.scopes.SingleIn

@SingleIn(LoggedInScope::class)
@MergeSubcomponent(LoggedInScope::class)
interface LoggedInComponent
