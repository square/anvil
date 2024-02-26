package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.rickbusarow.kase.Kase3
import com.rickbusarow.kase.kase
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.reference.ReferencesTestEnvironment.AssignableTestAction
import com.squareup.anvil.compiler.internal.reference.ReferencesTestEnvironment.ReferenceType
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class TypeReferenceTest :
  ReferenceTests,
  ReferenceAsserts {

  override val testEnvironmentFactory
    get() = ReferencesTestEnvironment.Factory

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

  @TestFactory
  fun `simgle-param generic type references are assignable to non-generic supertypes`() =
    testFactory {

      compile(
        """
      package com.squareup.test
  
      interface RefsContainer {
        fun refs(
          list_charSequence: List<out CharSequence>,
        )
      }
      """,
      ) {

        val list_charSequence by referenceMap

        // list_string shouldBeAssignableTo any
        // list_string shouldBeAssignableTo any_nullable
        // list_string shouldNotBeAssignableTo string
        // list_string shouldNotBeAssignableTo string_nullable

        list_charSequence.typeVariance shouldBe TypeVariance.OUT
      }
    }

  @TestFactory
  fun `canary thing 5`() = foo2(
    displayName = { (assignable, a, b) ->
      if (assignable) {
        "$a is assignable to $b"
      } else {
        "$a is not assignable to $b"
      }
    },
    // kase(true, "MutableList<String>", "Collection<Any>"),
    // kase(true, "MutableList<String>", "Collection<out Any>"),
    // kase(true, "MutableList<String>", "Collection<Any?>"),
    // kase(true, "MutableList<String>", "List<Any>"),
    // kase(true, "MutableList<String>", "List<Any?>"),
    // kase(true, "MutableList<String>", "List<CharSequence>"),
    // kase(true, "MutableList<String>", "List<String>"),
    // kase(true, "MutableList<String>", "MutableList<*>"),
    // kase(true, "List<String>", "Collection<Any>"),
    // kase(true, "List<String>", "Collection<Any?>"),
    // kase(true, "List<String>", "List<Any>"),
    // kase(true, "List<String>", "List<Any?>"),
    // kase(true, "List<String>", "List<CharSequence>"),
    // kase(true, "List<String>", "List<String>"),
    kase(true, "Generic1Out<String>", "Generic1Out<CharSequence>"),
    // kase(false, "AbstractMap<String, List<Int>>", "Map<CharSequence, List<Int>>"),
    // kase(false, "Generic1<String>", "Generic1<CharSequence>"),
    // kase(false, "Generic1In<String>", "Generic1In<CharSequence>"),
    content = listOf(
      //language=kotlin
      """
      package com.squareup.test
      
      interface Generic1<T>
      interface Generic1In<in T>
      interface Generic1Out<out T>
      
      interface Generic2<T, R>
      interface Generic2In<in T, in R>
      interface Generic2Out<out T, out R>
      """.trimIndent(),
    ),
  ) { assignable, assigned, assignedTo ->

    if (assignable) {
      assigned shouldBeAssignableTo assignedTo
    } else {
      assigned shouldNotBeAssignableTo assignedTo
    }
  }

  fun kase(
    assignable: Boolean,
    assigned: String,
    assignedTo: String,
  ): Kase3<Boolean, String, String> = kase(a1 = assignable, a2 = assigned, a3 = assignedTo)

  fun foo2(
    displayName: (Kase3<Boolean, String, String>) -> String,
    @Language("kotlin") vararg assignableToAssignedTypes: Kase3<Boolean, String, String>,
    content: List<String> = emptyList(),
    referenceTypes: List<ReferenceType> = params,
    testAction: AssignableTestAction,
  ): Stream<out DynamicNode> = assignableToAssignedTypes.asList()
    .asContainers(displayName) { (assignable, assigned, assignTo) ->

      referenceTypes.asTests {
        compile(
          *content.toTypedArray(),
          assignable = assignable,
          assigned = assigned,
          assignedTo = assignTo,
          testAction = testAction,
        )
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
                  ref.properties.single { it.name == "map1" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Any", "Any").inOrder()
                assertThat(
                  ref.properties.single { it.name == "map2" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "Any").inOrder()
                assertThat(
                  ref.properties.single { it.name == "map3" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Any", "String").inOrder()
                assertThat(
                  ref.properties.single { it.name == "map4" }.type().unwrappedTypes
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
                  ref.properties.single { it.name == "map" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("String", "Int").inOrder()
                assertThat(
                  ref.properties.single { it.name == "single1" }.type().unwrappedTypes
                    .map { it.asClassReference().shortName },
                ).containsExactly("Int", "String", "Long").inOrder()
                assertThat(
                  ref.properties.single { it.name == "single2" }.type().unwrappedTypes
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
