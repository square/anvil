package com.squareup.hephaestus.test

import com.squareup.hephaestus.annotations.ContributesTo
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class AppModule

@Module
@ContributesTo(SubScope::class)
abstract class SubModule
