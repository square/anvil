package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.source.getPsi
import org.junit.Test

class ClassReferenceTest {

  @Test fun `inner classes are parsed`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1

      class SomeClass2 {
        class Inner
      }

      class SomeClass3 {
        companion object
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              assertThat(psiRef.innerClasses()).isEmpty()
              assertThat(descriptorRef.innerClasses()).isEmpty()

              assertThat(psiRef.enclosingClassesWithSelf()).hasSize(1)
              assertThat(descriptorRef.enclosingClassesWithSelf()).hasSize(1)
            }
            "SomeClass2" -> {
              assertThat(psiRef.innerClasses()).hasSize(1)
              assertThat(descriptorRef.innerClasses()).hasSize(1)

              assertThat(psiRef.innerClasses().single().shortName).isEqualTo("Inner")
              assertThat(descriptorRef.innerClasses().single().shortName).isEqualTo("Inner")
            }
            "SomeClass3" -> {
              assertThat(psiRef.innerClasses()).isEmpty()
              assertThat(descriptorRef.innerClasses()).isEmpty()

              assertThat(psiRef.companionObjects()).hasSize(1)
              assertThat(descriptorRef.companionObjects()).hasSize(1)
            }
            "Inner" -> {
              assertThat(psiRef.enclosingClassesWithSelf()).hasSize(2)
              assertThat(descriptorRef.enclosingClassesWithSelf()).hasSize(2)

              assertThat(psiRef.enclosingClass()?.shortName).isEqualTo("SomeClass2")
              assertThat(descriptorRef.enclosingClass()?.shortName).isEqualTo("SomeClass2")
            }
            "Companion" -> {
              assertThat(psiRef.enclosingClassesWithSelf()).hasSize(2)
              assertThat(descriptorRef.enclosingClassesWithSelf()).hasSize(2)

              assertThat(psiRef.enclosingClass()?.shortName).isEqualTo("SomeClass3")
              assertThat(descriptorRef.enclosingClass()?.shortName).isEqualTo("SomeClass3")
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `the type parameter list is parsed`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1<T : List<String>>(
        private val t: T
      )
      
      class SomeClass2<V>(
        private val v: V
      ) where V : Appendable, V : CharSequence

      class SomeClass3<T, R : Set<String>>(
        private val t: T,
        private val r: Lazy<R>
      ) where T : Appendable, T : CharSequence
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              val psiParameter = psiRef.typeParameters.single()
              val descriptorParameter = descriptorRef.typeParameters.single()

              assertThat(psiParameter.name).isEqualTo("T")
              assertThat(descriptorParameter.name).isEqualTo("T")

              assertThat(psiParameter.upperBounds.single().asTypeName().toString())
                .isEqualTo("kotlin.collections.List<kotlin.String>")
              assertThat(descriptorParameter.upperBounds.single().asTypeName().toString())
                .isEqualTo("kotlin.collections.List<kotlin.String>")
            }
            "SomeClass2" -> {
              val psiParameter = psiRef.typeParameters.single()
              val descriptorParameter = descriptorRef.typeParameters.single()

              assertThat(psiParameter.name).isEqualTo("V")
              assertThat(descriptorParameter.name).isEqualTo("V")

              assertThat(psiParameter.upperBounds[0].asClassReference().fqName)
                .isEqualTo(FqName("java.lang.Appendable"))
              assertThat(psiParameter.upperBounds[1].asClassReference().fqName)
                .isEqualTo(FqName("kotlin.CharSequence"))

              assertThat(descriptorParameter.upperBounds[0].asClassReference().fqName)
                .isEqualTo(FqName("java.lang.Appendable"))
              assertThat(descriptorParameter.upperBounds[1].asClassReference().fqName)
                .isEqualTo(FqName("kotlin.CharSequence"))
            }
            "SomeClass3" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                assertThat(ref.typeParameters).hasSize(2)

                val t = ref.typeParameters[0]
                assertThat(t.upperBounds[0].asClassReference().fqName)
                  .isEqualTo(FqName("java.lang.Appendable"))
                assertThat(t.upperBounds[1].asClassReference().fqName)
                  .isEqualTo(FqName("kotlin.CharSequence"))

                val r = ref.typeParameters[1]
                assertThat(r.upperBounds.single().asTypeName().toString())
                  .isEqualTo("kotlin.collections.Set<kotlin.String>")
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        }
      )
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}

fun ClassReference.toDescriptorReference(): ClassReference.Descriptor {
  return when (this) {
    is ClassReference.Descriptor -> this
    is ClassReference.Psi -> {
      // Force using the descriptor.
      module.resolveFqNameOrNull(fqName)!!.toClassReference(module)
        .also { descriptorReference ->
          assertThat(descriptorReference).isInstanceOf(ClassReference.Descriptor::class.java)

          assertThat(this).isEqualTo(descriptorReference)
          assertThat(this.fqName).isEqualTo(descriptorReference.fqName)
        }
    }
  }
}

fun ClassReference.toPsiReference(): ClassReference.Psi {
  return when (this) {
    is ClassReference.Psi -> this
    is ClassReference.Descriptor -> {
      // Force using Psi.
      (clazz.source.getPsi() as KtClassOrObject).toClassReference(module)
        .also { psiReference ->
          assertThat(psiReference).isInstanceOf(ClassReference.Psi::class.java)

          assertThat(this).isEqualTo(psiReference)
          assertThat(this.fqName).isEqualTo(psiReference.fqName)
        }
    }
  }
}
