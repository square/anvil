package com.squareup.anvil.conventions

import com.rickbusarow.kgx.property
import com.squareup.anvil.conventions.utils.addTasksToStartParameter
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("IdentifierGrammar")
open class ConventionsExtension @Inject constructor(
  private val target: Project,
  objects: ObjectFactory,
) {

  /**
   * If true, Kotlin's `warningsAsErrors` functionality is enabled full-time.
   */
  val warningsAsErrors: Property<Boolean> = objects.property(false)

  /**
   * If true, adds the `-Xexplicit-api=strict` compiler argument.
   */
  val explicitApi: Property<Boolean> = objects.property(false)

  /**
   * Any additional Kotlin compiler arguments, such as `-Xjvm-default=all`.
   */
  val kotlinCompilerArgs: ListProperty<String> = objects.listProperty(String::class.java)

  /**
   * Eagerly adds the given tasks to the IDE sync task. This is useful for tasks that generate code
   * that is needed by the IDE, such as the `generateBuildConfig` task.
   */
  fun addTasksToIdeSync(vararg taskNames: String) {
    target.addTasksToStartParameter(taskNames.toList())
  }
}
