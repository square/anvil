package com.squareup.anvil.plugin

import com.rickbusarow.kase.Kase4
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleProjectBuilder
import com.rickbusarow.kase.gradle.HasGradleDependencyVersion
import com.rickbusarow.kase.gradle.HasKotlinDependencyVersion
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.rickbusarow.kase.kase
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment
import com.squareup.anvil.plugin.testing.BaseGradleTest
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class AnvilExtensionTest : BaseGradleTest() {

  @Nested
  inner class `gradle property to extension property mapping` {

    val properties = listOf(
      kase(a1 = "generateDaggerFactories", false),
      kase(a1 = "generateDaggerFactoriesOnly", false),
      kase(a1 = "disableComponentMerging", false),
      kase(a1 = "syncGeneratedSources", false),
      kase(a1 = "addOptionalAnnotations", false),
      kase(a1 = "trackSourceFiles", true),
    )

    fun GradleProjectBuilder.gradlePropertiesFile(propertyName: String, value: Any) {
      val props = buildList {
        if (propertyName == "generateDaggerFactoriesOnly") {
          // The "__FactoriesOnly" toggle requires the "__Factories" toggle to be enabled.
          add("com.squareup.anvil.generateDaggerFactories=true")
        }
        add("com.squareup.anvil.$propertyName=$value")
      }
      gradlePropertiesFile(props.joinToString("\n"))
    }

    @TestFactory
    fun `extension properties are their default value when not set anywhere`() =
      tests { versions, propertyName, default ->

        rootProject.buildFile(
          """
          plugins {
            kotlin("jvm") version "${versions.kotlinVersion}"
            id("com.squareup.anvil") version "$anvilVersion"
          }
          
          tasks.register("printProperty") {
            doLast {
              println("$propertyName: ${'$'}{anvil.$propertyName.get()}")
            }
          } 
          """.trimIndent(),
        )

        shouldSucceed("printProperty") {
          output shouldContain "$propertyName: $default"
        }
      }

    @TestFactory
    fun `the extension property is not default when only set in gradle properties`() =
      tests { versions, propertyName, default ->
        rootProject {
          buildFile(
            """
            plugins {
              kotlin("jvm") version "${versions.kotlinVersion}"
              id("com.squareup.anvil") version "$anvilVersion"
            }
            
            tasks.register("printProperty") {
              doLast {
                println("$propertyName: ${'$'}{anvil.$propertyName.get()}")
              }
            } 
            """.trimIndent(),
          )

          gradlePropertiesFile(propertyName, !default)
        }

        shouldSucceed("printProperty") {
          output shouldContain "$propertyName: ${!default}"
        }
      }

    @TestFactory
    fun `the extension property is default when explicitly set to it in gradle properties`() =
      tests { versions, propertyName, default ->
        rootProject {
          buildFile(
            """
            plugins {
              kotlin("jvm") version "${versions.kotlinVersion}"
              id("com.squareup.anvil") version "$anvilVersion"
            }
            
            tasks.register("printProperty") {
              doLast {
                println("$propertyName: ${'$'}{anvil.$propertyName.get()}")
              }
            } 
            """.trimIndent(),
          )

          gradlePropertiesFile(propertyName, default)
        }

        shouldSucceed("printProperty") {
          output shouldContain "$propertyName: $default"
        }
      }

    @TestFactory
    fun `the extension property overrides the gradle properties value when set`() =
      tests { versions, propertyName, default ->

        rootProject {
          val extensionText = if (propertyName == "generateDaggerFactoriesOnly") {
            """
            anvil {
              generateDaggerFactories = true
              $propertyName = ${!default}
            }
            """.trimIndent()
          } else {
            """
            anvil {
              $propertyName = ${!default}
            }
            """.trimIndent()
          }
          buildFile(
            """
            plugins {
              kotlin("jvm") version "${versions.kotlinVersion}"
              id("com.squareup.anvil") version "$anvilVersion"
            }
            
            tasks.register("printProperty") {
              doLast {
                println("$propertyName: ${'$'}{anvil.$propertyName.get()}")
              }
            }
             
            $extensionText
            """.trimIndent(),
          )

          gradlePropertiesFile(propertyName, default)
        }

        shouldSucceed("printProperty") {
          output shouldContain "$propertyName: ${!default}"
        }
      }

    inline fun tests(
      crossinline action: AnvilGradleTestEnvironment.(
        GradleKotlinTestVersions,
        String,
        Boolean,
      ) -> Unit,
    ): Stream<out DynamicNode> = params.asContainers { versions ->
      properties.map { (propertyName, default) ->
        PropertyKase(
          propertyName = propertyName,
          default = default,
          gradle = versions.gradle,
          kotlin = versions.kotlin,
        )
      }.asTests { kase -> action(versions, kase.propertyName, kase.default) }
    }
  }
}

@PublishedApi
internal class PropertyKase(
  val propertyName: String,
  val default: Boolean,
  override val gradle: GradleDependencyVersion,
  override val kotlin: KotlinDependencyVersion,
) : Kase4<GradleDependencyVersion, KotlinDependencyVersion, String, Boolean> by kase(
  displayName = "property: $propertyName | default: $default",
  a1 = gradle,
  a2 = kotlin,
  a3 = propertyName,
  a4 = default,
),
  GradleKotlinTestVersions,
  HasGradleDependencyVersion by HasGradleDependencyVersion(gradle),
  HasKotlinDependencyVersion by HasKotlinDependencyVersion(kotlin)
