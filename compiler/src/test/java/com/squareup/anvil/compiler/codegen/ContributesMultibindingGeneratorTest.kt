package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.compiler.assertFileGenerated
import com.squareup.anvil.compiler.assertStableInterfaceOrder
import com.squareup.anvil.compiler.codegen.ksp.simpleSymbolProcessor
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.generatedFileOrNull
import com.squareup.anvil.compiler.includeKspTests
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.multibindingModule
import com.squareup.anvil.compiler.multibindingModuleScope
import com.squareup.anvil.compiler.multibindingModuleScopes
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("RemoveRedundantQualifierName")
@RunWith(Parameterized::class)
class ContributesMultibindingGeneratorTest(
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun modes(): Collection<Any> {
      return buildList {
        add(AnvilCompilationMode.Embedded())
        if (includeKspTests()) {
          add(AnvilCompilationMode.Ksp())
        }
      }
    }
  }

  @Test fun `there is a binding module for a contributed multibinding for interfaces`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)

      assertFileGenerated(mode, "ContributingInterfaceMultiBindingModule.kt")
    }
  }

  @Test fun `there is a binding module for a contributed multibinding for classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      class ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a binding module for a contributed multibinding for an object`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      object ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
    }
  }

  @Test fun `the order of the scope can be changed with named parameters`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(boundType = ParentInterface::class, scope = Int::class)
      class ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Int::class)
    }
  }

  @Test fun `there is a binding module for a contributed multibinding for inner interfaces`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      class Abc {
        @ContributesMultibinding(Any::class, ParentInterface::class)
        interface ContributingInterface : ParentInterface
      }
      """,
      mode = mode,
    ) {
      val contributingInterface =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingInterface")
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there is a binding module for a contributed multibinding for inner classes`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface
      
      class Abc {
        @ContributesMultibinding(Any::class, ParentInterface::class)
        class ContributingClass : ParentInterface
      }
      """,
      mode = mode,
    ) {
      val contributingClass =
        classLoader.loadClass("com.squareup.test.Abc\$ContributingClass")
      assertThat(contributingClass.multibindingModule?.java).isEqualTo(contributingClass)
      assertThat(contributingClass.multibindingModuleScope).isEqualTo(Any::class)

      assertFileGenerated(mode, "Abc_ContributingClassMultiBindingModule.kt")
    }
  }

  @Test fun `contributed multibinding class must be public`() {
    val visibilities = setOf(
      "internal",
      "private",
      "protected",
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesMultibinding

        interface ParentInterface

        @ContributesMultibinding(Any::class, ParentInterface::class)
        $visibility class ContributingInterface : ParentInterface
        """,
      ) {
        assertThat(exitCode).isError()
        // Position to the class.
        assertThat(messages).contains("Source0.kt:8")
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface is binding a type, but the class is not " +
            "public. Only public types are supported.",
        )
      }
    }
  }

  @Test fun `contributed multibindings aren't allowed to have more than one qualifier`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import javax.inject.Qualifier
      
      @Qualifier
      annotation class AnyQualifier1
      
      @Qualifier
      annotation class AnyQualifier2

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @AnyQualifier1 
      @AnyQualifier2
      interface ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "Classes annotated with @ContributesMultibinding may not use more than one @Qualifier.",
      )
    }
  }

  @Test fun `the bound type can only be implied with one super type (2 interfaces)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface, CharSequence
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation.",
      )
    }
  }

  @Test fun `the bound type can only be implied with one super type (class and interface)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      open class Abc

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : Abc(), ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation.",
      )
    }
  }

  @Test fun `the bound type can only be implied with one super type (no super type)`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      @ContributesMultibinding(Any::class)
      object ContributingInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
          "the bound type. This is only allowed with exactly one direct super type. If there " +
          "are multiple or none, then the bound type must be explicitly defined in the " +
          "@ContributesMultibinding annotation.",
      )
    }
  }

  @Test fun `the bound type is not implied when explicitly defined`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(
        scope = Int::class, 
        ignoreQualifier = true, 
        boundType = ParentInterface::class
      )
      interface ContributingInterface : ParentInterface, CharSequence
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Int::class)
    }
  }

  @Test fun `the contributed multibinding class must extend the bound type`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : CharSequence
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding for " +
          "com.squareup.test.ParentInterface, but doesn't extend this type.",
      )
    }
  }

  @Test fun `the contributed multibinding class can extend Any explicitly`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      @ContributesMultibinding(Int::class, boundType = Any::class)
      interface ContributingInterface
      """,
      mode = mode,
    ) {
      println(generatedFiles.toList().joinToString("\n"))
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Int::class)
    }
  }

  @Test fun `a contributed multibinding can be generated`() {
    val stubContentToGenerate =
      //language=kotlin
      """
          package com.squareup.test
              
          import com.squareup.anvil.annotations.ContributesMultibinding
          import dagger.MapKey
          import javax.inject.Singleton
       
          @ContributesMultibinding(Any::class)
          @BindingKey("abc")
          @Singleton
          interface ContributingInterface : ParentInterface
      """.trimIndent()

    val localMode = when (mode) {
      is AnvilCompilationMode.Embedded -> {
        val codeGenerator = simpleCodeGenerator { clazz ->
          clazz
            .takeIf { it.isAnnotatedWith(mergeComponentFqName) }
            ?.let { stubContentToGenerate }
        }
        AnvilCompilationMode.Embedded(listOf(codeGenerator))
      }
      is AnvilCompilationMode.Ksp -> {
        val processor = simpleSymbolProcessor { resolver ->
          resolver.getSymbolsWithAnnotation(MergeComponent::class.qualifiedName!!)
            .map { stubContentToGenerate }
            .toList()
        }
        AnvilCompilationMode.Ksp(listOf(processor))
      }
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.MapKey
        
        @MapKey
        annotation class BindingKey(val value: String)
        
        interface ParentInterface
  
        @MergeComponent(Any::class)
        interface ComponentInterface
      """,
      mode = localMode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
    }
  }

  @Test fun `a contributed multibinding can be generated with map keys being generated`() {
    val stubContentToGenerate =
      //language=kotlin
      """
          package com.squareup.test
              
          import com.squareup.anvil.annotations.ContributesMultibinding
          import dagger.MapKey
          import javax.inject.Singleton

          interface ParentInterface

          @MapKey
          annotation class BindingKey1(val value: String)
      
          @ContributesMultibinding(Any::class)
          @BindingKey1("abc")
          @Singleton
          interface ContributingInterface : ParentInterface
      """.trimIndent()

    val localMode = when (mode) {
      is AnvilCompilationMode.Embedded -> {
        val codeGenerator = simpleCodeGenerator { clazz ->
          clazz
            .takeIf { it.isAnnotatedWith(mergeComponentFqName) }
            ?.let {
              stubContentToGenerate
            }
        }
        AnvilCompilationMode.Embedded(listOf(codeGenerator))
      }
      is AnvilCompilationMode.Ksp -> {
        val processor = simpleSymbolProcessor { resolver ->
          resolver.getSymbolsWithAnnotation(MergeComponent::class.qualifiedName!!)
            .map { stubContentToGenerate }
            .toList()
        }
        AnvilCompilationMode.Ksp(listOf(processor))
      }
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.MergeComponent
        
        @MergeComponent(Any::class)
        interface ComponentInterface
      """,
      mode = localMode,
    ) {
      assertThat(exitCode).isEqualTo(OK)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
    }
  }

  @Test fun `there are multiple hints for multiple contributed multibindings`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @ContributesMultibinding(Unit::class)
      class ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScopes)
        .containsExactly(Any::class, Unit::class)
    }
  }

  @Test fun `the scopes for multiple contributions have a stable sort`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @ContributesMultibinding(Unit::class)
      class ContributingInterface : ParentInterface
      
      @ContributesMultibinding(Unit::class)
      @ContributesMultibinding(Any::class)
      class SecondContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      generatedFileOrNull(mode, "ContributingInterfaceMultiBindingModule.kt")!!
        .assertStableInterfaceOrder()
      generatedFileOrNull(mode, "SecondContributingInterfaceMultiBindingModule.kt")!!
        .assertStableInterfaceOrder()
    }
  }

  @Test fun `there are multiple hints for contributed multibindings with fully qualified names`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      
      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @com.squareup.anvil.annotations.ContributesMultibinding(Unit::class)
      class ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScopes)
        .containsExactly(Any::class, Unit::class)
    }
  }

  @Test fun `multiple annotations with the same scope and bound type aren't allowed`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @ContributesMultibinding(Any::class, replaces = [Int::class])
      @ContributesMultibinding(Unit::class)
      @ContributesMultibinding(Unit::class, replaces = [Int::class])
      class ContributingInterface : ParentInterface
      """,
      mode = mode,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes multiple times to the same scope " +
          "using the same bound type: [ParentInterface]. Contributing multiple times to the " +
          "same scope with the same bound type is forbidden and all scope - bound type " +
          "combinations must be distinct.",
      )
    }
  }

  @Test fun `multiple annotations with the same scope and different bound type are allowed`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface1
      interface ParentInterface2

      @ContributesMultibinding(Any::class, boundType = ParentInterface1::class)
      @ContributesMultibinding(Any::class, boundType = ParentInterface2::class)
      @ContributesMultibinding(Unit::class, boundType = ParentInterface1::class)
      @ContributesMultibinding(Unit::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingModule?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScopes.toSet())
        .containsExactly(Any::class, Unit::class)
    }
  }
}
