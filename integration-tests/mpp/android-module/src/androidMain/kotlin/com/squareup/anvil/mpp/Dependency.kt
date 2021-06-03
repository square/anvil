package com.squareup.anvil.mpp

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.MergeComponent

interface Dependency

@ContributesBinding(AppScope::class)
object DependencyImpl : Dependency

@MergeComponent(AppScope::class)
interface Component {
  fun dependency(): Dependency
}
