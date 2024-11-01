package com.squareup.anvil.conventions

import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.extras
import com.rickbusarow.kgx.getOrNullAs
import com.rickbusarow.kgx.mustRunAfter
import com.rickbusarow.kgx.pluginId
import com.squareup.anvil.conventions.utils.gradlePublishingExtension
import com.squareup.anvil.conventions.utils.libs
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.internal.KaptTask
import javax.inject.Inject

open class PublishConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply("com.vanniktech.maven.publish.base")
    target.plugins.apply("org.jetbrains.dokka")

    target.tasks.named(DOKKA_HTML)
      .mustRunAfter(target.tasks.withType(KaptTask::class.java))

    val mavenPublishing = target.extensions
      .getByType(MavenPublishBaseExtension::class.java)

    target.extensions.create("publish", PublishExtension::class.java, mavenPublishing)

    val pluginPublishId = target.libs.plugins.gradlePublish.pluginId

    @Suppress("UnstableApiUsage")
    mavenPublishing.pomFromGradleProperties()
    mavenPublishing.signAllPublications()
    mavenPublishing.publishToMavenCentral(automaticRelease = true)

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      when {
        target.plugins.hasPlugin(pluginPublishId) -> {
          // Gradle's 'plugin-publish' plugin creates its own publication.  We only apply this plugin
          // in order to get all the automated POM configuration.
        }

        else -> {
          @Suppress("UnstableApiUsage")
          mavenPublishing.configure(
            platform = KotlinJvm(
              javadocJar = Dokka(DOKKA_HTML),
              sourcesJar = true,
            ),
          )
          target.plugins.withId(pluginPublishId) {
            error(
              "The '$pluginPublishId' plugin must be applied before the publishing " +
                "convention plugin, so that plugin publishing isn't configured twice.",
            )
          }
        }
      }
    }

    target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
      mavenPublishing.configure(
        platform = KotlinMultiplatform(
          javadocJar = Dokka(DOKKA_HTML),
          sourcesJar = true,
        ),
      )
      target.plugins.withId(pluginPublishId) {
        error(
          "The '$pluginPublishId' plugin must be applied before the publishing " +
            "convention plugin, so that plugin publishing isn't configured twice.",
        )
      }
    }

    // Fixes issues like:
    // Task 'generateMetadataFileForMavenPublication' uses this output of task 'dokkaJavadocJar'
    // without declaring an explicit or implicit dependency.
    target.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
      it.mustRunAfter(target.tasks.withType(Jar::class.java))
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

  companion object {
    internal const val DOKKA_HTML = "dokkaHtml"
    internal const val PUBLISH_TO_BUILD_M2 = "publishToBuildM2"
  }
}

open class PublishExtension @Inject constructor(
  private val extension: MavenPublishBaseExtension,
) {

  fun configurePom(
    artifactId: String,
    pomName: String,
    pomDescription: String,
  ) {
    extension.coordinates(artifactId = artifactId)
    extension.pom {
      it.name.set(pomName)
      it.description.set(pomDescription)
    }
  }
}
