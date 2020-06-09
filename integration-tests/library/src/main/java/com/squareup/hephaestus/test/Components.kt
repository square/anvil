package com.squareup.hephaestus.test

import com.squareup.hephaestus.annotations.ContributesTo
import dagger.Module

@Module
@ContributesTo(AppScope::class)
abstract class AppModule

@ContributesTo(AppScope::class)
interface AppComponentInterface
