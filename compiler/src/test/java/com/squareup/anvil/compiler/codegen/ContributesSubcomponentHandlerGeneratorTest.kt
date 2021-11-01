package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.PARENT_COMPONENT
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.internal.testing.extends
import com.squareup.anvil.compiler.internal.testing.generatedClassesString
import com.squareup.anvil.compiler.internal.testing.use
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.subcomponentInterface
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test
import kotlin.test.assertFailsWith

class ContributesSubcomponentHandlerGeneratorTest {

  @Test fun `there is a subcomponent generated for a @MergeComponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated for a @MergeSubcomponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeSubcomponent
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeSubcomponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated for a @MergeModules`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.compat.MergeModules
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeModules(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is no subcomponent generated for a @MergeInterfaces`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.compat.MergeInterfaces
  
        @ContributesSubcomponent(Any::class, Unit::class)
        interface SubcomponentInterface
        
        @MergeInterfaces(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)

      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent
      }
    }
  }

  @Test fun `there is no subcomponent generated for a mismatching scopes`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Int::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)

      assertFailsWith<ClassNotFoundException> {
        subcomponentInterface.anvilComponent
      }
    }
  }

  @Test fun `there is a subcomponent generated for an inner class`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        class Outer {
          @ContributesSubcomponent(Any::class, Unit::class)
          interface SubcomponentInterface
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val subcomponentInterface = classLoader
        .loadClass("com.squareup.test.Outer\$SubcomponentInterface")

      val anvilComponent = subcomponentInterface.anvilComponent
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a subcomponent generated in a chain of contributed subcomponents`() {
    // This test will exercise multiple rounds of code generation.
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface1
  
        @ContributesSubcomponent(Int::class, parentScope = Any::class)
        interface SubcomponentInterface2
  
        @ContributesSubcomponent(Long::class, parentScope = Int::class)
        interface SubcomponentInterface3
      """.trimIndent()
    ) {
      for (index in 1..3) {
        val subcomponentInterface = classLoader
          .loadClass("com.squareup.test.SubcomponentInterface$index")

        val anvilComponent = subcomponentInterface.anvilComponent
        assertThat(anvilComponent).isNotNull()

        val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
        assertThat(annotation).isNotNull()
      }
    }
  }

  @Test fun `there is a subcomponent generated with separate compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
        import com.squareup.anvil.annotations.ContributesSubcomponent
        
        @ContributesSubcomponent(Unit::class, parentScope = Int::class)
        interface SubcomponentInterface
        
        @MergeComponent(Int::class)
        interface ComponentInterface
      """.trimIndent(),
      previousCompilationResult = firstCompilationResult
    ) {
      val anvilComponent = subcomponentInterface.anvilComponent
      assertThat(anvilComponent).isNotNull()

      val annotation = anvilComponent.getAnnotation(MergeSubcomponent::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Any::class)
    }
  }

  @Test
  fun `there is a parent component interface automatically generated without declaring one explicitly`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent = subcomponentInterface.anvilComponent.parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val annotation = parentComponent.getAnnotation(ContributesTo::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Unit::class)

