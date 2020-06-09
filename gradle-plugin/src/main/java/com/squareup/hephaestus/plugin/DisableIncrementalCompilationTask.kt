package com.squareup.hephaestus.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

// Workaround for https://youtrack.jetbrains.com/issue/KT-38570
open class DisableIncrementalCompilationTask : DefaultTask() {

  @get:Internal lateinit var compileTask: KotlinCompile
  @get:Internal var configurationName: String? = null
    set(value) {
      field = value
      if (value != null) {
        // Initialize the value at config time.
        classpath = project
            .configurations
            .getByName(value)
            .incoming
            .artifacts("jar")
            .files
      }
    }

  @get:InputFiles lateinit var classpath: FileCollection
    private set

  @Suppress("unused")
  @get:OutputFile @get:Optional
  val someFile = File(project.buildDir, "not-existing-file-because-gradle-needs-an-output")

  @TaskAction fun toggleFlag() {
    // Disable incremental compilation if something in the classpath changed. If nothing has changed
    // in the classpath, then this task wouldn't run at all and be skipped.
    compileTask.incremental = false
  }
}

private val ARTIFACT_TYPE: Attribute<String> = Attribute.of("artifactType", String::class.java)

/** Returns a list of artifacts matching any of the given [artifactTypes]. */
@Suppress("SameParameterValue")
private fun ResolvableDependencies.artifacts(vararg artifactTypes: String): ArtifactView {
  return artifactView { view ->
    view.attributes { attributeContainer ->
      artifactTypes.forEach { artifactType ->
        attributeContainer.attribute(ARTIFACT_TYPE, artifactType)
      }
    }
  }
}
