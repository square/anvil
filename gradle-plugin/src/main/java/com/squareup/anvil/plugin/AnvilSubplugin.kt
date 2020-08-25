package com.squareup.anvil.plugin

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.io.File

@AutoService(KotlinGradleSubplugin::class)
@Suppress("unused")
class AnvilSubplugin : KotlinGradleSubplugin<AbstractCompile> {

  override fun isApplicable(
    project: Project,
    task: AbstractCompile
  ): Boolean = project.plugins.hasPlugin(AnvilPlugin::class.java)

  override fun getCompilerPluginId(): String = "com.squareup.anvil.compiler"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
      groupId = GROUP,
      artifactId = "compiler",
      version = VERSION
  )

  override fun apply(
    project: Project,
    kotlinCompile: AbstractCompile,
    javaCompile: AbstractCompile?,
    variantData: Any?,
    androidProjectHandler: Any?,
    kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
  ): List<SubpluginOption> {
    // Notice that we use the name of the Kotlin compile task as a directory name. Generated code
    // for this specific compile task will be included in the task output. The output of different
    // compile tasks shouldn't be mixed.
    val srcGenDir = File(project.buildDir, "anvil${File.separator}src-gen-${kotlinCompile.name}")
    val srcGenDirOption = SubpluginOption(
        key = "src-gen-dir",
        value = srcGenDir.absolutePath
    )

    project.afterEvaluate {
      // Notice that we pass the absolutePath to the Kotlin compiler plugin. That is necessary,
      // because the plugin has no understanding of Gradle or what the working directory is. The
      // Kotlin Gradle plugin adds all SubpluginOptions as input to the Gradle task. This is bad,
      // because now the hash of inputs is different on every machine. We override this input with
      // a relative path in order to preserve some safety and avoid the cache misses.
      kotlinCompile.inputs.property(
          "${getCompilerPluginId()}.${srcGenDirOption.key}",
          srcGenDir.relativeTo(project.buildDir).path
      )
    }

    return listOf(srcGenDirOption)
  }
}
