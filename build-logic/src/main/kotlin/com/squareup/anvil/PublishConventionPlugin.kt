package com.squareup.anvil

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.Platform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Jar
import javax.inject.Inject

open class PublishConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    target.plugins.apply("com.vanniktech.maven.publish.base")
    target.plugins.apply("org.jetbrains.dokka")

    target.extensions.create("publish", PublishExtension::class.java)

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
            KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaHtml"), sourcesJar = true)
          )
        }
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun configurePublication(
    target: Project,
    mavenPublishing: MavenPublishBaseExtension,
    platform: Platform
  ) {
    mavenPublishing.configure(platform)

    target.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
      it.mustRunAfter(target.tasks.withType(Jar::class.java))
      it.mustRunAfter("dokkaJavadocJar")
      it.mustRunAfter("kotlinSourcesJar")
    }
  }
}

open class PublishExtension @Inject constructor(
  private val target: Project,
) {
  fun configurePom(args: Map<String, Any>) {

    val artifactId = args.getValue("artifactId") as String
    val pomName = args.getValue("pomName") as String
    val pomDescription = args.getValue("pomDescription") as String

    target.extensions
      .getByType(PublishingExtension::class.java)
      .publications.withType(MavenPublication::class.java)
      .matching { !it.name.endsWith("PluginMarkerMaven") }
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
