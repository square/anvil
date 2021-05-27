package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeneratedCodeTest {

  @Test fun `the class is generated`() {
    val generatedClass = Class.forName("generated.test.com.squareup.anvil.test.GeneratedClass")
    assertThat(generatedClass).isNotNull()
  }
}
