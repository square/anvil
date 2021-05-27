package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.sqareup.anvil.compiler.internal.testing.compile
import com.sqareup.anvil.compiler.internal.testing.createInstance
import org.junit.Test

class TestCodeGeneratorTest {

  @Test fun `code is generated`() {
    compile(
      """
      package com.squareup.test
      
      @com.squareup.anvil.test.Trigger
      class AnyTrigger
      """,
      """
      package com.squareup.anvil.test

      annotation class Trigger
      """
    ) {
      assertThat(
        classLoader.loadClass("generated.test.com.squareup.test.GeneratedClass").createInstance()
      ).isNotNull()
    }
  }
}
