package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.wrap
import com.squareup.anvil.compiler.assertCompilationSucceeded
import com.squareup.anvil.compiler.assertFileGenerated
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.contributingObject
import com.squareup.anvil.compiler.generatedMultiBindingModule
import com.squareup.anvil.compiler.injectClass
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.moduleFactoryClass
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.multibindingModuleScope
import com.squareup.anvil.compiler.multibindingModuleScopes
import com.squareup.anvil.compiler.multibindingOriginClass
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTest
import com.squareup.anvil.compiler.testing.AnvilCompilationModeTestEnvironment
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import dagger.multibindings.StringKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

@Suppress("RemoveRedundantQualifierName", "RedundantSuppression")
class ContributesMultibindingGeneratorTest : AnvilCompilationModeTest(
  AnvilCompilationMode.Embedded(),
) {

  @TestFactory fun `there is a binding module for a contributed multibinding for interfaces`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)

        assertFileGenerated(
          mode,
          "ContributingInterface_ParentInterface_Any_MultiBindingModule_612ae703.kt",
        )
      }
    }

  @TestFactory fun `there is a binding module for a contributed multibinding for classes`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory fun `there is a binding module for a contributed multibinding for an object`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory fun `the order of the scope can be changed with named parameters`() = testFactory {
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

  @TestFactory fun `there is a binding module for a contributed multibinding for inner interfaces`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Any::class)
      }
    }

  @TestFactory fun `there is a binding module for a contributed multibinding for inner classes`() =
    testFactory {
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
        assertThat(contributingClass.multibindingOriginClass?.java).isEqualTo(contributingClass)
        assertThat(contributingClass.multibindingModuleScope).isEqualTo(Any::class)

        assertFileGenerated(
          mode,
          "ContributingClass_ParentInterface_Any_MultiBindingModule_16a2d7f6.kt",
        )
      }
    }

  @TestFactory fun `contributed multibinding class must be public`() = testFactory {
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

  @TestFactory
  fun `a StringKey annotation using a top-level const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesMultibinding
        import dagger.multibindings.StringKey
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
  
        interface ParentInterface
  
        private const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
  
        @StringKey(CONSTANT)
        @ContributesMultibinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
        mode = mode,
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val stringKey = injectClass.generatedMultiBindingModule.methods.single()
          .getDeclaredAnnotation(StringKey::class.java)

        assertThat(stringKey.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory
  fun `a StringKey annotation using an object's const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesMultibinding
        import dagger.multibindings.StringKey
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
  
        private object Constants {
          const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
        }
  
        interface ParentInterface
  
        @StringKey(Constants.CONSTANT)
        @ContributesMultibinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
        mode = mode,
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val stringKey = injectClass.generatedMultiBindingModule.methods.single()
          .getDeclaredAnnotation(StringKey::class.java)

        assertThat(stringKey.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory
  fun `a StringKey annotation using a companion object's const property is inlined in the generated module`() =
    testFactory {

      // https://github.com/square/anvil/issues/938

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesMultibinding
        import dagger.multibindings.StringKey
        import com.squareup.test.other.OTHER_CONSTANT
        import javax.inject.Inject
  
        private interface Settings {
          companion object {
            const val CONSTANT = "${'$'}OTHER_CONSTANT.foo"
          }
        }
  
        interface ParentInterface
  
        @StringKey(Settings.CONSTANT)
        @ContributesMultibinding(Any::class)
        class InjectClass @Inject constructor() : ParentInterface
        """,
        """
        package com.squareup.test.other
        
        const val OTHER_CONSTANT = "abc"
        """.trimIndent(),
        mode = mode,
      ) {

        assertThat(exitCode).isEqualTo(OK)

        val stringKey = injectClass.generatedMultiBindingModule.methods.single()
          .getDeclaredAnnotation(StringKey::class.java)

        assertThat(stringKey.value).isEqualTo("abc.foo")
      }
    }

  @TestFactory fun `contributed multibindings aren't allowed to have more than one qualifier`() =
    testFactory {
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
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "Classes annotated with @ContributesMultibinding may not use more than one @Qualifier.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type (2 interfaces)`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      interface ContributingInterface : ParentInterface, CharSequence
      """,
        mode = mode,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesMultibinding annotation.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type (class and interface)`() =
    testFactory {
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
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesMultibinding annotation.",
        )
      }
    }

  @TestFactory fun `the bound type can only be implied with one super type (no super type)`() =
    testFactory {
      compile(
        """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      @ContributesMultibinding(Any::class)
      object ContributingInterface
      """,
        mode = mode,
        expectExitCode = ExitCode.COMPILATION_ERROR,
      ) {
        assertThat(messages).contains(
          "com.squareup.test.ContributingInterface contributes a binding, but does not specify " +
            "the bound type. This is only allowed with exactly one direct super type. If there " +
            "are multiple or none, then the bound type must be explicitly defined in the " +
            "@ContributesMultibinding annotation.",
        )
      }
    }

  @TestFactory fun `duplicate binding checks consider ignoreQualifier`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import com.squareup.anvil.annotations.MergeComponent
      import javax.inject.Inject
      import javax.inject.Named

      interface ParentInterface

      @Named("test")
      @ContributesMultibinding(Int::class, ParentInterface::class)
      @ContributesMultibinding(Int::class, ParentInterface::class, ignoreQualifier = true)
      class ContributingInterface @Inject constructor() : ParentInterface

      @MergeComponent(Int::class)
      interface ComponentInterface {
        fun injectClass(): InjectClass
      }

      class InjectClass @Inject constructor(
        @Named("test") val qualified : Set<@JvmSuppressWildcards ParentInterface>,
        val unqualified : Set<@JvmSuppressWildcards ParentInterface>
      )
      """,
      mode = mode,
    ) {
      assertThat(contributingInterface.multibindingOriginClass?.java)
        .isEqualTo(contributingInterface)

      assertThat(contributingInterface.multibindingModuleScopes)
        .containsExactly(Int::class, Int::class)
    }
  }

  @TestFactory fun `the bound type is not implied when explicitly defined`() = testFactory {
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
      assertThat(
        contributingInterface.multibindingOriginClass?.java,
      ).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Int::class)
    }
  }

  @TestFactory fun `the contributed multibinding class must extend the bound type`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      interface ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      interface ContributingInterface : CharSequence
      """,
      mode = mode,
      expectExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "com.squareup.test.ContributingInterface contributes a binding for " +
          "com.squareup.test.ParentInterface, but doesn't extend this type.",
      )
    }
  }

  @TestFactory fun `the contributed multibinding class can extend Any explicitly`() = testFactory {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding

      @ContributesMultibinding(Int::class, boundType = Any::class)
      interface ContributingInterface
      """,
      mode = mode,
    ) {
      assertThat(
        contributingInterface.multibindingOriginClass?.java,
      ).isEqualTo(contributingInterface)
      assertThat(contributingInterface.multibindingModuleScope).isEqualTo(Int::class)
    }
  }

  @TestFactory fun `a contributed multibinding can be generated`() = testFactory {
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

  @TestFactory fun `a contributed multibinding can be generated with map keys being generated`() =
    testFactory {
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

  @TestFactory fun `there are multiple hints for multiple contributed multibindings`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScopes)
          .containsExactly(Any::class, Unit::class)
      }
    }

  @TestFactory fun `there are multiple hints for contributed multibindings with fully qualified names`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScopes)
          .containsExactly(Any::class, Unit::class)
      }
    }

  @TestFactory fun `multiple annotations with the same scope and bound type aren't allowed`() =
    testFactory {
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
        assertThat(
          contributingInterface.multibindingOriginClass?.java,
        ).isEqualTo(contributingInterface)
        assertThat(contributingInterface.multibindingModuleScopes.toSet())
          .containsExactly(Any::class, Unit::class)
      }
    }

  @TestFactory
  fun `a provider factory is still generated IFF no normal Dagger factory generation is enabled`() =
    params.withFactorySource { _, source ->

      // https://github.com/square/anvil/issues/948

      compile(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesMultibinding
        import com.squareup.anvil.annotations.ContributesTo
        import com.squareup.anvil.annotations.MergeSubcomponent
        import javax.inject.Inject
  
        interface ParentInterface
  
        @ContributesMultibinding(Unit::class)
        object ContributingObject : ParentInterface
        """,
        enableDaggerAnnotationProcessor = source == DaggerFactorySource.DAGGER,
        generateDaggerFactories = source == DaggerFactorySource.ANVIL,
      ) {
        assertCompilationSucceeded()

        contributingObject.generatedMultiBindingModule
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
