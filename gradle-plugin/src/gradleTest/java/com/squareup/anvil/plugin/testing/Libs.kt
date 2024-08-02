package com.squareup.anvil.plugin.testing

// TODO (rbusarow) generate this automatically from the version catalog
class Libs(
  private val anvilVersion: String = com.squareup.anvil.plugin.buildProperties.anvilVersion,
  private val daggerVersion: String = com.squareup.anvil.plugin.buildProperties.daggerVersion,
) {

  val inject = "javax.inject:javax.inject:1"

  val anvil = Anvil()

  inner class Anvil {
    val compilerApi = "dev.zacsweers.anvil:compiler-api:$anvilVersion"
    val compilerUtils = "dev.zacsweers.anvil:compiler-utils:$anvilVersion"
    val compiler = "dev.zacsweers.anvil:compiler:$anvilVersion"
    val annotations = "com.squareup.anvil.annotations:annotations:$anvilVersion"
  }

  val dagger2 = Dagger2()

  inner class Dagger2 {
    val annotations = "com.google.dagger:dagger:$daggerVersion"
    val compiler = "com.google.dagger:dagger-compiler:$daggerVersion"
  }
}
