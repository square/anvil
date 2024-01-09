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
        },
      ),
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
          if (psiRef.shortName in listOf("AnyQualifier", "Values", "ABC")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val argument = ref.annotations.single().arguments.single()

            assertThat(argument.value<FqName>())
              .isEqualTo(FqName("com.squareup.test.AnyQualifier.Values.ABC"))
          }

          null
        },
      ),
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
          if (psiRef.shortName in listOf("BindingKey")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val argument = ref.annotations.single().arguments.single()

            assertThat(argument.value<String>()).isEqualTo("1")

            assertThat(ref.annotations.single().toAnnotationSpec().toString())
              .isEqualTo("@com.squareup.test.BindingKey(value = \"1\")")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `an int annotation argument can be parsed`() {
    @Suppress("RemoveRedundantQualifierName")
    compile(
      """
      package com.squareup.test
 
      import com.squareup.test2.CONSTANT_2
      import com.squareup.test2.Abc
      import com.squareup.test2.Abc.Companion.CONSTANT_4
      import com.squareup.test2.SomeObject
      import com.squareup.test2.SomeObject.CONSTANT_3
      import com.squareup.test3.*
      import kotlin.Int.Companion.MAX_VALUE
 
      annotation class BindingKey(val value: Int)
      
      @BindingKey(1)
      interface SomeClass1
      
      const val CONSTANT = 1

      @BindingKey(CONSTANT)
      interface SomeClass2
      
      @BindingKey(-5)
      interface SomeClass3
      
      @BindingKey(MAX_VALUE)
      interface SomeClass4
      
      @BindingKey(Int.MAX_VALUE)
      interface SomeClass5
      
      @BindingKey(kotlin.Int.MAX_VALUE)
      interface SomeClass6
      
      @BindingKey(CONSTANT_2)
      interface SomeClass7
      
      @BindingKey(com.squareup.test2.CONSTANT_2)
      interface SomeClass8
      
      @BindingKey(CONSTANT_3)
      interface SomeClass9
      
      @BindingKey(SomeObject.CONSTANT_3)
      interface SomeClass10
      
      @BindingKey(com.squareup.test2.SomeObject.CONSTANT_3)
      interface SomeClass11
      
      @BindingKey(CONSTANT_4)
      interface SomeClass12
      
      @BindingKey(Abc.CONSTANT_4)
      interface SomeClass13
      
      @BindingKey(com.squareup.test2.Abc.CONSTANT_4)
      interface SomeClass14

      @BindingKey(CONSTANT_5)
      interface SomeClass15
      """,
      """
      package com.squareup.test2
        
      const val CONSTANT_2 = 2
      
      object SomeObject {
        const val CONSTANT_3 = 3
      }
      
      class Abc {
        companion object {
          const val CONSTANT_4 = 4
        }
      }
      """,
      """
      package com.squareup.test3
        
      const val CONSTANT_5 = 5
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey", "SomeObject", "Abc", "Companion")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            when (psiRef.shortName) {
              "SomeClass1" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(1)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 1)")
              }
              "SomeClass2" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(1)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 1)")
              }
              "SomeClass3" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(-5)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = -5)")
              }
              "SomeClass4", "SomeClass5", "SomeClass6" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(Int.MAX_VALUE)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 2147483647)")
              }
              "SomeClass7", "SomeClass8" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(2)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 2)")
              }
              "SomeClass9", "SomeClass10", "SomeClass11" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(3)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 3)")
              }
              "SomeClass12", "SomeClass13", "SomeClass14" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(4)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 4)")
              }
              "SomeClass15" -> {
                val argument = ref.annotations.single().arguments.single()
                assertThat(argument.value<Int>()).isEqualTo(5)

                assertThat(ref.annotations.single().toAnnotationSpec().toString())
                  .isEqualTo("@com.squareup.test.BindingKey(value = 5)")
              }
              else -> throw NotImplementedError(psiRef.shortName)
            }
          }

          null
        },
      ),
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
          if (psiRef.shortName in listOf("BindingKey")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val arguments = ref.annotations.single().arguments
            assertThat(arguments[0].value<String>()).isEqualTo("abc")
            assertThat(arguments[1].value<ClassReference>().shortName).isEqualTo("Unit")
            assertThat(arguments[2].value<IntArray>()).asList()
              .containsExactly(1, 2, 3).inOrder()

            assertThat(ref.annotations.single().toAnnotationSpec().toString()).isEqualTo(
              "@com.squareup.test.BindingKey(name = \"abc\", " +
                "implementingClass = kotlin.Unit::class, thresholds = [1, 2, 3])",
            )
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a string template annotation argument can be parsed`() {
    compile(
      """
      package com.squareup.test

      import kotlin.reflect.KClass
 
      annotation class BindingKey(val name: String)

      const val WORLD: String = "World!"

      @BindingKey("Hello, ${'$'}WORLD")
      interface SomeClass1
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val arguments = ref.annotations.single().arguments
            assertThat(arguments[0].value<String>()).isEqualTo("Hello, World!")

            assertThat(ref.annotations.single().toAnnotationSpec().toString()).isEqualTo(
              "@com.squareup.test.BindingKey(name = \"Hello, World!\")",
            )
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a string template annotation argument with no literals can be parsed`() {
    compile(
      """
      package com.squareup.test

      import kotlin.reflect.KClass
 
      annotation class BindingKey(val name: String)

      const val WORLD: String = "World!"

      @BindingKey("${'$'}WORLD")
      interface SomeClass1
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val arguments = ref.annotations.single().arguments
            assertThat(arguments[0].value<String>()).isEqualTo("World!")

            assertThat(ref.annotations.single().toAnnotationSpec().toString()).isEqualTo(
              "@com.squareup.test.BindingKey(name = \"World!\")",
            )
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a string template annotation argument with dot-qualified constants can be parsed`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.test2.Abc
      import com.squareup.test2.SomeObject
      import kotlin.reflect.KClass
 
      annotation class BindingKey(val name: String)

      @BindingKey("${'$'}{SomeObject.HELLO_NESTED} ${'$'}{Abc.WORLD_NESTED}")
      interface SomeClass1
      """,
      """
      package com.squareup.test2
      
      object SomeObject {
        const val HELLO_NESTED = "Hello nested,"
      }
      
      class Abc {
        companion object {
          const val WORLD_NESTED = "World nested!"
        }
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName in listOf("BindingKey", "SomeObject", "Abc", "Companion")) {
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            val arguments = ref.annotations.single().arguments
            assertThat(arguments[0].value<String>()).isEqualTo("Hello nested, World nested!")

            assertThat(ref.annotations.single().toAnnotationSpec().toString()).isEqualTo(
              "@com.squareup.test.BindingKey(name = \"Hello nested, World nested!\")",
            )
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `an annotation may have a dollar sign and be wrapped in backticks`() {
    compile(
      """
      package com.squareup.test
      
      @DslMarker
      annotation class `Fancy${'$'}DslMarker`
      
      @`Fancy${'$'}DslMarker`
      interface SomeClass1
      """,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          if (psiRef.shortName == "Fancy\$DslMarker") {
            psiRef.clazz.nameAsSafeName.asString().also(::println)
            return@simpleCodeGenerator null
          }

          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->

            val annotation = ref.annotations.single()

            assertThat(annotation.fqName.asString())
              .isEqualTo("com.squareup.test.Fancy\$DslMarker")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
