package com.squareup.hephaestus.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import java.io.File

// Workaround for https://youtrack.jetbrains.com/issue/KT-38570
open class DisableIncrementalCompilationTask : DefaultTask() {

  @get:Internal
  var didClassPathChange: Boolean = false
    private set

  @Suppress("unused")
  @get:InputFiles
  val pluginClasspath: FileCollection by lazy {
    project.configurations.getByName(PLUGIN_CLASSPATH_CONFIGURATION_NAME)
  }

  @Suppress("unused")
  @get:OutputFile @get:Optional
  val someFile = File(project.buildDir, "not-existing-file-because-gradle-needs-an-output")

  @TaskAction fun toggleFlag() {
    // Disable incremental compilation if something in the classpath changed. If nothing has changed
    // in the classpath, then this task wouldn't run at all and be skipped.
    didClassPathChange = true
  }
}
