package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.hintSubcomponent
import com.squareup.anvil.compiler.hintSubcomponentParentScope
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.subcomponentInterface
import org.junit.Test

class ContributesSubcomponentGeneratorTest {

  @Test fun `there is a hint for contributed subcomponents`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `there is a hint for contributed subcomponents - abstract class`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      abstract class SubcomponentInterface
      """
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `there is a hint for contributed subcomponents with a different parameter order`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(parentScope = Unit::class, scope = Any::class)
      interface SubcomponentInterface
      """
    ) {
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `there is a hint for contributed inner subcomponents`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      class Outer {
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
      }
      """
    ) {
      val subcomponentInterface = classLoader
        .loadClass("com.squareup.test.Outer\$SubcomponentInterface")
      assertThat(subcomponentInterface.hintSubcomponent?.java).isEqualTo(subcomponentInterface)
      assertThat(subcomponentInterface.hintSubcomponentParentScope).isEqualTo(Unit::class)
    }
  }

  @Test fun `contributed subcomponents must be a interfaces or abstract classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      class SubcomponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (6,")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface."
      )
    }

    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesSubcomponent

      @ContributesSubcomponent(Any::class, Unit::class)
      object SubcomponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      // Position to the class.
      assertThat(messages).contains("Source0.kt: (6,")
      assertThat(messages).contains(
        "com.squareup.test.SubcomponentInterface is annotated with @ContributesSubcomponent, " +
          "but this class is not an interface."
      )
    }
  }

  @Test fun `contributed subcomponents must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected"
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        $visibility interface SubcomponentInterface
        """
      ) {
        assertThat(exitCode).isError()
        // Position to the class.
        assertThat(messages).contains("Source0.kt: (6, ")
        assertThat(messages).contains(
          "com.squareup.test.SubcomponentInterface is contributed to the Dagger graph, but the " +
            "interface is not public. Only public interfaces are supported."
        )
      }
    }
  }
}
