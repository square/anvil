plugins {
  id("conventions.library")
  id("conventions.publish")
  alias(libs.plugins.doks)
}

conventions {
  explicitApi = true
}

doks {
  dokSet {
    docs("README.md")
    sampleCodeSource(
      kotlin.sourceSets.map { it.kotlin },
      project(":gradle-plugin").kotlin.sourceSets.map { it.kotlin },
    )

    rule("boogers") {
      regex = """(__.+__)"""
      replacement = "$1-boogers"
    }

    rule("whole-file") {

      replacement = sourceCode(
        fqName = "com.squareup.anvil.compiler.api.CustomCodeGeneratorSample",
        bodyOnly = false,
      )
    }
  }
}

publish {
  configurePom(
    artifactId = "compiler-api",
    pomName = "Anvil Compiler API",
    pomDescription = "API definitions for creating custom code generators that integrate with Anvil",
  )
}

dependencies {
  api(project(":annotations"))
  api(libs.kotlin.compiler)

  implementation(platform(libs.kotlin.bom))

  testCompileOnly(libs.auto.service.annotations)

  testImplementation(libs.junit)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
}
