package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
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
}
