package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test

class TypeReferenceTest {
  @Test fun `the type parameter arguments from a super type can be queried`() {
    compile(
      """
      package com.squareup.test

      abstract class SomeClass1 : Map<String, Int>
      
      abstract class SomeClass2 : List<Map<String, Int>>
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                val unwrappedClassNames = ref.directSuperTypeReferences().single().unwrappedTypes
                  .map { it.asClassReference().shortName }

                assertThat(unwrappedClassNames).containsExactly("String", "Int").inOrder()
              }
            }
            "SomeClass2" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                val listType = ref.directSuperTypeReferences().single()
                assertThat(listType.asClassReference().shortName).isEqualTo("List")

                val mapType = listType.unwrappedTypes.single()
                assertThat(mapType.asClassReference().shortName).isEqualTo("Map")

                assertThat(mapType.unwrappedTypes.map { it.asClassReference().shortName })
                  .containsExactly("String", "Int").inOrder()
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
