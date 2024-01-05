package com.squareup.anvil.plugin

import com.rickbusarow.kase.kase
import io.kotest.assertions.asClue
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.TestFactory

class AnvilExtensionTest : BaseGradleTest() {

  @TestFactory
  fun `gradle properties map to extension properties`() = listOf(
    kase(a1 = "generateDaggerFactories", false),
    kase(a1 = "generateDaggerFactoriesOnly", false),
    kase(a1 = "disableComponentMerging", false),
    kase(a1 = "syncGeneratedSources", false),
    kase(a1 = "addOptionalAnnotations", false),
  )
    .asContainers { (propertyName, default) ->
      testFactory { versions ->

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
        }

        "the extension property is $default when not set anywhere".asClue {

          shouldSucceed("printProperty") {
            output shouldContain "$propertyName: $default"
          }
        }

        val addFactoryGen = propertyName == "generateDaggerFactoriesOnly"

        rootProject { // The "factories only" toggle requires the "factories" toggle to be enabled.

          if (addFactoryGen) {
            gradlePropertiesFile(
              """
              com.squareup.anvil.generateDaggerFactories=true
              com.squareup.anvil.$propertyName=${!default}
              """.trimIndent(),
            )
          } else {
            gradlePropertiesFile(
              """
              com.squareup.anvil.$propertyName=${!default}
              """.trimIndent(),
            )
          }
        }

        "the extension property is ${!default} when only set in gradle.properties".asClue {

          shouldSucceed("printProperty") {
            output shouldContain "$propertyName: ${!default}"
          }
        }

        rootProject {
          gradlePropertiesFile(
            """
            com.squareup.anvil.$propertyName=$default
            """.trimIndent(),
          )
        }

        "the extension property is $default when set in gradle.properties".asClue {

          shouldSucceed("printProperty") {
            output shouldContain "$propertyName: $default"
          }
        }

        val extensionText = if (addFactoryGen) {
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

        rootProject.path
          .resolve(dslLanguage.buildFileName)
          .appendText("\n$extensionText")

        "setting a value in the extension property supersedes the value in gradle.properties".asClue {

          shouldSucceed("printProperty") {
            output shouldContain "$propertyName: ${!default}"
          }
        }
      }
    }
}
