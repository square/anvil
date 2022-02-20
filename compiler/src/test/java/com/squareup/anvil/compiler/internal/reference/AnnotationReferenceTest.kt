package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class AnnotationReferenceTest {
  @Test fun `annotation references with different arguments aren't equal`() {
    compile(
      """
      package com.squareup.test
 
      import com.squareup.anvil.annotations.ContributesTo

      @ContributesTo(Any::class)
      interface SomeInterface1

      @ContributesTo(Unit::class)
      interface SomeInterface2

      @ContributesTo(Unit::class)
      interface SomeInterface3
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val annotation1 = FqName("com.squareup.test.SomeInterface1")
            .toClassReference(psiRef.module)
            .annotations
            .single()

          val annotation2 = FqName("com.squareup.test.SomeInterface2")
            .toClassReference(psiRef.module)
            .annotations
            .single()

          val annotation3 = FqName("com.squareup.test.SomeInterface3")
            .toClassReference(psiRef.module)
            .annotations
            .single()

          assertThat(annotation1).isNotEqualTo(annotation2)
          assertThat(annotation2).isEqualTo(annotation3)

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
