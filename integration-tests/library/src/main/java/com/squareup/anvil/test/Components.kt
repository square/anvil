package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesTo

@ContributesTo(AppScope::class)
public interface AppComponentInterface

@ContributesTo(SubScope::class)
public interface SubComponentInterface
