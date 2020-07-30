package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ParentType

interface MiddleType : ParentType

@ContributesBinding(
    scope = AppScope::class,
    boundType = ParentType::class
)
class AppBinding @Inject constructor() : MiddleType

@ContributesBinding(SubScope::class)
class SubcomponentBinding @Inject constructor() : MiddleType
