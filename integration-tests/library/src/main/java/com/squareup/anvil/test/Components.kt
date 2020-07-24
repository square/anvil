package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface AppComponentInterface

@ContributesTo(SubScope::class)
interface SubComponentInterface
