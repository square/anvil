package com.squareup.hephaestus.test

import com.squareup.hephaestus.annotations.ContributesTo

@ContributesTo(AppScope::class)
interface AppComponentInterface

@ContributesTo(SubScope::class)
interface SubComponentInterface
