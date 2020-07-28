package com.squareup.anvil.plugin

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None

/** This signal is used to share state between the task above and Kotlin compile tasks. */
abstract class IncrementalSignal : BuildService<None> {
  val incremental = mutableMapOf<String, Boolean?>()
}
