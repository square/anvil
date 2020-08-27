package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.hintBinding
import com.squareup.anvil.compiler.hintBindingScope
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.junit.Test

class ContributesBindingGeneratorTest {

  @Test fun `there is a hint for a contributed binding for interfaces`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        interface ContributingInterface : ParentInterface
        """
    ) {
      assertThat(contributingInterface.hintBinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintBindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a hint for a contributed binding for classes`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        class ContributingInterface : ParentInterface
        """
    ) {
      assertThat(contributingInterface.hintBinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintBindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `the order of the scope can be changed with named parameters`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(boundType = ParentInterface::class, scope = Int::class)
        class ContributingInterface : ParentInterface
        """
    ) {
      assertThat(contributingInterface.hintBindingScope).isEqualTo(Int::class)
    }
  }

  @Test fun `there is a hint for a contributed binding for inner interfaces`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        class Abc {
          @ContributesBinding(Any::class, ParentInterface::class)
          interface ContributingInterface : ParentInterface
        }
        """
    ) {
      val contributingInterface =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingInterface")
      assertThat(contributingInterface.hintBinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintBindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a hint for a contributed binding for inner classes`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface
        
        class Abc {
          @ContributesBinding(Any::class, ParentInterface::class)
          class ContributingClass : ParentInterface
        }
        """
    ) {
      val contributingClass =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingClass")
      assertThat(contributingClass.hintBinding?.java).isEqualTo(contributingClass)
      assertThat(contributingClass.hintBindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `contributed binding class must be public`() {
    val visibilities = setOf(
        "internal", "private", "protected"
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        $visibility class ContributingInterface : ParentInterface
        """
      ) {
        assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
        // Position to the class.
        assertThat(messages).contains("Source.kt: (8, ")
        assertThat(messages).contains(
            "com.squareup.test.ContributingInterface is binding a type, but the class is not " +
                "public. Only public types are supported."
        )
      }
    }
  }
}
