package com.squareup.anvil.mpp

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.MergeComponent

interface DebugDependency

@ContributesBinding(AppScope::class)
object DebugDependencyImpl : DebugDependency

@MergeComponent(AppScope::class)
interface DebugComponent {
  fun debugDependency(): DebugDependency
}
