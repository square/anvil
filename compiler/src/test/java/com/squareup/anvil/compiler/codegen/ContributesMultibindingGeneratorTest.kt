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
        assertThat(messages).contains("Source0.kt: (8, ")
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

  @Test fun `the bound type can only be implied with one super type (2 interfaces)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface, CharSequence
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation."
      )
    }
  }

  @Test fun `the bound type can only be implied with one super type (class and interface)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      open class Abc

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : Abc(), ParentInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation."
      )
    }
  }

  @Test fun `the bound type can only be implied with one super type (no super type)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      @ContributesMultibinding(Any::class)
      object ContributingInterface
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation."
      )
    }
  }

  @Test fun `the bound type is not implied when explicitly defined`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(
        scope = Int::class, 
        ignoreQualifier = true, 
        boundType = ParentInterface::class
      )
      interface ContributingInterface : ParentInterface, CharSequence
      """
    ) {
      assertThat(contributingInterface.hintMultibinding?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Int::class)
    }
  }

  @Test fun `the contributed multibinding class must extend the bound type`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : CharSequence
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding for " +
          "com.squareup.test.ParentInterface, but doesn't extend this type."
      )
    }
  }
}
