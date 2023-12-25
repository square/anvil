plugins {
  id("conventions.library")
  id("conventions.publish")
}

publish {
  configurePom(
    artifactId = "annotations-optional",
    pomName = "Anvil Optional Annotations",
    pomDescription = "Optional annotations that we\"ve found to be helpful with managing larger dependency graphs",
  )
}

dependencies {
  api(libs.inject)
}
