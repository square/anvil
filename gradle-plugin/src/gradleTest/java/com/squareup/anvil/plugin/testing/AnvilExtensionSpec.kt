package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.dsl.model.AbstractDslElementContainer
import com.rickbusarow.kase.gradle.dsl.model.LambdaParameter
import com.rickbusarow.kase.gradle.dsl.model.gradlePropertyReference

/** Adds an [AnvilExtensionSpec] to a [BuildFileSpec]. */
fun BuildFileSpec.anvil(
  block: AnvilExtensionSpec.() -> Unit,
): BuildFileSpec = functionCall("anvil", LambdaParameter(builder = block))

/**
 * Defines the `anvil` extension as a Kase DSL specs.
 *
 * usage:
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   rootProject {
 *     buildFile {
 *       plugins {
 *         // ...
 *       }
 *
 *       anvil {
 *         generateDaggerFactories.set(true)
 *         generateDaggerFactoriesOnly.set(true)
 *         syncGeneratedSources.set(true)
 *       }
 *     }
 *   }
 * }
 * ```
 */
class AnvilExtensionSpec : AbstractDslElementContainer<AnvilExtensionSpec>() {

  val generateDaggerFactories by gradlePropertyReference()
  val generateDaggerFactoriesOnly by gradlePropertyReference()
  val disableComponentMerging by gradlePropertyReference()
  val syncGeneratedSources by gradlePropertyReference()
  val addOptionalAnnotations by gradlePropertyReference()
  val trackSourceFiles by gradlePropertyReference()
}
