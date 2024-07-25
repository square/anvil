package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.api.ComponentMergingBackend
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.ComponentProcessingMode
import com.tschuchort.compiletesting.KotlinCompilation
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.reflect.KVisibility

class KspContributionMergerTest {

  @Test fun `creator-less components still generate a shim`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      
      @MergeComponent(Any::class)
      interface ComponentInterface

      fun example() {
        // It should be able to call this generated shim now even without a creator
        DaggerComponentInterface.create()
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      expectExitCode = KotlinCompilation.ExitCode.OK,
    )
  }

  @Test fun `merged component visibility is inherited from annotated class`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeComponent
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @MergeComponent(Any::class)
      internal interface ComponentInterface
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
    ) {
      val visibility = componentInterface.kotlin.visibility
      assertThat(visibility).isEqualTo(KVisibility.INTERNAL)
    }
  }

  @Test fun `typealiases are followed`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      import dagger.BindsInstance

      typealias CustomMergeComponent = MergeComponent
      typealias CustomMergeComponentFactory = MergeComponent.Factory
      typealias CustomBindsInstance = BindsInstance
      
      @CustomMergeComponent(Any::class)
      interface ComponentInterface {
        fun value(): Int
        
        @CustomMergeComponentFactory
        interface Factory {
          fun create(@CustomBindsInstance value: Int): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.KSP,
      expectExitCode = KotlinCompilation.ExitCode.OK,
    ) {
      assertThat(
        componentInterface.canonicalName,
      ).isEqualTo("com.squareup.test.MergedComponentInterface")
      // Ensure we saw and generated the factory creator too
      assertThat(
        classLoader.loadClass("${componentInterface.canonicalName}\$Factory").canonicalName,
      )
        .isEqualTo("com.squareup.test.MergedComponentInterface.Factory")
    }
  }

  @Test fun `error type annotations are ignored`() {
    assumeTrue(includeKspTests())
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      import fake.DoesNotExist

      @DoesNotExist
      @MergeComponent(Any::class)
      interface ComponentInterface {
        @DoesNotExist
        @MergeComponent.Factory
        interface Factory {
          fun create(): ComponentInterface
        }
      }
      """,
      componentProcessingMode = ComponentProcessingMode.NONE,
      componentMergingBackend = ComponentMergingBackend.KSP,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      // Assert that we generated a merged component
      val mergedComponent = walkGeneratedFiles(AnvilCompilationMode.Ksp())
        .single { it.name == "MergedComponentInterface.kt" }
      assertThat(mergedComponent).isNotNull()
      assertThat(mergedComponent.readText()).doesNotContain("DoesNotExist")
    }
  }
}
