package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

public interface ParentType

public interface MiddleType : ParentType

@ContributesBinding(
  scope = AppScope::class,
  boundType = ParentType::class
)
public class AppBinding @Inject constructor() : MiddleType

@ContributesBinding(SubScope::class)
public object SubcomponentBinding : MiddleType
