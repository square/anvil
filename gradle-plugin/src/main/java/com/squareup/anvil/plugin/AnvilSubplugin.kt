package com.squareup.anvil.plugin

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
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
    val extension = project.extensions.findByType(AnvilExtension::class.java) ?: AnvilExtension()

    return listOf(
        FilesSubpluginOption(
            key = "src-gen-dir",
            files = listOf(srcGenDir)
        ),
        SubpluginOption(
            key = "generate-dagger-factories",
            value = extension.generateDaggerFactories.toString()
        )
    )
  }
}
