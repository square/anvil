package com.squareup.anvil

import com.squareup.anvil.benchmark.BenchmarkPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin

open class RootPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BenchmarkPlugin::class.java)
    target.plugins.apply(KtlintConventionPlugin::class.java)
  }
}

open class LibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(KotlinPlatformJvmPlugin::class.java)
    target.plugins.apply(KtlintConventionPlugin::class.java)
  }
}
