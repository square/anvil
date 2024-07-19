package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test

@Suppress("RemoveRedundantQualifierName")
class MemberPropertyReferenceTest {

  @Test fun `primary constructor val properties are properties`() {
    compile(
      """
      package com.squareup.test

      class Subject(
        val name: String,
        val age: Int
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "name: kotlin.String",
                "age: kotlin.Int",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `primary constructor var properties are properties`() {
    compile(
      """
      package com.squareup.test

      class Subject(
        var name: String,
        var age: Int
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "name: kotlin.String",
                "age: kotlin.Int",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member properties are properties`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        val name: String = ""
        val age: Int = 5
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "name: kotlin.String",
                "age: kotlin.Int",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `nested class member properties are not properties of the outer class`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        val name: String = ""
        
        class Nested {
          val age: Int = 5        
        }
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          if (psiRef.shortName == "Nested") return@simpleCodeGenerator null

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly("name: kotlin.String")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `nested class constructor properties are not properties of the outer class`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        val name: String = ""
        
        class Nested(
          val age: Int        
        )
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          if (psiRef.shortName == "Nested") return@simpleCodeGenerator null

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly("name: kotlin.String")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a stdlib type reference wrapped in backticks resolves to the standard type`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        val name: `String` = ""
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.declaredMemberProperties.single().type().asTypeName().toString())
              .isEqualTo("kotlin.String")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a property for a nested class with a name wrapped in backticks can be resolved`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        lateinit var fancy: `Nested${'$'}Fancy` 
        
        class `Nested${'$'}Fancy`
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          if (psiRef.shortName == "Nested\$Fancy") return@simpleCodeGenerator null

          listOf(psiRef, descriptorRef).forEach { ref ->

            val propertyType = ref.declaredMemberProperties.single().type()

            assertThat(propertyType.asTypeName().toString())
              .isEqualTo("com.squareup.test.Subject.`Nested\$Fancy`")

            assertThat(propertyType.asClassReference().fqName.asString())
              .isEqualTo("com.squareup.test.Subject.Nested\$Fancy")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a property for a partially qualified nested class with a name wrapped in backticks can be resolved`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        lateinit var fancy: Subject.`Nested${'$'}Fancy` 
        
        class `Nested${'$'}Fancy`
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          if (psiRef.shortName == "Nested\$Fancy") return@simpleCodeGenerator null

          listOf(psiRef, descriptorRef).forEach { ref ->

            val propertyType = ref.declaredMemberProperties.single().type()

            assertThat(propertyType.asTypeName().toString())
              .isEqualTo("com.squareup.test.Subject.`Nested\$Fancy`")

            assertThat(propertyType.asClassReference().fqName.asString())
              .isEqualTo("com.squareup.test.Subject.Nested\$Fancy")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a property with fully qualified reference to a nested class with a name wrapped in backticks can be resolved`() {
    compile(
      """
      package com.squareup.test

      class Subject {
        lateinit var fancy: com.squareup.test.Subject.`Nested${'$'}Fancy` 
        
        class `Nested${'$'}Fancy`
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          if (psiRef.shortName == "Nested\$Fancy") return@simpleCodeGenerator null

          listOf(psiRef, descriptorRef).forEach { ref ->

            val propertyType = ref.declaredMemberProperties.single().type()

            assertThat(propertyType.asTypeName().toString())
              .isEqualTo("com.squareup.test.Subject.`Nested\$Fancy`")

            assertThat(propertyType.asClassReference().fqName.asString())
              .isEqualTo("com.squareup.test.Subject.Nested\$Fancy")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `mixed constructor and member properties are all properties`() {
    compile(
      """
      package com.squareup.test

      class Subject(
        val name: String
      ) {
        val age: Int = 5
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "age: kotlin.Int",
                "name: kotlin.String",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `overridden member properties are properties`() {
    compile(
      """
      package com.squareup.test

      interface Named {
        val name: String
      }

      class Subject : Named {
        override val name: String = "" 
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          if (psiRef.shortName == "Named") return@simpleCodeGenerator null

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly("name: kotlin.String")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `properties in a super class are not properties`() {
    compile(
      """
      package com.squareup.test

      abstract class Named {
        val name: String = ""
      }

      class Subject : Named()
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          if (psiRef.shortName == "Named") return@simpleCodeGenerator null

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames()).isEmpty()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member properties can have annotations`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject {
        @Inject val name: String = ""
        val age: Int = 5
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameAnnotations = ref.declaredMemberProperties.named("name")
              .annotations
              .map { it.fqName.asString() }

            assertThat(nameAnnotations).containsExactly("javax.inject.Inject")

            val ageAnnotations = ref.declaredMemberProperties.named("age")
              .annotations
              .map { it.fqName.asString() }

            assertThat(ageAnnotations).isEmpty()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member properties can have annotations on a getter delegate`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject {
        @get:Inject val name: String = ""
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameGetterAnnotations = ref.declaredMemberProperties.named("name")
              .getterAnnotations
              .map { it.fqName.asString() }

            assertThat(nameGetterAnnotations).containsExactly("javax.inject.Inject")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member property getter annotations are included in the field's annotations`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject {
        @get:Inject val name: String = ""
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameGetterAnnotations = ref.declaredMemberProperties.named("name")
              .getterAnnotations
              .map { it.fqName.asString() }

            assertThat(nameGetterAnnotations).containsExactly("javax.inject.Inject")

            val nameAnnotations = ref.declaredMemberProperties.named("name")
              .annotations
              .map { it.fqName.asString() }

            assertThat(nameAnnotations).containsExactly("javax.inject.Inject")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member properties can have annotations on a setter delegate`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject {
        @set:Inject var name: String = "" 
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameSetterAnnotations = ref.declaredMemberProperties.named("name")
              .setterAnnotations
              .map { it.fqName.asString() }

            assertThat(nameSetterAnnotations).containsExactly("javax.inject.Inject")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `member property setter annotations are included in the field's annotations`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject {
        @set:Inject var name: String = "" 
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameSetterAnnotations = ref.declaredMemberProperties.named("name")
              .setterAnnotations
              .map { it.fqName.asString() }

            assertThat(nameSetterAnnotations).containsExactly("javax.inject.Inject")

            val nameAnnotations = ref.declaredMemberProperties.named("name")
              .annotations
              .map { it.fqName.asString() }

            assertThat(nameAnnotations).containsExactly("javax.inject.Inject")
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `primary constructor properties return empty accessor delegate annotations`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject(
        val name: String
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameProperty = ref.declaredMemberProperties.named("name")

            assertThat(nameProperty.getterAnnotations).isEmpty()
            assertThat(nameProperty.setterAnnotations).isEmpty()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `primary constructor properties can have annotations`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class Subject(
        @Inject var name: String,
        val age: Int
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->

          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            val nameAnnotations = ref.declaredMemberProperties.named("name")
              .annotations
              .map { it.fqName.asString() }

            assertThat(nameAnnotations).containsExactly("javax.inject.Inject")

            val ageAnnotations = ref.declaredMemberProperties.named("age")
              .annotations
              .map { it.fqName.asString() }

            assertThat(ageAnnotations).isEmpty()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `primary constructor parameters without val or var are not properties`() {
    compile(
      """
      package com.squareup.test

      class Subject(
        val name: String,
        val age: Int,
        notAProperty: Double
      )
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "name: kotlin.String",
                "age: kotlin.Int",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `secondary constructor parameters are not properties`() {
    compile(
      """
      package com.squareup.test

      class Subject(
        val name: String,
        val age: Int
      ) { 
        constructor(
          name: String,
          age: Int,
          notAProperty: Double
        ): this(name, age)
      }
      """,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        simpleCodeGenerator { psiRef ->
          val descriptorRef = psiRef.toDescriptorReference()

          listOf(psiRef, descriptorRef).forEach { ref ->

            assertThat(ref.propertyTypeNames())
              .containsExactly(
                "name: kotlin.String",
                "age: kotlin.Int",
              )
              .inOrder()
          }

          null
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  private fun ClassReference.propertyTypeNames() = declaredMemberProperties
    .map { "${it.name}: ${it.type().asTypeName()}" }

  private fun List<MemberPropertyReference>.named(name: String) = single { it.name == name }
}
