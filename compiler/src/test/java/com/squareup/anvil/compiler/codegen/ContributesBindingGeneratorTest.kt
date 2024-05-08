package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.wrap
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.assertCompilationSucceeded
import com.squareup.anvil.compiler.assertFileGenerated
import com.squareup.anvil.compiler.bindingModuleScope
import com.squareup.anvil.compiler.bindingModuleScopes
import com.squareup.anvil.compiler.bindingOriginKClass
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.contributingObject
import com.squareup.anvil.compiler.generatedBindingModule
import com.squareup.anvil.compiler.generatedBindingModules
import com.squareup.anvil.compiler.injectClass
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.moduleFactoryClass
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTest
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTestEnvironment
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import javax.inject.Named

class ContributesBindingGeneratorTest : AnvilCompilationModeTest(
  AnvilCompilationMode.Embedded(),
  AnvilCompilationMode.Ksp(),
) {

  @TestFactory fun `there is a binding module for a contributed binding for interfaces`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      interface ContributingInterface : ParentInterface
      """,
      ) {
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(contributingInterface.bindingModuleScope).isEqualTo(Any::class)

        assertFileGenerated(
          mode,
          "ContributingInterface_ParentInterface_Any_BindingModule_68088f18.kt",
        )
      }
    }

  @TestFactory fun `there is a binding module for a contributed binding for classes`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      class ContributingInterface : ParentInterface
      """,
      ) {
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(contributingInterface.bindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory
  fun `a Named annotation using a private top-level const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
        import javax.inject.Named
  
        interface ParentInterface
  
        private const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
  
        @Named(CONSTANT)
        @ContributesBinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val stringKey = injectClass.generatedBindingModule.methods.single()
          .getDeclaredAnnotation(Named::class.java)

        assertThat(stringKey.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory
  fun `a Named annotation using a private object's const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
        import javax.inject.Named
  
        private object Constants {
          const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
        }
  
        interface ParentInterface
  
        @Named(Constants.CONSTANT)
        @ContributesBinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val Named = injectClass.generatedBindingModule.methods.single()
          .getDeclaredAnnotation(Named::class.java)

        assertThat(Named.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory
  fun `a Named annotation using a private companion object's const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding 
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
        import javax.inject.Named
  
        private interface Settings {
          companion object {
            const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
          }
        }
  
        interface ParentInterface
  
        @Named(Settings.CONSTANT)
        @ContributesBinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val Named = injectClass.generatedBindingModule.methods.single()
          .getDeclaredAnnotation(Named::class.java)

        assertThat(Named.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory fun `there is a binding module for a contributed binding for an object`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      object ContributingInterface : ParentInterface
      """,
      ) {
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(contributingInterface.bindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory fun `the order of the scope can be changed with named parameters`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(boundType = ParentInterface::class, scope = Int::class)
      class ContributingInterface : ParentInterface
      """,
    ) {
      assertThat(contributingInterface.bindingModuleScope).isEqualTo(Int::class)
    }
  }

  @TestFactory fun `there is a binding module for a contributed binding for inner interfaces`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      class Abc {
        @ContributesBinding(Any::class, ParentInterface::class)
        interface ContributingInterface : ParentInterface
      }
      """,
      ) {
        val contributingInterface =
          classLoader.loadClass("com.squareup.test.Abc\$ContributingInterface")
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(contributingInterface.bindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory fun `there is a binding module for a contributed binding for inner classes`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface
      
      class Abc {
        @ContributesBinding(Any::class, ParentInterface::class)
        class ContributingClass : ParentInterface
      }
      """,
      ) {
        val contributingClass =
          classLoader.loadClass("com.squareup.test.Abc\$ContributingClass")
        assertThat(contributingClass.bindingOriginKClass?.java).isEqualTo(contributingClass)
        assertThat(contributingClass.bindingModuleScope).isEqualTo(Any::class)

        assertFileGenerated(
          mode,
          "ContributingClass_ParentInterface_Any_BindingModule_91037c91.kt",
        )
      }
    }

  @TestFactory fun `there is a binding module for a contributed binding for very long class names`() =
    testFactory {

      val longClassName = "A".repeat(240)

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
  
        interface ParentInterface
        
        @ContributesBinding(Any::class, ParentInterface::class)
        class $longClassName : ParentInterface
        """,
      ) {
        val contributingClass =
          classLoader.loadClass("com.squareup.test.$longClassName")
        assertThat(contributingClass.bindingOriginKClass?.java).isEqualTo(contributingClass)
        assertThat(contributingClass.bindingModuleScope).isEqualTo(Any::class)

        val hash = "66db371b"
        val truncatedLength = 240 - "com.squareup.test".length
        val truncatedName = "${longClassName.take(truncatedLength)}_$hash"

        assertFileGenerated(
          mode,
          "$truncatedName.kt",
        )
      }
    }

  @TestFactory fun `contributed binding class must be public`() = testFactory {
    val visibilities = setOf(
      "internal",
      "private",
      "protected",
    )

    visibilities.forEach { visibility ->
      compile(
        """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding

        interface ParentInterface

        @ContributesBinding(Any::class, ParentInterface::class)
        $visibility class ContributingInterface : ParentInterface
        """,
        mode = mode,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        // Position to the class.
        assertThat(messages).contains("Source0.kt:8")
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface is binding a type, but the class is not " +
            "public. Only public types are supported.",
        )
      }
    }
  }

  @TestFactory fun `contributed bindings aren't allowed to have more than one qualifier`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import javax.inject.Qualifier
      
      @Qualifier
      annotation class AnyQualifier1
      
      @Qualifier
      annotation class AnyQualifier2

      interface ParentInterface

      @com.squareup.anvil.annotations.ContributesBinding(Any::class)
      @AnyQualifier1 
      @AnyQualifier2
      interface ContributingInterface : ParentInterface
      """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "Classes annotated with @ContributesBinding may not use more than one @Qualifier.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type - 2 interfaces`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : ParentInterface, CharSequence
      """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesBinding annotation.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type - class and interface`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface
      
      open class Abc
      
      @ContributesBinding(Any::class)
      interface ContributingInterface : Abc(), ParentInterface
      """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesBinding annotation.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type - no super type`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      @ContributesBinding(Any::class)
      object ContributingInterface
      """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesBinding annotation.",
        )
      }
    }

  @TestFactory fun `the bound type is not implied when explicitly defined`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Int::class, ParentInterface::class)
      interface ContributingInterface : ParentInterface, CharSequence
      """,
    ) {
      assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.bindingModuleScope).isEqualTo(Int::class)
    }
  }

  @TestFactory fun `the contributed binding class must extend the bound type`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class, ParentInterface::class)
      interface ContributingInterface : CharSequence
      """,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding for " +
          "com.squareup.test.ParentInterface, but doesn't extend this type.",
      )
    }
  }

  @TestFactory fun `the contributed binding class can extend Any explicitly`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.MergeComponent

      @ContributesBinding(Int::class, boundType = Any::class)
      interface ContributingInterface

      @MergeComponent(
        scope = Int::class,
      )
      interface ComponentInterface
      """,
    ) {
      assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.bindingModuleScope).isEqualTo(Int::class)
    }
  }

  @TestFactory fun `there are multiple hints for multiple contributed bindings`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Unit::class)
      class ContributingInterface : ParentInterface
      
      @ContributesBinding(Unit::class)
      @ContributesBinding(Any::class)
      class SecondContributingInterface : ParentInterface
      """,
    ) {
      assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
      assertThat(contributingInterface.bindingModuleScopes).containsExactly(Any::class, Unit::class)

      assertFileGenerated(
        mode,
        "ContributingInterface_ParentInterface_Any_BindingModule_68088f18.kt",
      )
      assertFileGenerated(
        mode,
        "ContributingInterface_ParentInterface_Unit_BindingModule_0c1e79f9.kt",
      )

      assertThat(
        secondContributingInterface.bindingOriginKClass?.java,
      ).isEqualTo(secondContributingInterface)
      assertThat(
        secondContributingInterface.bindingModuleScopes,
      ).containsExactly(Any::class, Unit::class)
      assertFileGenerated(
        mode,
        "SecondContributingInterface_ParentInterface_Any_BindingModule_bbbec9d2.kt",
      )
      assertFileGenerated(
        mode,
        "SecondContributingInterface_ParentInterface_Unit_BindingModule_75c5914b.kt",
      )
    }
  }

  @TestFactory fun `there are multiple hints for contributed bindings with fully qualified names`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      
      interface ParentInterface

      @ContributesBinding(Any::class)
      @com.squareup.anvil.annotations.ContributesBinding(Unit::class)
      class ContributingInterface : ParentInterface
      """,
      ) {
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(contributingInterface.bindingModuleScopes).containsExactly(
          Any::class,
          Unit::class,
        )
      }
    }

  @TestFactory fun `multiple annotations with the same scope and bound type aren't allowed`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface

      @ContributesBinding(Any::class)
      @ContributesBinding(Any::class, replaces = [Int::class])
      @ContributesBinding(Unit::class)
      @ContributesBinding(Unit::class, replaces = [Int::class])
      class ContributingInterface : ParentInterface
      """,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes multiple times to the same scope " +
            "using the same bound type: [ParentInterface]. Contributing multiple times to the " +
            "same scope with the same bound type is forbidden and all scope - bound type " +
            "combinations must be distinct.",
        )
      }
    }

  @TestFactory fun `multiple annotations with the same scope and different bound type are allowed`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface1
      interface ParentInterface2

      @ContributesBinding(Any::class, boundType = ParentInterface1::class)
      @ContributesBinding(Any::class, boundType = ParentInterface2::class)
      @ContributesBinding(Unit::class, boundType = ParentInterface1::class)
      @ContributesBinding(Unit::class, boundType = ParentInterface2::class)
      class ContributingInterface : ParentInterface1, ParentInterface2
      """,
      ) {
        assertThat(contributingInterface.bindingOriginKClass?.java).isEqualTo(contributingInterface)
        assertThat(
          contributingInterface.bindingModuleScopes.toSet(),
        ).containsExactly(Any::class, Unit::class)
      }
    }

  @TestFactory fun `legacy priority is correctly propagated`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding

      interface ParentInterface1
      interface ParentInterface2

      @ContributesBinding(Any::class, boundType = ParentInterface1::class) // Default case is NORMAL
      @ContributesBinding(Any::class, boundType = ParentInterface2::class, priority = ContributesBinding.Priority.NORMAL)
      @ContributesBinding(Unit::class, boundType = ParentInterface1::class, priority = ContributesBinding.Priority.HIGH)
      @ContributesBinding(Unit::class, boundType = ParentInterface2::class, priority = ContributesBinding.Priority.HIGHEST)
      class ContributingInterface : ParentInterface1, ParentInterface2
      """,
      mode = mode,
      allWarningsAsErrors = false,
    ) {
      val bindingModules = contributingInterface.generatedBindingModules()
        .associate { clazz ->
          val bindingMarker = clazz.getAnnotation(InternalBindingMarker::class.java)
          clazz.simpleName to bindingMarker.rank
        }
      assertThat(
        bindingModules["ContributingInterface_ParentInterface1_Any_BindingModule_141ba678"],
      ).isEqualTo(ContributesBinding.RANK_NORMAL)
      assertThat(
        bindingModules["ContributingInterface_ParentInterface2_Any_BindingModule_384535e9"],
      ).isEqualTo(ContributesBinding.RANK_NORMAL)
      assertThat(
        bindingModules["ContributingInterface_ParentInterface1_Unit_BindingModule_0474bc27"],
      ).isEqualTo(ContributesBinding.RANK_HIGH)
      assertThat(
        bindingModules["ContributingInterface_ParentInterface2_Unit_BindingModule_0abbde43"],
      ).isEqualTo(ContributesBinding.RANK_HIGHEST)
    }
  }

  @TestFactory
  fun `a provider factory is still generated IFF no normal Dagger factory generation is enabled`() =
    params.withFactorySource { _, source ->

      // https://github.com/square/anvil/issues/948

      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeSubcomponent
      import javax.inject.Inject

      interface ParentInterface

      @ContributesBinding(Unit::class)
      object ContributingObject : ParentInterface
      """,
        enableDaggerAnnotationProcessor = source == DaggerFactorySource.DAGGER,
        generateDaggerFactories = source == DaggerFactorySource.ANVIL,
      ) {
        assertCompilationSucceeded()

        contributingObject.generatedBindingModule
          .moduleFactoryClass()
          .getDeclaredMethod("get")
          .returnType shouldBe parentInterface
      }
    }

  enum class DaggerFactorySource {
    DAGGER,
    ANVIL,
    NONE,
  }

  private inline fun List<Kase1<AnvilCompilationMode>>.withFactorySource(
    crossinline testAction: suspend AnvilCompilationModeTestEnvironment.(
      compilationMode: AnvilCompilationMode,
      factoryGenMode: DaggerFactorySource,
    ) -> Unit,
  ): Stream<out DynamicNode> = asContainers { modeKase ->
    DaggerFactorySource.entries.asTests(
      testEnvironmentFactory = AnvilCompilationModeTestEnvironment.wrap(modeKase),
      testName = { "factory source: ${it.name.lowercase()}" },
      testAction = { testAction(mode, it) },
    )
  }
}
