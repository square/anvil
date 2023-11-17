/*
 * Copyright (C) 2023 Rick Busarow
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.anvil

import com.rickbusarow.kgx.applyOnce
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.javaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

abstract class IntegrationTestsConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.applyOnce("idea")

    val integrationTestSourceSet = target.javaExtension
      .sourceSets
      .register(INTEGRATION_TEST) { ss ->

        ss.compileClasspath += target.javaSourceSet("main")
          .output
          .plus(target.javaSourceSet("test").output)
          .plus(target.configurations.getByName("testRuntimeClasspath"))

        ss.runtimeClasspath += ss.output + ss.compileClasspath
      }

    target.configurations.register("${INTEGRATION_TEST}Compile") {
      it.extendsFrom(target.configurations.getByName("testCompileOnly"))
    }
    target.configurations.register("${INTEGRATION_TEST}Runtime") {
      it.extendsFrom(target.configurations.getByName("testRuntimeOnly"))
    }

    val integrationTestTask = target.tasks
      .register(INTEGRATION_TEST, Test::class.java) { task ->

        task.group = "verification"
        task.description = "tests the '$INTEGRATION_TEST' source set"

        task.useJUnitPlatform()

        val mainSourceSet = target.javaSourceSet("main")

        val ss = integrationTestSourceSet.get()

        task.testClassesDirs = ss.output.classesDirs
        task.classpath = ss.runtimeClasspath
        task.inputs.files(ss.output.classesDirs)
        task.inputs.files(mainSourceSet.allSource)
      }

    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(integrationTestTask)

    target.extensions.configure(KotlinJvmProjectExtension::class.java) { kotlin ->
      val compilations = kotlin.target.compilations

      compilations.getByName(INTEGRATION_TEST) {
        it.associateWith(compilations.getByName("main"))
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
