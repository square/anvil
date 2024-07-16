package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.ComponentProcessingMode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import org.junit.Assume.assumeTrue
import org.junit.Test

class KspMergeAnnotationsCheckSymbolProcessorTest {
  @Test fun `dagger creator annotations - component factory`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import dagger.Component
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface {
        @Component.Factory
        interface Factory {
          fun create(): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "When using @MergeComponent, you must use @MergeComponent.Factory instead " +
          "of Dagger's own 'dagger.Component.Factory' annotation. The Dagger " +
          "annotation will be generated in the final merged component.",
      )
    }
  }

  @Test fun `dagger creator annotations - component builder`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import dagger.Component
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface {
        @Component.Builder
        interface Builder {
          fun build(): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "When using @MergeComponent, you must use @MergeComponent.Builder instead " +
          "of Dagger's own 'dagger.Component.Builder' annotation. The Dagger " +
          "annotation will be generated in the final merged component.",
      )
    }
  }

  @Test fun `dagger creator annotations - subcomponent factory`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeSubcomponent(Any::class)
      interface SubcomponentInterface {
        @Subcomponent.Factory
        interface Factory {
          fun create(): SubcomponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "When using @MergeSubcomponent, you must use @MergeSubcomponent.Factory instead " +
          "of Dagger's own 'dagger.Subcomponent.Factory' annotation. The Dagger " +
          "annotation will be generated in the final merged component.",
      )
    }
  }

  @Test fun `dagger creator annotations - subcomponent builder`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import dagger.Subcomponent
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeSubcomponent(Any::class)
      interface SubcomponentInterface {
        @Subcomponent.Builder
        interface Builder {
          fun build(): SubcomponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "When using @MergeSubcomponent, you must use @MergeSubcomponent.Builder instead " +
          "of Dagger's own 'dagger.Subcomponent.Builder' annotation. The Dagger " +
          "annotation will be generated in the final merged component.",
      )
    }
  }

  @Test fun `dagger creator annotations - mergecomponent factory`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface {
        @MergeComponent.Factory
        interface Factory {
          fun create(): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.OK,
    )
  }

  @Test fun `dagger creator annotations - mergecomponent builder`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface {
        @MergeComponent.Builder
        interface Builder {
          fun build(): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.OK,
    )
  }

  @Test fun `dagger creator annotations - mergesubcomponent factory`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeSubcomponent(Any::class)
      interface SubcomponentInterface {
        @MergeSubcomponent.Factory
        interface Factory {
          fun create(): SubcomponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.OK,
    )
  }

  @Test fun `dagger creator annotations - mergesubcomponent builder`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeSubcomponent(Any::class)
      interface SubcomponentInterface {
        @MergeSubcomponent.Builder
        interface Builder {
          fun build(): SubcomponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      componentMergingBackend = ComponentMergingBackend.KSP,
      mode = AnvilCompilationMode.Ksp(),
      expectExitCode = ExitCode.OK,
    )
  }
}
