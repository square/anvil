package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.ANVIL_SUBCOMPONENT_SUFFIX
import com.squareup.anvil.compiler.COMPONENT_PACKAGE_PREFIX
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.generatedClassesString
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

  private val Class<*>.anvilComponent: Class<*>
    get() = classLoader
      .loadClass("$COMPONENT_PACKAGE_PREFIX.${generatedClassesString()}$ANVIL_SUBCOMPONENT_SUFFIX")
}
