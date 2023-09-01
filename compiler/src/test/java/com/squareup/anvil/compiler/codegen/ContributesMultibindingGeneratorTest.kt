package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.containingFile
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.compiler.codegen.ksp.SimpleMapperResult
import com.squareup.anvil.compiler.codegen.ksp.simpleSymbolProcessor
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.hintMultibinding
import com.squareup.anvil.compiler.hintMultibindingScope
import com.squareup.anvil.compiler.hintMultibindingScopes
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.secondContributingInterface
import com.squareup.anvil.compiler.walkGeneratedFiles
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("RemoveRedundantQualifierName")
@RunWith(Parameterized::class)
class ContributesMultibindingGeneratorTest(
  private val mode: AnvilCompilationMode
) {

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic fun modes(): Collection<Any> {
      return buildList {
        add(AnvilCompilationMode.Embedded())
        add(AnvilCompilationMode.Ksp())
      }
    }
  }

  @Test fun `a contributed multibinding can be generated`() {
    val stubContentToGenerate =
      //language=kotlin
      """
          package com.squareup.test

          import com.squareup.anvil.annotations.ContributesMultibinding

          @ContributesMultibinding(Any::class)
          @BindingKey("abc")
          interface ContributingInterface : ParentInterface
      """.trimIndent()

    val localMode = when (mode) {
      is AnvilCompilationMode.Embedded -> {
        val codeGenerator = simpleCodeGenerator { clazz ->
          clazz
            .takeIf { it.isAnnotatedWith(contributesBindingFqName) }
            ?.let { stubContentToGenerate }
        }
        AnvilCompilationMode.Embedded(listOf(codeGenerator))
      }

      is AnvilCompilationMode.Ksp -> {
        val processor = simpleSymbolProcessor { resolver, env ->
          env.logger.info("Simple processor ran")
          resolver.getSymbolsWithAnnotation(contributesBindingFqName.toString())
            .map { clazz ->
              SimpleMapperResult(
                content = stubContentToGenerate,
                originatingFile = clazz.containingFile
              )
            }
            .toList()
        }
        AnvilCompilationMode.Ksp(listOf(processor))
      }
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import dagger.MapKey
        
        @MapKey
        annotation class BindingKey(val value: String)
        
        interface ParentInterface

        interface OtherInterface
  
        @ContributesBinding(Any::class)
        interface ComponentInterface : OtherInterface
      """,
      mode = localMode
    ) {
      assertThat(exitCode).isEqualTo(OK)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }

  @Test fun `a contributed multibinding can be generated with map keys being generated`() {
    val stubContentToGenerate =
      //language=kotlin
      """
          package com.squareup.test

          import com.squareup.anvil.annotations.ContributesMultibinding
          import dagger.MapKey

          interface ParentInterface

          @MapKey
          annotation class BindingKey1(val value: String)
      
          @ContributesMultibinding(Any::class)
          @BindingKey1("abc")
          interface ContributingInterface : ParentInterface
      """.trimIndent()

    val localMode = when (mode) {
      is AnvilCompilationMode.Embedded -> {
        val codeGenerator = simpleCodeGenerator { clazz ->
          clazz
            .takeIf { it.isAnnotatedWith(contributesBindingFqName) }
            ?.let {
              stubContentToGenerate
            }
        }
        AnvilCompilationMode.Embedded(listOf(codeGenerator))
      }

      is AnvilCompilationMode.Ksp -> {
        val processor = simpleSymbolProcessor { resolver, env ->
          env.logger.info("Simple processor ran")
          resolver.getSymbolsWithAnnotation(contributesBindingFqName.toString())
            .map { clazz ->
              SimpleMapperResult(
                content = stubContentToGenerate,
                originatingFile = clazz.containingFile
              )
            }
            .toList()
        }
        AnvilCompilationMode.Ksp(listOf(processor))
      }
    }

    compile(
      """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        
        interface OtherInterface
  
        @ContributesBinding(Any::class)
        interface ComponentInterface : OtherInterface
      """,
      mode = localMode
    ) {
      assertThat(exitCode).isEqualTo(OK)
      assertThat(contributingInterface.hintMultibindingScope).isEqualTo(Any::class)
    }
  }
}
