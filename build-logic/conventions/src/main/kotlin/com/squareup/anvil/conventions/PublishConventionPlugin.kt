package com.squareup.anvil.conventions

import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.extras
import com.rickbusarow.kgx.getOrNullAs
import com.rickbusarow.kgx.mustRunAfter
import com.squareup.anvil.conventions.utils.gradlePublishingExtension
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.Platform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.internal.KaptTask
import javax.inject.Inject

open class PublishConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("publish", PublishExtension::class.java)

    target.plugins.apply("com.vanniktech.maven.publish.base")
    target.plugins.apply("org.jetbrains.dokka")

    target.tasks.named("dokkaHtml")
      .mustRunAfter(target.tasks.withType(KaptTask::class.java))

    val mavenPublishing = target.extensions
      .getByType(MavenPublishBaseExtension::class.java)

    val pluginPublishId = "com.gradle.plugin-publish"

    @Suppress("UnstableApiUsage")
    mavenPublishing.pomFromGradleProperties()
    mavenPublishing.signAllPublications()

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      when {
        target.plugins.hasPlugin(pluginPublishId) -> {
          // Gradle's 'plugin-publish' plugin creates its own publication.  We only apply this plugin
          // in order to get all the automated POM configuration.
        }

        else -> {
          configurePublication(
            target,
            mavenPublishing,
            KotlinJvm(javadocJar = Dokka("dokkaHtml"), sourcesJar = true),
          )
        }
      }
    }

    // We publish all artifacts to `anvil/build/m2` for the plugin integration tests.
    // The generated projects in tests use this repository to get the plugin
    // and the other published artifacts, so that the tests have a realistic classpath.
    target.gradlePublishingExtension.repositories { repositories ->
      repositories.maven {
        it.name = "buildM2"
        it.setUrl(target.rootProject.layout.buildDirectory.dir("m2"))
      }
    }

    setUpPublishToBuildM2(target)

    target.tasks.withType(AbstractDokkaTask::class.java).configureEach {
      val skipDokka = target.extras.getOrNullAs<Boolean>("skipDokka") ?: false
      it.enabled = !skipDokka
    }
  }

  /**
   * Registers this [target]'s version of the `publishToBuildM2` task
   * and adds it as a dependency to the root project's version.
   */
  private fun setUpPublishToBuildM2(target: Project) {

    val publishToBuildM2 = target.tasks.register(PUBLISH_TO_BUILD_M2) {
      it.group = "Publishing"
      it.description = "Delegates to the publishAllPublicationsToBuildM2Repository task " +
        "on projects where publishing is enabled."

      it.dependsOn("publishAllPublicationsToBuildM2Repository")

      // Don't generate javadoc for integration tests.
      target.extras["skipDokka"] = true
    }

    target.rootProject.tasks.named(PUBLISH_TO_BUILD_M2).dependsOn(publishToBuildM2)
  }

  @Suppress("UnstableApiUsage")
  private fun configurePublication(
    target: Project,
    mavenPublishing: MavenPublishBaseExtension,
    platform: Platform,
  ) {
    mavenPublishing.configure(platform)

    target.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
      it.mustRunAfter(target.tasks.withType(Jar::class.java))
    }
  }

  companion object {
    internal const val PUBLISH_TO_BUILD_M2 = "publishToBuildM2"
  }
}

open class PublishExtension @Inject constructor(
  private val target: Project,
) {
  fun configurePom(args: Map<String, Any>) {
    val artifactId = args.getValue("artifactId") as String
    val pomName = args.getValue("pomName") as String
    val pomDescription = args.getValue("pomDescription") as String

    target.gradlePublishingExtension
      .publications.withType(MavenPublication::class.java)
      .configureEach { publication ->

        // Gradle plugin publications have their own artifactID convention,
        // and that's handled automatically.
        if (!publication.name.endsWith("PluginMarkerMaven")) {
          publication.artifactId = artifactId
        }

        publication.pom {
          it.name.set(pomName)
          it.description.set(pomDescription)
        }
      }
  }
}
