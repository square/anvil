package com.squareup.anvil

import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.pluginId
import com.squareup.anvil.benchmark.BenchmarkPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

open class RootPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BenchmarkPlugin::class.java)
    target.plugins.apply(KtlintConventionPlugin::class.java)
  }
}

open class LibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(target.libsCatalog.pluginId("kotlin-jvm"))
    target.plugins.apply(KtlintConventionPlugin::class.java)
  }
}
