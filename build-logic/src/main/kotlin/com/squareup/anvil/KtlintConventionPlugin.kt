package com.squareup.anvil

import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

open class KtlintConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(KtlintPlugin::class.java)

    target.extensions.configure(KtlintExtension::class.java) { ktlint ->

      ktlint.version.set(target.provider { target.libsCatalog.version("ktlint") })
      ktlint.verbose.set(true)
    }
  }
}
