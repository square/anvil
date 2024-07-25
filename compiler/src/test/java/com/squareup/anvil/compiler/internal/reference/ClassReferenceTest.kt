package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.source.getPsi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

@Suppress("LocalVariableName")
class ClassReferenceTest : ReferenceTests {

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
        },
      ),
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
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `generic types are detected`() {
    compile(
      """
      package com.squareup.test

      class SomeClass1<T : List<String>>(
        private val t: T
      )
      
      abstract class SomeClass2 : Lazy<String> {
        abstract fun string(): String
      }

      abstract class SomeClass3 {
        abstract fun list(): List<String>
      }

      abstract class SomeClass4 {
        abstract fun <T : List<T>> list(): T
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              assertThat(psiRef.isGenericClass()).isTrue()
              assertThat(descriptorRef.isGenericClass()).isTrue()

              assertThat(
                psiRef.constructors.single().parameters.single().type().isGenericType(),
              ).isTrue()
              assertThat(
                descriptorRef.constructors.single().parameters.single().type().isGenericType(),
              ).isTrue()
            }
            "SomeClass2" -> {
              assertThat(psiRef.isGenericClass()).isFalse()
              assertThat(descriptorRef.isGenericClass()).isFalse()

              assertThat(
                psiRef.declaredMemberFunctions.single().returnType().isGenericType(),
              ).isFalse()
              assertThat(
                descriptorRef.declaredMemberFunctions.single { it.name == "string" }
                  .returnType()
                  .isGenericType(),
              ).isFalse()

              assertThat(
                psiRef.directSuperTypeReferences().single().isGenericType(),
              ).isTrue()
              assertThat(
                psiRef.directSuperTypeReferences().single().asClassReference().isGenericClass(),
              ).isTrue()
              assertThat(
                descriptorRef.directSuperTypeReferences().single().isGenericType(),
              ).isTrue()
              assertThat(
                descriptorRef.directSuperTypeReferences().single().asClassReference()
                  .isGenericClass(),
              ).isTrue()
            }
            "SomeClass3" -> {
              assertThat(
                psiRef.declaredMemberFunctions.single().returnType().isGenericType(),
              ).isTrue()
              assertThat(
                descriptorRef.declaredMemberFunctions.single { it.name == "list" }
                  .returnType()
                  .isGenericType(),
              ).isTrue()
            }
            "SomeClass4" -> {
              assertThat(
                psiRef.declaredMemberFunctions.single().returnType().isGenericType(),
              ).isTrue()
              assertThat(
                descriptorRef.declaredMemberFunctions.single { it.name == "list" }
                  .returnType()
                  .isGenericType(),
              ).isTrue()
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

  @Test fun `an enum entry is a class`() {
    compile(
      """
      package com.squareup.test

      annotation class AnyQualifier(val abc: Values) {
        enum class Values {
          ABC
        }
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          assertThat(
            psiRef.module.resolveFqNameOrNull(FqName("com.squareup.test.AnyQualifier")),
          ).isNotNull()

          assertThat(
            psiRef.module.resolveFqNameOrNull(FqName("com.squareup.test.AnyQualifier.Values")),
          ).isNotNull()

          assertThat(
            psiRef.module.resolveFqNameOrNull(FqName("com.squareup.test.AnyQualifier.Values.ABC")),
          ).isNotNull()

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `an import alias can be resolved`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.other.Other as Abc

      class SomeClass1 : Abc
      """,
      """
      package com.squareup.other
      
      interface Other
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "Other" -> Unit
            "SomeClass1" -> {
              assertThat(
                psiRef.directSuperTypeReferences().single().asClassReference().fqName,
              ).isEqualTo(FqName("com.squareup.other.Other"))
              assertThat(
                descriptorRef.directSuperTypeReferences().single().asClassReference().fqName,
              ).isEqualTo(FqName("com.squareup.other.Other"))
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

  @Test fun `a generic class can be converted to a typename`() {
    compile(
      """
      package com.squareup.test

      abstract class SomeClass1 : Lazy<String> {
        abstract fun string(): String
      }

      class SomeClass2<T : List<String>>(
        private val t: T
      )

      class SomeClass3<T : String, S : Map<List<String>, Int>>(
        private val t: T
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          when (psiRef.shortName) {
            "SomeClass1" -> {
              val expected = ClassName("com.squareup.test", "SomeClass1")
              assertThat(psiRef.asClassName()).isEqualTo(expected)
              assertThat(descriptorRef.asClassName()).isEqualTo(expected)

              assertThat(psiRef.asTypeName()).isEqualTo(expected)
              assertThat(descriptorRef.asTypeName()).isEqualTo(expected)
            }
            "SomeClass2" -> {
              val expected = ClassName("com.squareup.test", "SomeClass2")
              assertThat(psiRef.asClassName()).isEqualTo(expected)
              assertThat(descriptorRef.asClassName()).isEqualTo(expected)

              assertThat(psiRef.asTypeName().toString())
                .isEqualTo("com.squareup.test.SomeClass2<T>")
              assertThat(descriptorRef.asTypeName().toString())
                .isEqualTo("com.squareup.test.SomeClass2<T>")

              val psiTypeSpec = TypeSpec.classBuilder("Class")
                .addTypeVariables(psiRef.typeParameters.map { it.typeVariableName })
                .build()

              val descriptorTypeSpec = TypeSpec.classBuilder("Class")
                .addTypeVariables(psiRef.typeParameters.map { it.typeVariableName })
                .build()

              assertThat(psiTypeSpec.toString())
                .contains("public class Class<T : kotlin.collections.List<kotlin.String>>")
              assertThat(descriptorTypeSpec.toString())
                .contains("public class Class<T : kotlin.collections.List<kotlin.String>>")
            }
            "SomeClass3" -> {
              val expected = ClassName("com.squareup.test", "SomeClass3")
              assertThat(psiRef.asClassName()).isEqualTo(expected)
              assertThat(descriptorRef.asClassName()).isEqualTo(expected)

              assertThat(psiRef.asTypeName().toString())
                .isEqualTo("com.squareup.test.SomeClass3<T, S>")
              assertThat(descriptorRef.asTypeName().toString())
                .isEqualTo("com.squareup.test.SomeClass3<T, S>")

              val psiTypeSpec = TypeSpec.classBuilder("Class")
                .addTypeVariables(psiRef.typeParameters.map { it.typeVariableName })
                .build()

              val descriptorTypeSpec = TypeSpec.classBuilder("Class")
                .addTypeVariables(psiRef.typeParameters.map { it.typeVariableName })
                .build()

              assertThat(psiTypeSpec.toString()).contains(
                "public class Class<T : kotlin.String, " +
                  "S : kotlin.collections.Map<kotlin.collections.List<kotlin.String>, kotlin.Int>>",
              )
              assertThat(descriptorTypeSpec.toString()).contains(
                "public class Class<T : kotlin.String, " +
                  "S : kotlin.collections.Map<kotlin.collections.List<kotlin.String>, kotlin.Int>>",
              )
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

  @Test fun `super types can be resolved`() {
    compile(
      """
      package com.squareup.test

      abstract class OuterClass {
        interface InnerInterface
      }
      
      class ClassA : OuterClass() {
        class ClassB : InnerInterface
      }

      abstract class OuterClass2<T> {
        interface InnerInterface2<S>
      }

      class ClassE : OuterClass2<String>() {
        class ClassF : InnerInterface2<Int>
      }
      """,
      """
      package com.squareup.test2
      
      import com.squareup.test.OuterClass
        
      class ClassC : OuterClass() {
        class ClassD : InnerInterface
      }  
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            when (psiRef.shortName) {
              "OuterClass" -> {
                assertThat(ref.directSuperTypeReferences()).isEmpty()
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "InnerInterface" -> {
                assertThat(ref.directSuperTypeReferences()).isEmpty()
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "ClassA" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "ClassB" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "ClassC" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "ClassD" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "OuterClass2" -> {
                assertThat(ref.directSuperTypeReferences()).isEmpty()
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "InnerInterface2" -> {
                assertThat(ref.directSuperTypeReferences()).isEmpty()
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "ClassE" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "ClassF" -> {
                assertThat(ref.directSuperTypeReferences()).hasSize(1)
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
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

  @Test fun `an imported top level function doesn't confuse the class resolver`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.test.other.Navigator

      interface Navigator {
        interface Factory<T : Navigator>
      }
      
      class NavigatorImpl : Navigator {
        interface Factory : Navigator.Factory<NavigatorImpl>
      }
      """,
      """
      package com.squareup.test.other

      fun Navigator() = Unit
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          listOf(psiRef, psiRef.toDescriptorReference()).forEach { ref ->
            when (psiRef.classId.relativeClassName.asString()) {
              "Navigator" -> {
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "Navigator.Factory" -> {
                assertThat(ref.allSuperTypeClassReferences().toList()).isEmpty()
              }
              "NavigatorImpl" -> {
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
              "NavigatorImpl.Factory" -> {
                assertThat(ref.allSuperTypeClassReferences().toList()).hasSize(1)
              }
            }
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @TestFactory
  fun `declaredMemberFunctions does not include invisible inherited functions`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent {
        fun parentFunction1()

        fun parentFunction2() { }
      }

      interface Child : Parent
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.declaredMemberFunctions.map { it.fqName.asString() } shouldBe listOf()
    }
  }

  @TestFactory
  fun `memberFunctions includes invisible inherited functions`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent {
        fun parentFunction1()

        fun parentFunction2() { }
      }

      interface Child : Parent
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.memberFunctions.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Parent.parentFunction1",
        "com.squareup.test.Parent.parentFunction2",
      )
    }
  }

  @TestFactory
  fun `memberFunctions does not duplicate inherited functions`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent<T> {
        fun function1()
        fun function2()
        fun function3(name: String)
        fun function3(age: Int)
        fun function4(t: T)
        fun function5(t: T)
      }

      interface Child : Parent<String> {
        override fun function1()
        override fun function3(age: Int)
        override fun function5(t: String)
      }
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.memberFunctions.map { it.text } shouldBe listOf(
        "override fun function1()",
        "override fun function3(age: Int)",
        "override fun function5(t: String)",
        "fun function2()",
        "fun function3(name: String)",
        "fun function4(t: T)",
      )
    }
  }

  @TestFactory
  fun `function lists do not include functions from a nested class`() = testFactory {

    compile(
      """
        package com.squareup.test

        interface Parent {
          fun parentFunction1(): CharSequence
        }

        interface Child : Parent {
          fun function1(): String
          
          interface Nested {
            fun nestedFunction(): String
          }
        }
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.declaredMemberFunctions.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.function1",
      )
      Child.memberFunctions.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.function1",
        "com.squareup.test.Parent.parentFunction1",
      )
    }
  }

  @TestFactory
  fun `declaredMemberFunctions includes overridden inherited functions`() = testFactory {

    compile(
      """
        package com.squareup.test

        interface Parent {
          fun parentFunction1()

          fun parentFunction2() { }
        }

        interface Child : Parent {
          override fun parentFunction1() { }
        }
        
        class ChildClass : Parent {
          override fun parentFunction1() { }
          override fun toString() = ""
        }

      """.trimIndent(),
    ) {
      val Child by classReferences
      val ChildClass by classReferences

      Child.declaredMemberFunctions.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.parentFunction1",
      )
      ChildClass.declaredMemberFunctions.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.ChildClass.parentFunction1",
        "com.squareup.test.ChildClass.toString",
      )
    }
  }

  @TestFactory
  fun `declaredMemberProperties does not include invisible inherited properties`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent {
        val parentProperty1: CharSequence

        val parentProperty2: String
      }

      interface Child : Parent
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.declaredMemberProperties.map { it.fqName.asString() } shouldBe listOf()
    }
  }

  @TestFactory
  fun `memberProperties includes invisible inherited properties`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent {
        val parentProperty1: CharSequence

        val parentProperty2: String
      }

      interface Child : Parent
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.memberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Parent.parentProperty1",
        "com.squareup.test.Parent.parentProperty2",
      )
    }
  }

  @TestFactory
  fun `memberProperties does not duplicate inherited properties`() = testFactory {

    compile(
      """
      package com.squareup.test

      interface Parent {
        val parentProperty1: CharSequence

        val parentProperty2: String
      }

      interface Child : Parent {
        override val parentProperty1: String
      }
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.memberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.parentProperty1",
        "com.squareup.test.Parent.parentProperty2",
      )
    }
  }

  @TestFactory
  fun `declaredMemberProperties includes overridden inherited properties`() = testFactory {

    compile(
      """
        package com.squareup.test

        interface Parent {
          val parentProperty1: CharSequence

          val parentProperty2: String
        }

        interface Child : Parent {
          override val parentProperty1: String
        }
        
        abstract class ChildClass : Parent {
          abstract override val parentProperty1: String
        }

      """.trimIndent(),
    ) {
      val Child by classReferences
      val ChildClass by classReferences

      Child.declaredMemberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.parentProperty1",
      )
      ChildClass.declaredMemberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.ChildClass.parentProperty1",
      )
    }
  }

  @TestFactory
  fun `property lists do not include properties from a nested class`() = testFactory {

    compile(
      """
        package com.squareup.test

        interface Parent {
          val parentProperty1: CharSequence
        }

        interface Child : Parent {
          val property1: String
          
          interface Nested {
            val nestedProperty: String
          }
        }
      """.trimIndent(),
    ) {
      val Child by classReferences

      Child.declaredMemberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.property1",
      )
      Child.memberProperties.map { it.fqName.asString() } shouldBe listOf(
        "com.squareup.test.Child.property1",
        "com.squareup.test.Parent.parentProperty1",
      )
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
