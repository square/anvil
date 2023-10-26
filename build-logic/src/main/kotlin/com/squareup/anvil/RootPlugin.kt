package com.squareup.anvil

import com.squareup.anvil.benchmark.BenchmarkPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

open class RootPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    target.plugins.apply(BenchmarkPlugin::class.java)
  }
}

open class LibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    // TODO
  }
}
