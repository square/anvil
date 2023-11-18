package com.squareup.anvil.conventions

import com.rickbusarow.kgx.applyOnce
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.javaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

abstract class IntegrationTestsConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.applyOnce("idea")

    val integrationTestSourceSet = target.javaExtension
      .sourceSets
      .register(INTEGRATION_TEST) { ss ->

        val main = target.javaSourceSet(SourceSet.MAIN_SOURCE_SET_NAME)

        ss.compileClasspath += main.output
        ss.runtimeClasspath += main.output

        listOf(
          ss.implementationConfigurationName to main.implementationConfigurationName,
          ss.runtimeOnlyConfigurationName to main.runtimeOnlyConfigurationName,
        ).forEach { (integrationConfig, mainConfig) ->

          target.configurations.named(integrationConfig) {
            it.extendsFrom(target.configurations.getByName(mainConfig))
          }
        }
      }

    // The `compileOnlyApi` configuration is added by the `java-library` plugin,
    // which is applied by the kotlin-jvm plugin.
    target.pluginManager.withPlugin("java-library") {
      val ss = integrationTestSourceSet.get()

      val main = target.javaSourceSet(SourceSet.MAIN_SOURCE_SET_NAME)
      target.configurations.getByName(ss.compileOnlyConfigurationName)
        .extendsFrom(target.configurations.getByName(main.compileOnlyApiConfigurationName))
    }

    val integrationTestTask = target.tasks
      .register(INTEGRATION_TEST, Test::class.java) { task ->

        task.group = "verification"
        task.description = "tests the '$INTEGRATION_TEST' source set"

        task.useJUnitPlatform()

        val javaSourceSet = target.javaSourceSet(INTEGRATION_TEST)

        task.testClassesDirs = javaSourceSet.output.classesDirs
        task.classpath = javaSourceSet.runtimeClasspath
        task.inputs.files(javaSourceSet.allSource)
      }

    // Make `check` depend upon `integrationTest`
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(integrationTestTask)

    // Make the IDE treat `src/integrationTest/[java|kotlin]` as a test source directory.
    target.extensions.configure(IdeaModel::class.java) { idea ->
      idea.module { module ->
        module.testSources.from(
          integrationTestSourceSet.map { it.allSource.srcDirs },
        )
      }
    }
  }

  private fun Project.javaSourceSet(name: String): SourceSet {
    return javaExtension.sourceSets.getByName(name)
  }

  companion object {
    private const val INTEGRATION_TEST = "integrationTest"
  }
}
