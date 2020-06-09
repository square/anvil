package com.squareup.hephaestus.test

import com.squareup.hephaestus.annotations.ContributesTo
import dagger.Module

@Module
@ContributesTo(SubScope::class)
abstract class SubModule

@ContributesTo(SubScope::class)
interface SubComponentInterface
