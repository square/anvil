package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Companion.PRIORITY_HIGH

public interface PriorityBinding

@ContributesBinding(
  scope = AppScope::class,
  priority = PRIORITY_HIGH,
)
public object PriorityBindingHigh : PriorityBinding

@ContributesBinding(AppScope::class)
public object PriorityBindingNormal : PriorityBinding
