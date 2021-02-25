package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.hintMultibinding
import com.squareup.anvil.compiler.hintMultibindingScope
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.junit.Test

class ContributesMultibindingGeneratorTest {

  @Test fun `there is a hint for a contributed multibinding for interfaces`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : ParentInterface
      """
    ) {
      assertThat(contributingInterface.hintMultibinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a hint for a contributed multibinding for classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      class ContributingInterface : ParentInterface
      """
    ) {
      assertThat(contributingInterface.hintMultibinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a hint for a contributed multibinding for an object`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      object ContributingInterface : ParentInterface
      """
    ) {
      assertThat(contributingInterface.hintMultibinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `the order of the scope can be changed with named parameters`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(boundType = ParentInterface::class, scope = Int::class)
      class ContributingInterface : ParentInterface
      """
    ) {
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Int::class)
    }
  }

  @Test fun `there is a hint for a contributed multibinding for inner interfaces`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      class Abc {
        @ContributesMultibinding(Any::class, ParentInterface::class)
        interface ContributingInterface : ParentInterface
      }
      """
    ) {
      val contributingInterface =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingInterface")
      assertThat(contributingInterface.hintMultibinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a hint for a contributed multibinding for inner classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface
      
      class Abc {
        @ContributesMultibinding(Any::class, ParentInterface::class)
        class ContributingClass : ParentInterface
      }
      """
    ) {
      val contributingClass =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingClass")
      assertThat(contributingClass.hintMultibinding?.java).isEqualTo(contributingClass)
      assertThat(contributingClass.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `contributed multibinding class must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected"
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesMultibinding

        interface ParentInterface

        @ContributesMultibinding(Any::class, ParentInterface::class)
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

  @Test fun `contributed multibindings aren't allowed to have more than one qualifier`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import javax.inject.Qualifier
      
      @Qualifier
      annotation class AnyQualifier1
      
      @Qualifier
      annotation class AnyQualifier2

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @AnyQualifier1 
      @AnyQualifier2
      interface ContributingInterface : ParentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "Classes annotated with @ContributesMultibinding may not use more than one @Qualifier."
      )
    }
  }
}
