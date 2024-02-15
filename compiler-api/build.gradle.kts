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

    rule("anvil-version") {
      regex = SEMVER
      replacement = project.version.toString()
    }

    rule("anvil-plugin") {
      regex = gradlePluginWithVersion("com.squareup.anvil")
      replacement = "$1$2$3$4${project.version}$6"
    }

    rule("whole-CodeGenerator") {

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

  testImplementation(project(":compiler-utils"))
}
