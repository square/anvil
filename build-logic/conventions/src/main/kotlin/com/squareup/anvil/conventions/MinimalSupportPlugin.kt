package com.squareup.anvil.conventions

import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Project

open class MinimalSupportPlugin : BasePlugin() {
  override fun Project.jvmTargetInt(): Int = libs.versions.jvm.target.minimal.get().toInt()
}
