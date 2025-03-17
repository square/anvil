plugins {
  id("conventions.library")
  id("conventions.publish")
}

conventions {
  explicitApi = true
}

publish {
  configurePom(
    artifactId = "compiler-k2-api",
    pomName = "Anvil Compiler API for Kotlin 2+",
    pomDescription = "API definitions for creating custom code generators that integrate with Anvil",
  )
}

dependencies {
  compileOnly(libs.auto.service.annotations)
  api(libs.kotlin.compiler)

  compileOnly(project(":annotations"))

  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
}
