package com.squareup.hephaestus.test

import com.squareup.hephaestus.annotations.ContributesTo
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class AppModule1

@Module
@ContributesTo(AppScope::class)
interface AppModule2

@Module
@ContributesTo(SubScope::class)
abstract class SubModule1

@Module
@ContributesTo(SubScope::class)
interface SubModule2
