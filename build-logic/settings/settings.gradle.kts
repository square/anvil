rootProject.name = "settings"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
  }
  versionCatalogs {
    create("libs") {
      from(files("../../gradle/libs.versions.toml"))
      System.getProperties().forEach { entry ->
        val key = entry.key as String
        if (key.startsWith("override_")) {
          val catalogKey = key.substring("override_".length)
          println("Overriding $catalogKey with ${entry.value}")
          version(catalogKey, entry.value as String)
        }
      }
    }
  }
}
