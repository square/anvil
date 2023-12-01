package com.squareup.anvil.conventions

import com.rickbusarow.kgx.pluginId
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Project

open class LibraryPlugin : BasePlugin() {
  override fun Project.jvmTargetInt(): Int = libs.versions.jvm.target.library.get().toInt()

  override fun beforeApply(target: Project) {
    target.plugins.apply(target.libs.plugins.kotlin.jvm.pluginId)

    target.plugins.apply(PublishConventionPlugin::class.java)
  }
}
