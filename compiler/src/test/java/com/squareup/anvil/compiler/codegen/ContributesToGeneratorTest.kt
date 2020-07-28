package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.hintContributes
import com.squareup.anvil.compiler.innerInterface
import com.squareup.anvil.compiler.innerModule
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import org.junit.Test

class ContributesToGeneratorTest {

  @Test fun `there is no hint for merge annotations`() {
    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.MergeComponent
        
        @MergeComponent(Any::class)
        interface ComponentInterface
    """
    ) {
      assertThat(componentInterface.hintContributes).isNull()
    }

    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.MergeSubcomponent
        
        @MergeSubcomponent(Any::class)
        interface ComponentInterface
    """
    ) {
      assertThat(componentInterface.hintContributes).isNull()
    }
  }

  @Test fun `there is a hint for contributed Dagger modules`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule1
    """
    ) {
      assertThat(daggerModule1.hintContributes?.java).isEqualTo(daggerModule1)
    }
  }

  @Test fun `there is a hint for contributed interfaces`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        interface ContributingInterface
    """
    ) {
      assertThat(contributingInterface.hintContributes?.java).isEqualTo(contributingInterface)
    }
  }

  @Test fun `there is a hint for contributed inner Dagger modules`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        interface ComponentInterface {
          @ContributesTo(Any::class)
          @dagger.Module
          abstract class InnerModule
        }
    """
    ) {
      assertThat(innerModule.hintContributes?.java).isEqualTo(innerModule)
    }
  }

  @Test fun `there is a hint for contributed inner interfaces`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        class SomeClass {
          @ContributesTo(Any::class)
          interface InnerInterface
        }
    """
    ) {
      assertThat(innerInterface.hintContributes?.java).isEqualTo(innerInterface)
    }
  }

  @Test fun `contributing module must be a Dagger Module`() {
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        abstract class DaggerModule1
    """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      // Position to the class.
      assertThat(messages).contains("Source.kt: (6, 16)")
    }
  }

  @Test fun `contributed modules must be public`() {
    val visibilities = setOf(
        "internal", "private", "protected"
    )

    visibilities.forEach { visibility ->
      compile(
          """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        @dagger.Module
        $visibility abstract class DaggerModule1
    """
      ) {
        assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
        // Position to the class.
        assertThat(messages).contains("Source.kt: (7, ")
      }
    }
  }

  @Test fun `contributed interfaces must be public`() {
    val visibilities = setOf(
        "internal", "private", "protected"
    )

    visibilities.forEach { visibility ->
      compile(
          """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo

        @ContributesTo(Any::class)
        $visibility interface ContributingInterface
    """
      ) {
        assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
        // Position to the class.
        assertThat(messages).contains("Source.kt: (6, ")
      }
    }
  }
}
