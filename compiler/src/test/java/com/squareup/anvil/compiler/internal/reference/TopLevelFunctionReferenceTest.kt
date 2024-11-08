package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode.OK
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.source.getPsi
import org.junit.Test
import java.io.File

class TopLevelFunctionReferenceTest {

  @Test fun `top level functions are parsed correctly`() {
    functionTest(
      """
      package com.squareup.test

      private fun abc() = Unit
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.abc"))
      assertThat(ref.parameters).isEmpty()
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.visibility()).isEqualTo(PRIVATE)
      when (ref) {
        is TopLevelFunctionReference.Psi ->
          assertThat(ref.returnTypeOrNull()).isNull()

        is TopLevelFunctionReference.Descriptor ->
          assertThat(ref.returnType().asClassReference().fqName.asString())
            .isEqualTo("kotlin.Unit")
      }
    }
  }

  @Test fun `top level functions parameters and types are parsed correctly`() {
    functionTest(
      """
      package com.squareup.test

      fun abc(string: String): String = string
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.abc"))
      assertThat(ref.parameters.single().type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.returnType().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
    }
  }

  @Test fun `top level functions annotations are parsed correctly`() {
    functionTest(
      """
      package com.squareup.test

      @PublishedApi
      internal fun abc(): Int? = null
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.abc"))
      assertThat(ref.parameters).isEmpty()
      assertThat(ref.annotations.single().fqName.asString())
        .isEqualTo("kotlin.PublishedApi")
      assertThat(ref.visibility()).isEqualTo(INTERNAL)
      assertThat(ref.returnType().asClassReference().fqName.asString())
        .isEqualTo("kotlin.Int")
      assertThat(ref.returnType().isNullable()).isTrue()
    }
  }

  @Test fun `top level functions generic return types are parsed correctly`() {
    functionTest(
      """
      package com.squareup.test

      fun <T : CharSequence> abc(): T = throw NotImplementedError()
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.abc"))
      assertThat(ref.parameters).isEmpty()
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.returnType().isGenericType()).isTrue()
    }
  }

  @Test fun `top level functions generic and lambda parameters are parsed correctly`() {
    functionTest(
      """
      package com.squareup.test

      fun <T : CharSequence> abc(param1: T, param2: () -> T): T? = null
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.abc"))
      assertThat(ref.parameters).hasSize(2)
      assertThat(ref.parameters[0].type().isGenericType()).isTrue()
      assertThat(ref.parameters[1].type().isFunctionType()).isTrue()
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.returnType().isGenericType()).isTrue()
      assertThat(ref.returnType().isNullable()).isTrue()
    }
  }

  private fun functionTest(
    @Language("kotlin") vararg sources: String,
    assert: (TopLevelFunctionReference) -> Unit,
  ) {
    compile(
      *sources,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        object : CodeGenerator {
          override fun isApplicable(context: AnvilContext): Boolean = true

          override fun generateCode(
            codeGenDir: File,
            module: ModuleDescriptor,
            projectFiles: Collection<KtFile>,
          ): Collection<FileWithContent> {
            projectFiles
              .topLevelFunctionReferences(module)
              .flatMap { listOf(it.toPsiReference(), it.toDescriptorReference()) }
              .forEach(assert)

            return emptyList()
          }
        },
      ),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}

fun TopLevelFunctionReference.toDescriptorReference(): TopLevelFunctionReference.Descriptor {
  return when (this) {
    is TopLevelFunctionReference.Descriptor -> this
    is TopLevelFunctionReference.Psi -> {
      // Force using the descriptor.
      module.getPackage(fqName.parent()).memberScope
        .getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)
        .filterIsInstance<FunctionDescriptor>()
        .single { it.name.asString() == name }
        .toTopLevelFunctionReference(module)
        .also { descriptorReference ->
          assertThat(descriptorReference)
            .isInstanceOf(TopLevelFunctionReference.Descriptor::class.java)

          assertThat(this).isEqualTo(descriptorReference)
          assertThat(this.fqName).isEqualTo(descriptorReference.fqName)
        }
    }
  }
}

fun TopLevelFunctionReference.toPsiReference(): TopLevelFunctionReference.Psi {
  return when (this) {
    is TopLevelFunctionReference.Psi -> this
    is TopLevelFunctionReference.Descriptor -> {
      // Force using Psi.
      (function.source.getPsi() as KtFunction).toTopLevelFunctionReference(module)
        .also { psiReference ->
          assertThat(psiReference).isInstanceOf(TopLevelFunctionReference.Psi::class.java)

          assertThat(this).isEqualTo(psiReference)
          assertThat(this.fqName).isEqualTo(psiReference.fqName)
        }
    }
  }
}
