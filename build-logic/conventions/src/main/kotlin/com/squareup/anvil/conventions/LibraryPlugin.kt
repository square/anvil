package com.squareup.anvil.conventions

import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import com.rickbusarow.kgx.pluginId
import com.rickbusarow.kgx.withJavaTestFixturesPlugin
import com.squareup.anvil.conventions.utils.isInAnvilRootBuild
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Project

open class LibraryPlugin : BasePlugin() {
  override fun Project.jvmTargetInt(): Int = libs.versions.jvm.target.library.get().toInt()

  override fun beforeApply(target: Project) {
    target.plugins.apply(target.libs.plugins.kotlin.jvm.pluginId)

    if (target.isInAnvilRootBuild()) {
      target.plugins.apply(target.libs.plugins.kotlinx.binaryCompatibility.pluginId)
      configureDependencyGuard(target)
    }

    target.extensions.getByType(ConventionsExtension::class.java)
      .explicitApi.set(true)
  }

  private fun configureDependencyGuard(target: Project) {
    target.plugins.apply(target.libs.plugins.dependencyGuard.pluginId)

    val dependencyGuard = target.extensions
      .getByType(DependencyGuardPluginExtension::class.java)

    dependencyGuard.configuration("runtimeClasspath")

    target.plugins.withJavaTestFixturesPlugin {
      dependencyGuard.configuration("testFixturesRuntimeClasspath")
    }
  }
}
