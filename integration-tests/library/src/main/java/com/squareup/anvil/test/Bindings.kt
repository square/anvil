package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Named

public interface ParentType

public interface MiddleType : ParentType

@ContributesBinding(
  scope = AppScope::class,
  boundType = ParentType::class,
)
@ContributesMultibinding(
  scope = AppScope::class,
  boundType = ParentType::class,
)
public class AppBinding @Inject constructor() : MiddleType

@ContributesBinding(SubScope::class)
@ContributesMultibinding(SubScope::class, ignoreQualifier = true)
@Named("middle")
public object SubcomponentBinding1 : MiddleType

@ContributesMultibinding(SubScope::class)
@Named("middle")
public object SubcomponentBinding2 : MiddleType

@ContributesMultibinding(SubScope::class)
public object SubcomponentBinding3 : MiddleType
