package com.squareup.anvil.test

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Companion.RANK_HIGH

public interface RankBinding

@ContributesBinding(
  scope = AppScope::class,
  rank = RANK_HIGH,
)
public object RankBindingHigh : RankBinding

@ContributesBinding(AppScope::class)
public object RankBindingNormal : RankBinding
