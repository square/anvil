package com.squareup.anvil.plugin.testing

// TODO (rbusarow) generate this automatically from the version catalog
class Libs(
  private val anvilVersion: String = com.squareup.anvil.plugin.buildProperties.anvilVersion,
  private val daggerVersion: String = com.squareup.anvil.plugin.buildProperties.daggerVersion,
) {

  val inject = "javax.inject:javax.inject:1"

  val anvil = Anvil()

  inner class Anvil {
    val compilerApi = "com.squareup.anvil:compiler-api:$anvilVersion"
    val compilerUtils = "com.squareup.anvil:compiler-utils:$anvilVersion"
    val compiler = "com.squareup.anvil:compiler:$anvilVersion"
    val annotations = "com.squareup.anvil.annotations:annotations:$anvilVersion"
  }

  val auto = Auto()
  inner class Auto {

    val service = Service()

    inner class Service {
      val annotations = "com.google.auto.service:auto-service-annotations:1.0-rc7"
      val processor = "com.google.auto.service:auto-service:1.0-rc7"
    }
  }

  val dagger2 = Dagger2()

  inner class Dagger2 {
    val annotations = "com.google.dagger:dagger:$daggerVersion"
    val compiler = "com.google.dagger:dagger-compiler:$daggerVersion"
  }
}
