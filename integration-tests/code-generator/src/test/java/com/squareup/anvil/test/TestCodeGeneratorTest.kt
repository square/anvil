package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.createInstance
import org.junit.Test

@ExperimentalAnvilApi
class TestCodeGeneratorTest {

  @Test fun `code is generated`() {
    compileAnvil(
      """
      package com.squareup.test
      
      @com.squareup.anvil.test.Trigger
      class AnyTrigger
      """,
      """
      package com.squareup.anvil.test

      annotation class Trigger
      """,
    ) {
      assertThat(
        classLoader.loadClass("generated.test.com.squareup.test.GeneratedClass").createInstance(),
      ).isNotNull()
    }
  }
}
