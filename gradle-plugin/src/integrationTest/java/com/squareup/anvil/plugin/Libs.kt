package com.squareup.anvil.plugin

class Libs(
  private val autoServiceVersion: String = BuildPropertiesIntegrationTest.autoServiceVersion,
  private val daggerVersion: String = BuildPropertiesIntegrationTest.daggerVersion
) {

  val inject = "javax.inject:javax.inject:1"

  val anvil = Anvil()

  inner class Anvil {
    val compilerApi = "com.squareup.anvil:compiler-api:$VERSION"
    val compilerUtils = "com.squareup.anvil:compiler-utils:$VERSION"
    val compiler = "com.squareup.anvil:compiler:$VERSION"
    val annotations = "com.squareup.anvil.annotations:annotations:$VERSION"
  }

  val auto: Auto = Auto()

  inner class Auto {
    val service = Service()

    inner class Service {
      val annotations = "com.google.auto.service:auto-service-annotations:$autoServiceVersion"
      val service = "com.google.auto.service:auto-service:1.0-rc7"
    }

    val value = Value()

    inner class Value {
      val annotations = "com.google.auto.value:auto-value-annotations:1.6.5"
      val processor = "com.google.auto.value:auto-value:1.6.5"
    }
  }

  val dagger2 = Dagger2()

  inner class Dagger2 {
    val annotations = "com.google.dagger:dagger:$daggerVersion"
    val compiler = "com.google.dagger:dagger-compiler:$daggerVersion"
  }
}