      assertThat(parentComponent.declaredMethods.single().returnType)
        .isEqualTo(subcomponentInterface.anvilComponent)
    }
  }

  @Test
  fun `the parent component interface extends a manually declared component interface with the same scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
            fun integer(): Int
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent = subcomponentInterface.anvilComponent.parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val annotation = parentComponent.getAnnotation(ContributesTo::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Unit::class)
      assertThat(annotation.replaces.toList())
        .containsExactly(subcomponentInterface.anyParentComponentInterface.kotlin)

      val createComponentFunction = parentComponent.declaredMethods.single()
      assertThat(createComponentFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent)
      assertThat(createComponentFunction.name)
        .isEqualTo("createComponent")

      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isTrue()
    }
  }

  @Test
  fun `the parent component interface extends a manually declared component interface with the same scope with multiple compilations`() {
    val firstCompilationResult = compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
            fun integer(): Int
          }
        }
      """
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
  
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      previousCompilationResult = firstCompilationResult
    ) {
      val parentComponent = subcomponentInterface.anvilComponent.parentComponentInterface
      assertThat(parentComponent).isNotNull()

      val annotation = parentComponent.getAnnotation(ContributesTo::class.java)
      assertThat(annotation).isNotNull()
      assertThat(annotation.scope).isEqualTo(Unit::class)
      assertThat(annotation.replaces.toList())
        .containsExactly(subcomponentInterface.anyParentComponentInterface.kotlin)

      val createComponentFunction = parentComponent.declaredMethods.single()
      assertThat(createComponentFunction.returnType)
        .isEqualTo(subcomponentInterface.anvilComponent)
      assertThat(createComponentFunction.name)
        .isEqualTo("createComponent")

      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isTrue()
    }
  }

  @Test
  fun `the parent component interface does not extend a manually declared component interface with a different scope`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Int::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      val parentComponent = subcomponentInterface.anvilComponent.parentComponentInterface
      assertThat(
        parentComponent extends subcomponentInterface.anyParentComponentInterface
      ).isFalse()
    }
  }

  @Test fun `there must be only one parent component interface`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent1
          
          @ContributesTo(Unit::class)
          interface AnyParentComponent1
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (8, 11)")
      assertThat(messages).contains(
        "Expected zero or one parent component interface within " +
          "com.squareup.test.SubcomponentInterface being contributed to the parent scope."
      )
    }
  }

  @Test
  fun `a parent component interface must have no more than one function returning the contributed subcomponent`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent1(): SubcomponentInterface
            fun createComponent2(): SubcomponentInterface
          }
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent()
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains("Source0.kt: (10, 13)")
      assertThat(messages).contains(
        "Expected zero or one function returning the subcomponent " +
          "com.squareup.test.SubcomponentInterface."
      )
    }
  }

  @Test
  fun `Dagger generates the real component and subcomponent and they can be instantiated through the component interfaces`() {
    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesSubcomponent
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Module
        import dagger.Provides

        @ContributesTo(Any::class)
        @Module
        object DaggerModule {
          @Provides fun provideInteger(): Int = 5
        }
  
        @ContributesSubcomponent(Any::class, parentScope = Unit::class)
        interface SubcomponentInterface {
          @ContributesTo(Unit::class)
          interface AnyParentComponent {
            fun createComponent(): SubcomponentInterface
          }
          
          fun integer(): Int
        }
        
        @MergeComponent(Unit::class)
        interface ComponentInterface
      """,
      enableDaggerAnnotationProcessor = true
    ) {
      val daggerComponent = componentInterface.daggerComponent.declaredMethods
        .single { it.name == "create" }
        .invoke(null)

      // Note that there are no declared methods, only inherited methods.
      assertThat(componentInterface.declaredMethods.toList()).isEmpty()

      // There are two methods: one from AnyParentComponent and one from the generated component
      // interface. Both show up in the reflection APIs.
      val subcomponent = componentInterface.methods
        .first { it.name == "createComponent" }
        .invoke(daggerComponent)

      val int = subcomponent::class.java.declaredMethods
        .single { it.name == "integer" }
        .use { it.invoke(subcomponent) as Int }

      assertThat(int).isEqualTo(5)
    }
  }

  private val Class<*>.anvilComponent: Class<*>
    get() = classLoader
      .loadClass("$COMPONENT_PACKAGE_PREFIX.${generatedClassesString()}$ANVIL_SUBCOMPONENT_SUFFIX")

  private val Class<*>.anyParentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$AnyParentComponent")

  private val Class<*>.parentComponentInterface: Class<*>
    get() = classLoader.loadClass("$canonicalName\$$PARENT_COMPONENT")

  private val Class<*>.daggerComponent: Class<*>
    get() = classLoader.loadClass("$packageName.Dagger$simpleName")
}
