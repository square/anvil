plugins {
  id("conventions.library")
  id("conventions.publish")
}

publish {
  configurePom(
    artifactId = "compiler-k2-utils",
    pomName = "Anvil Compiler Utils for K2",
    pomDescription = "Optional utility and extension functions for working with FIR and IR, " +
      "designed to simplify code generation tasks in Anvil",
  )
}

dependencies {

  api(libs.kotlin.compiler.embeddable)
  api(libs.kotlin.reflect)

  api(project(":annotations"))
  api(project(":compiler-k2-api"))

  testImplementation(project(":compiler-testing"))
}
