package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.source.getPsi
import org.junit.Test

class ClassReferenceTest {

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
        }
      )
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
              assertThat(psiRef.functions).hasSize(0)
              assertThat(descriptorRef.functions).hasSize(3)
              assertThat(descriptorRef.functions.map { it.name }).containsExactly(
                "equals", "toString", "hashCode"
              )
            }
            "SomeClass2" -> {
              assertThat(psiRef.functions).hasSize(1)
              assertThat(descriptorRef.functions).hasSize(4)

              val psiFunction = psiRef.functions.single()
              val descriptorFunction = descriptorRef.functions.single { it.name == "test" }

              assertThat(psiFunction.name).isEqualTo("test")
              assertThat(descriptorFunction.name).isEqualTo("test")

              assertThat(psiFunction.parameters).hasSize(1)
              assertThat(descriptorFunction.parameters).hasSize(1)

              assertThat(psiFunction.returnType().fqName).isEqualTo(Unit::class.fqName)
              assertThat(descriptorFunction.returnType().fqName).isEqualTo(Unit::class.fqName)
            }
            "SomeInterface" -> {
              assertThat(psiRef.functions).hasSize(1)
              assertThat(descriptorRef.functions).hasSize(4)

              val psiFunction = psiRef.functions.single()
              val descriptorFunction = descriptorRef.functions.single { it.name == "test" }

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

              assertThat(psiRef.functions).hasSize(1)
              assertThat(descriptorRef.functions).hasSize(4)
            }
            else -> throw NotImplementedError()
          }

          null
        }
      )
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
              val psiFunction = psiRef.functions.single()
              val descriptorFunction = descriptorRef.functions.single { it.name == "test" }

              assertThat(psiFunction.returnTypeOrNull()).isNull()
              assertThat(descriptorFunction.returnType().fqName).isEqualTo(Unit::class.fqName)

              // That is null as well, because it's not a generic return type and we can't resolve
              // the return type from a function body. The compiler will take care of this in the
              // backend.
              assertThat(psiFunction.resolveGenericReturnTypeOrNull(psiRef)).isNull()
            }
            "SomeClass2", "SomeClass3" -> {
              val psiFunction = psiRef.functions.single()
              val descriptorFunction = descriptorRef.functions.single { it.name == "hello" }

              assertThat(psiFunction.returnType().fqName).isEqualTo(FqName("kotlin.String"))
              assertThat(descriptorFunction.returnType().fqName).isEqualTo(FqName("kotlin.String"))
            }
            "GenericInterface1" -> {
              val psiFunction = psiRef.functions.single()
              val descriptorFunction = descriptorRef.functions.single { it.name == "hello" }

              assertThat(psiFunction.returnTypeOrNull()).isNull()
              assertThat(descriptorFunction.returnTypeOrNull()).isNull()

              val implementingClass2 = FqName("com.squareup.test.SomeClass2")
                .toClassReference(psiRef.module)
                .toPsiReference()

              assertThat(
                psiFunction.resolveGenericReturnType(implementingClass2).fqName
              ).isEqualTo(FqName("kotlin.String"))

              assertThat(
                descriptorFunction.resolveGenericReturnType(implementingClass2).fqName
              ).isEqualTo(FqName("kotlin.String"))

              val implementingClass3 = FqName("com.squareup.test.SomeClass3")
                .toClassReference(psiRef.module)
                .toPsiReference()

              assertThat(
                psiFunction.resolveGenericReturnType(implementingClass3).fqName
              ).isEqualTo(FqName("kotlin.String"))

              assertThat(
                descriptorFunction.resolveGenericReturnType(implementingClass3).fqName
              ).isEqualTo(FqName("kotlin.String"))
            }
            "GenericInterface2" -> {
              assertThat(psiRef.functions).hasSize(0)
              assertThat(descriptorRef.functions).hasSize(4)

              val descriptorFunction = descriptorRef.functions.single { it.name == "hello" }
              assertThat(descriptorFunction.returnTypeOrNull()).isNull()

              val implementingClass = FqName("com.squareup.test.SomeClass3")
                .toClassReference(psiRef.module)
                .toPsiReference()

              assertThat(
                descriptorFunction.resolveGenericReturnType(implementingClass).fqName
              ).isEqualTo(FqName("kotlin.String"))
            }
            else -> throw NotImplementedError()
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  private fun ClassReference.Psi.toDescriptorReference(): ClassReference.Descriptor {
    // Using the FqName will resolve a descriptor of source files from the main source set. On the
    // other hand, generated source code from code generators will always be the PSI
    // implementation.
    val descriptorClassReference = fqName.classDescriptor(module).toClassReference(module)
    assertThat(descriptorClassReference).isInstanceOf(ClassReference.Descriptor::class.java)

    assertThat(this).isEqualTo(descriptorClassReference)
    assertThat(this.fqName).isEqualTo(descriptorClassReference.fqName)

    return descriptorClassReference as ClassReference.Descriptor
  }

  private fun ClassReference.toPsiReference(): ClassReference.Psi {
    return when (this) {
      is ClassReference.Psi -> this
      is ClassReference.Descriptor -> (clazz.source.getPsi() as? KtClassOrObject)
        ?.toClassReference(module)
        ?: throw UnsupportedOperationException()
    }
  }
}
