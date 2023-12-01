package com.squareup.anvil.plugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class HelloWorldIntegrationTest {

  @Test
  fun `integration tests are a thing`() {
    val projectDir = createTempDirectory().toFile()

    val result = GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments("help")
      .withPluginClasspath()
      .build()

    assertThat(result.output)
      .contains("BUILD SUCCESSFUL")
  }
}
