@file:Suppress("UnstableApiUsage")

package com.squareup.hephaestus.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

// Workaround for https://youtrack.jetbrains.com/issue/KT-38570
abstract class DisableIncrementalCompilationTask : DefaultTask() {

  @Suppress("unused")
  @get:Classpath
  @get:InputFiles
  abstract val pluginClasspath: ConfigurableFileCollection

  @Suppress("unused")
  @get:OutputFile @get:Optional
  val someFile = File(project.buildDir, "not-existing-file-because-gradle-needs-an-output")

  @get:Internal
  abstract val incrementalSignal: Property<IncrementalSignal>

  private val projectPath = project.path

  @TaskAction fun toggleFlag() {
    // Disable incremental compilation if something in the classpath changed. If nothing has changed
    // in the classpath, then this task wouldn't run at all and be skipped.
    incrementalSignal.get().incremental[projectPath] = false
  }
}
