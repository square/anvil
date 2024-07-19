package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class MemberFunctionReferenceTest {

  @Test fun `constructors are parsed for PSI and Descriptor APIs correctly`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1

      class SomeClass2(string: String)

      class SomeClass3() {
        constructor(string: String) : this()
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              // Notice that the size differs between the descriptor and Psi implementation. Both
              // implementations see different values and that's expected.
              assertThat(psiRef.constructors).hasSize(0)

              assertThat(descriptorRef.constructors).hasSize(1)
              assertThat(descriptorRef.constructors.single().fqName.asString())
                .isEqualTo("com.squareup.test.SomeClass1.<init>")
            }
            "SomeClass2" -> {
              assertThat(psiRef.constructors).hasSize(1)
              assertThat(psiRef.constructors.single().fqName.asString())
                .isEqualTo("com.squareup.test.SomeClass2.<init>")

              assertThat(descriptorRef.constructors).hasSize(1)
              assertThat(descriptorRef.constructors.single().fqName.asString())
                .isEqualTo("com.squareup.test.SomeClass2.<init>")
            }
            "SomeClass3" -> {
              assertThat(psiRef.constructors).hasSize(2)
              psiRef.constructors.forEach {
                assertThat(it.fqName.asString())
                  .isEqualTo("com.squareup.test.SomeClass3.<init>")
              }

              assertThat(descriptorRef.constructors).hasSize(2)
              descriptorRef.constructors.forEach {
                assertThat(it.fqName.asString())
                  .isEqualTo("com.squareup.test.SomeClass3.<init>")
              }
            }
            else -> throw NotImplementedError()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `functions are parsed for PSI and Descriptor APIs correctly`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1

      class SomeClass2 {
        fun test(string: String): Unit = Unit
      }

      interface SomeInterface {
        fun test(): Int
        
        companion object {
          fun test2(): Int = 8
        }
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              // Notice that the size differs between the descriptor and Psi implementation. Both
              // implementations see different values and that's expected.
              assertThat(psiRef.declaredMemberFunctions).hasSize(0)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(3)
              assertThat(descriptorRef.declaredMemberFunctions.map { it.name }).containsExactly(
                "equals",
                "toString",
                "hashCode",
              )
            }
            "SomeClass2" -> {
              assertThat(psiRef.declaredMemberFunctions).hasSize(1)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(4)

              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "test" }

              assertThat(psiFunction.name).isEqualTo("test")
              assertThat(descriptorFunction.name).isEqualTo("test")

              assertThat(psiFunction.parameters).hasSize(1)
              assertThat(descriptorFunction.parameters).hasSize(1)

              assertThat(psiFunction.returnType().asClassReference().fqName)
                .isEqualTo(Unit::class.fqName)
              assertThat(descriptorFunction.returnType().asClassReference().fqName)
                .isEqualTo(Unit::class.fqName)
            }
            "SomeInterface" -> {
              assertThat(psiRef.declaredMemberFunctions).hasSize(1)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(4)

              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "test" }

              assertThat(psiFunction.name).isEqualTo("test")
              assertThat(descriptorFunction.name).isEqualTo("test")

              assertThat(psiFunction.isAbstract()).isTrue()
              assertThat(descriptorFunction.isAbstract()).isTrue()

              assertThat(psiFunction.isConstructor()).isFalse()
              assertThat(descriptorFunction.isConstructor()).isFalse()

              assertThat(psiFunction.visibility()).isEqualTo(Visibility.PUBLIC)
              assertThat(descriptorFunction.visibility()).isEqualTo(Visibility.PUBLIC)
            }
            "Companion" -> {
              assertThat(psiRef.enclosingClass()?.shortName).isEqualTo("SomeInterface")
              assertThat(descriptorRef.enclosingClass()?.shortName).isEqualTo("SomeInterface")

              assertThat(psiRef.declaredMemberFunctions).hasSize(1)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(4)
            }
            else -> throw NotImplementedError()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `the return type of a function can be resolved`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1 {
        fun test(string: String) = Unit
      }

      interface GenericInterface1<T> {
        fun hello(): T
      }

      interface GenericInterface2<S> : GenericInterface1<S>

      class SomeClass2 : GenericInterface1<String> {
        override fun hello(): String = "hello"
      }

      class SomeClass3 : GenericInterface2<String> {
        override fun hello(): String = "hello"
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "test" }

              assertThat(psiFunction.returnTypeOrNull()).isNull()
              assertThat(descriptorFunction.returnType().asClassReference().fqName)
                .isEqualTo(Unit::class.fqName)

              // That is null as well, because it's not a generic return type and we can't resolve
              // the return type from a function body. The compiler will take care of this in the
              // backend.
              assertThat(psiFunction.resolveGenericReturnTypeOrNull(psiRef)).isNull()
            }
            "SomeClass2", "SomeClass3" -> {
              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "hello" }

              assertThat(psiFunction.returnType().asClassReference().fqName)
                .isEqualTo(FqName("kotlin.String"))
              assertThat(descriptorFunction.returnType().asClassReference().fqName)
                .isEqualTo(FqName("kotlin.String"))
            }
            "GenericInterface1" -> {
              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "hello" }

              assertThat(psiFunction.returnType().asClassReferenceOrNull()).isNull()
              assertThat(descriptorFunction.returnType().asClassReferenceOrNull()).isNull()

              val implementingClass2 = FqName("com.squareup.test.SomeClass2")
                .toClassReference(psiRef.module)

              assertThat(
                psiFunction
                  .resolveGenericReturnType(implementingClass2.toPsiReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                psiFunction
                  .resolveGenericReturnType(implementingClass2.toDescriptorReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))

              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass2.toPsiReference()).fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass2.toDescriptorReference()).fqName,
              ).isEqualTo(FqName("kotlin.String"))

              val implementingClass3 = FqName("com.squareup.test.SomeClass3")
                .toClassReference(psiRef.module)

              assertThat(
                psiFunction.resolveGenericReturnType(implementingClass3.toPsiReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                psiFunction.resolveGenericReturnType(implementingClass3.toDescriptorReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))

              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass3.toPsiReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass3.toDescriptorReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
            }
            "GenericInterface2" -> {
              assertThat(psiRef.declaredMemberFunctions).hasSize(0)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(4)

              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "hello" }
              assertThat(descriptorFunction.returnType().asClassReferenceOrNull()).isNull()

              val implementingClass = FqName("com.squareup.test.SomeClass3")
                .toClassReference(psiRef.module)

              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass.toPsiReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                descriptorFunction
                  .resolveGenericReturnType(implementingClass.toDescriptorReference())
                  .fqName,
              ).isEqualTo(FqName("kotlin.String"))
            }
            else -> throw NotImplementedError()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `the parameter type of a function can be resolved`() {
    compile(
      """
      package com.squareup.test

      interface GenericInterface1<T> {
        fun hello(param: T)
      }

      interface GenericInterface2<S> : GenericInterface1<S>

      class SomeClass1 : GenericInterface1<String> {
        override fun hello(param: String) = Unit
      }

      class SomeClass2 : GenericInterface2<String> {
        override fun hello(param: String) = Unit
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1", "SomeClass2" -> {
              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction =
                descriptorRef.declaredMemberFunctions.single { it.name == "hello" }

              assertThat(
                psiFunction.parameters.single().typeOrNull()
                  ?.asClassReferenceOrNull()
                  ?.fqName,
              ).isEqualTo(FqName("kotlin.String"))
              assertThat(
                descriptorFunction.parameters.single().typeOrNull()
                  ?.asClassReferenceOrNull()
                  ?.fqName,
              ).isEqualTo(FqName("kotlin.String"))

              assertThat(
                psiFunction.parameters.single().typeOrNull()?.asTypeNameOrNull(),
              ).isNotNull()
              assertThat(
                descriptorFunction.parameters.single().typeOrNull()?.asTypeNameOrNull(),
              ).isNotNull()
            }
            "GenericInterface1" -> {
              val psiFunction = psiRef.declaredMemberFunctions.single()
              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "hello" }

              assertThat(psiFunction.parameters.single().type().asClassReferenceOrNull())
                .isNull()
              assertThat(descriptorFunction.parameters.single().type().asClassReferenceOrNull())
                .isNull()

              val implementingClass1 = FqName("com.squareup.test.SomeClass1")
                .toClassReference(psiRef.module)

              assertThat(
                psiFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass1.toPsiReference()),
              ).isNotNull()
              assertThat(
                psiFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass1.toDescriptorReference()),
              ).isNotNull()

              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass1.toPsiReference()),
              ).isNotNull()
              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass1.toDescriptorReference()),
              ).isNotNull()

              val implementingClass2 = FqName("com.squareup.test.SomeClass2")
                .toClassReference(psiRef.module)

              assertThat(
                psiFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass2.toPsiReference()),
              ).isNotNull()
              assertThat(
                psiFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass2.toDescriptorReference()),
              ).isNotNull()

              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass2.toPsiReference()),
              ).isNotNull()
              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass2.toDescriptorReference()),
              ).isNotNull()
            }
            "GenericInterface2" -> {
              assertThat(psiRef.declaredMemberFunctions).hasSize(0)
              assertThat(descriptorRef.declaredMemberFunctions).hasSize(4)

              val descriptorFunction = descriptorRef.declaredMemberFunctions
                .single { it.name == "hello" }
              assertThat(
                descriptorFunction.parameters.single().type().asClassReferenceOrNull(),
              ).isNull()

              val implementingClass = FqName("com.squareup.test.SomeClass2")
                .toClassReference(psiRef.module)

              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass.toPsiReference()),
              ).isNotNull()
              assertThat(
                descriptorFunction.parameters.single()
                  .resolveTypeNameOrNull(implementingClass.toDescriptorReference()),
              ).isNotNull()
            }
            else -> throw NotImplementedError()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
