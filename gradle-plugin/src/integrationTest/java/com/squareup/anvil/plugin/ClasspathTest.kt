package com.squareup.anvil.plugin

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class ClasspathTest : BaseAnvilGradleTest() {

  @TestFactory
  fun `annotations aren't in the runtime classpath`() = testFactory {
    rootBuild {
      plugins {
        kotlin("jvm", it.kotlinVersion)
        id("com.squareup.anvil", version = VERSION)
      }

      raw(
        """
        def printDependencies(configurationName) {
          println(
              configurations.getByName(configurationName).incoming.dependencies
                  .collect { "${'$'}{it.group}:${'$'}{it.name}" }
                  .sort()
                  .join("\n")
          )
        }
        tasks.register("printRuntimeClasspath") {
          doLast { printDependencies("runtimeClasspath") }
        }
        tasks.register("printCompileClasspath") {
          doLast { printDependencies("compileClasspath") }
        }
        """.trimIndent()
      )
    }

    shouldSucceed("printCompileClasspath", "printRuntimeClasspath") {
      val taskOutputs = output.split("> Task ", "\n\n")
        .associate {
          val lines = it.trim().lines()
          lines.first() to lines.drop(1).joinToString("\n").trim()
        }

      taskOutputs[":printCompileClasspath"] shouldBe """
        com.squareup.anvil:annotations
        org.jetbrains.kotlin:kotlin-stdlib-jdk8
      """.trimIndent()

      taskOutputs[":printRuntimeClasspath"] shouldBe """
        org.jetbrains.kotlin:kotlin-stdlib-jdk8
      """.trimIndent()
    }
  }
}
