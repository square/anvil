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
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `type names may be wrapped in backticks`() {
    compile(
      """
      package com.squareup.test

      abstract class `Fancy${'$'}Class`

      class Subject : `Fancy${'$'}Class`()
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "Fancy\$Class" -> return@simpleCodeGenerator null

            "Subject" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                val superType = ref.directSuperTypeReferences().single()

                assertThat(superType.asTypeName().toString())
                  .isEqualTo("com.squareup.test.`Fancy\$Class`")

                assertThat(superType.asClassReference().fqName.asString())
                  .isEqualTo("com.squareup.test.Fancy\$Class")
              }
            }

            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `resolving the generic type name on a concrete type returns the concrete type`() {
    compile(
      """
      package com.squareup.test

      abstract class SomeClass1 : List<String>
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                assertThat(
                  ref.directSuperTypeReferences()
                    .single() // List<String>
                    .unwrappedTypes
                    .single() // String
                    .resolveGenericTypeNameOrNull(ref)
                    .toString(),
                ).isEqualTo("kotlin.String")
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test
  fun `resolving the generic type name with no available resolved type returns unresolved type`() {
    compile(
      """
      package com.squareup.test

      abstract class SomeClass1<T> : List<T>
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                assertThat(
                  ref.directSuperTypeReferences()
                    .single() // List<T>
                    .unwrappedTypes
                    .single() // T
                    .resolveGenericTypeNameOrNull(ref)
                    .toString(),
                ).isEqualTo("T")
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `resolving the generic type name from a parent returns the resolved type`() {
    compile(
      """
      package com.squareup.test

      abstract class SomeClass1<T> : List<T>

      abstract class SomeClass2 : SomeClass1<Int>()
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> Unit
            "SomeClass2" -> {
              listOf(psiRef, descriptorRef).forEach { ref ->
                assertThat(
                  ref.directSuperTypeReferences()
                    .single() // SomeClass1<Int>
                    .asClassReference()
                    .directSuperTypeReferences()
                    .single() // List<T>
                    .unwrappedTypes
                    .single() // T
                    .resolveGenericTypeNameOrNull(ref)
                    .toString(),
                ).isEqualTo("kotlin.Int")
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `star projections are supported`() {
    compile(
      """
      package com.squareup.test

      class SomeClass {
        lateinit var map1: Map<*, *>
        lateinit var map2: Map<Int, *>
        lateinit var map3: Map<*, String>
        lateinit var map4: Map<Int, String>
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          when (psiRef.shortName) {
            "SomeClass" -> {
              listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "map1" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Any", "Any").inOrder()
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "map2" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "Any").inOrder()
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "map3" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Any", "String").inOrder()
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "map4" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "String").inOrder()
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `unwrapped types contain types from type aliases`() {
    compile(
      """
      package com.squareup.test
      
      typealias AliasType<X> = Map<String, X>

      typealias MyPair<X, Y> = Triple<X, String, Y>
      typealias MySingle1<Z> = MyPair<Z, Long>
      typealias MySingle2<Z> = Triple<Z, Z, Z>

      class SomeClass {
        lateinit var map: AliasType<Int>
        lateinit var single1: MySingle1<Int>
        lateinit var single2: MySingle2<Int>
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          when (psiRef.shortName) {
            "SomeClass" -> {
              listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "map" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("String", "Int").inOrder()
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "single1" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "String", "Long").inOrder()
                assertThat(
                  ref.declaredMemberProperties.single { it.name == "single2" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "Int", "Int").inOrder()
              }
            }
            else -> throw NotImplementedError(psiRef.shortName)
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
