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

  @Test fun `an enum as annotation argument can be parsed`() {
    compile(
      """
      @file:Suppress("RemoveRedundantQualifierName")
      package com.squareup.test
 
      import com.squareup.test.AnyQualifier.Values 
      import com.squareup.test.AnyQualifier.Values.ABC 

      annotation class AnyQualifier(val abc: Values) {
        enum class Values {
          ABC
        }
      }

      @AnyQualifier(ABC)
      interface SomeClass1

      @AnyQualifier(Values.ABC)
      interface SomeClass2

      @AnyQualifier(AnyQualifier.Values.ABC)
      interface SomeClass3
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("AnyQualifier", "Values", "ABC"))
            return@simpleCodeGenerator null

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val argument = ref.annotations.single().arguments.single()

            assertThat(argument.value<FqName>())
              .isEqualTo(FqName("com.squareup.test.AnyQualifier.Values.ABC"))
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a string annotation argument can be parsed`() {
    compile(
      """
      package com.squareup.test
 
      annotation class BindingKey(val value: String)
      
      @BindingKey("1")
      interface SomeClass1
      
      const val CONSTANT = "1"

      @BindingKey(CONSTANT)
      interface SomeClass2
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey"))
            return@simpleCodeGenerator null

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val argument = ref.annotations.single().arguments.single()

            assertThat(argument.value<String>()).isEqualTo("1")

            assertThat(ref.annotations.single().toAnnotationSpec().toString())
              .isEqualTo("@com.squareup.test.BindingKey(value = \"1\")")
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `an int annotation argument can be parsed`() {
    compile(
      """
      package com.squareup.test
 
      annotation class BindingKey(val value: Int)
      
      @BindingKey(1)
      interface SomeClass1
      
      const val CONSTANT = 1

      @BindingKey(CONSTANT)
      interface SomeClass2
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey"))
            return@simpleCodeGenerator null

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val argument = ref.annotations.single().arguments.single()

            assertThat(argument.value<Int>()).isEqualTo(1)

            assertThat(ref.annotations.single().toAnnotationSpec().toString())
              .isEqualTo("@com.squareup.test.BindingKey(value = 1)")
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a complex annotation argument can be parsed`() {
    compile(
      """
      package com.squareup.test
      
      import kotlin.reflect.KClass
 
      annotation class BindingKey(
        val name: String,
        val implementingClass: KClass<*>,
        val thresholds: IntArray
      )
      
      @BindingKey("abc", Unit::class, [1, 2, 3])
      interface SomeClass1
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey"))
            return@simpleCodeGenerator null

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val arguments = ref.annotations.single().arguments
            assertThat(arguments[0].value<String>()).isEqualTo("abc")
            assertThat(arguments[1].value<ClassReference>().shortName).isEqualTo("Unit")
            assertThat(arguments[2].value<IntArray>()).asList()
              .containsExactly(1, 2, 3).inOrder()

            assertThat(ref.annotations.single().toAnnotationSpec().toString()).isEqualTo(
              "@com.squareup.test.BindingKey(name = \"abc\", " +
                "implementingClass = kotlin.Unit::class, thresholds = [1, 2, 3])"
            )
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
