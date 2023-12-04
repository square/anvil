package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGH

public interface PriorityBinding

@ContributesBinding(
  scope = AppScope::class,
  priority = HIGH,
)
public object PriorityBindingHigh : PriorityBinding

@ContributesBinding(AppScope::class)
public object PriorityBindingNormal : PriorityBinding
