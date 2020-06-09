package com.squareup.hephaestus.sample

import com.squareup.hephaestus.annotations.MergeSubcomponent
import com.squareup.scopes.LoggedInScope
import com.squareup.scopes.SingleIn

@SingleIn(LoggedInScope::class)
@MergeSubcomponent(LoggedInScope::class)
interface LoggedInComponent
