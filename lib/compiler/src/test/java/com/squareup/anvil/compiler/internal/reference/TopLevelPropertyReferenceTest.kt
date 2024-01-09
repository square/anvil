package com.squareup.anvil.compiler.internal.reference

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.source.getPsi
import org.junit.Test
import java.io.File

class TopLevelPropertyReferenceTest {

  @Test fun `top level properties are parsed`() {
    propertyTest(
      """
      package com.squareup.test
      
      private val prop = Unit 
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop"))
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.getterAnnotations).isEmpty()
      assertThat(ref.setterAnnotations).isEmpty()
      assertThat(ref.isLateinit()).isFalse()
      assertThat(ref.visibility()).isEqualTo(PRIVATE)
      when (ref) {
        is TopLevelPropertyReference.Psi ->
          assertThat(ref.typeOrNull()).isNull()

        is TopLevelPropertyReference.Descriptor ->
          assertThat(ref.type().asClassReference().fqName.asString())
            .isEqualTo("kotlin.Unit")
      }
    }
  }

  @Test fun `for top level properties types are resolved`() {
    propertyTest(
      """
      package com.squareup.test
      
      var prop: String = "" 
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop"))
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.getterAnnotations).isEmpty()
      assertThat(ref.setterAnnotations).isEmpty()
      assertThat(ref.isLateinit()).isFalse()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
    }
  }

  @Test fun `for top level properties annotations are parsed`() {
    propertyTest(
      """
      package com.squareup.test
      
      @PublishedApi
      internal val prop: Int? = null 
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop"))
      assertThat(ref.annotations.single().fqName.asString())
        .isEqualTo("kotlin.PublishedApi")
      assertThat(ref.getterAnnotations).isEmpty()
      assertThat(ref.setterAnnotations).isEmpty()
      assertThat(ref.isLateinit()).isFalse()
      assertThat(ref.visibility()).isEqualTo(INTERNAL)
      assertThat(ref.type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.Int")
      assertThat(ref.type().isNullable()).isTrue()
    }
  }

  @Test fun `for top level properties setter annotations are parsed`() {
    propertyTest(
      """
      package com.squareup.test

      import javax.inject.Inject
      
      @set:Inject var prop: String = "" 
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop"))
      assertThat(ref.annotations.single().fqName.asString())
        .isEqualTo("javax.inject.Inject")
      assertThat(ref.getterAnnotations).isEmpty()
      assertThat(ref.setterAnnotations.single().fqName.asString())
        .isEqualTo("javax.inject.Inject")
      assertThat(ref.isLateinit()).isFalse()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
    }
  }

  @Test fun `for top level properties getter annotations are parsed`() {
    propertyTest(
      """
      package com.squareup.test

      import javax.inject.Inject
      
      @get:Inject var prop: String = "" 
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop"))
      assertThat(ref.annotations.single().fqName.asString())
        .isEqualTo("javax.inject.Inject")
      assertThat(ref.getterAnnotations.single().fqName.asString())
        .isEqualTo("javax.inject.Inject")
      assertThat(ref.setterAnnotations).isEmpty()
      assertThat(ref.isLateinit()).isFalse()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
    }
  }

  @Test fun `lateinit top level properties are parsed`() {
    propertyTest(
      """
      package com.squareup.test

      lateinit var prop6: String
      """,
    ) { ref ->
      assertThat(ref.fqName).isEqualTo(FqName("com.squareup.test.prop6"))
      assertThat(ref.annotations).isEmpty()
      assertThat(ref.getterAnnotations).isEmpty()
      assertThat(ref.setterAnnotations).isEmpty()
      assertThat(ref.isLateinit()).isTrue()
      assertThat(ref.visibility()).isEqualTo(PUBLIC)
      assertThat(ref.type().asClassReference().fqName.asString())
        .isEqualTo("kotlin.String")
    }
  }

  private fun propertyTest(
    @Language("kotlin") vararg sources: String,
    assert: (TopLevelPropertyReference) -> Unit,
  ) {
    compile(
      sources = sources,
      allWarningsAsErrors = false,
      codeGenerators = listOf(
        object : CodeGenerator {
          override fun isApplicable(context: AnvilContext): Boolean = true

          override fun generateCode(
            codeGenDir: File,
            module: ModuleDescriptor,
            projectFiles: Collection<KtFile>,
          ): Collection<GeneratedFile> {
            projectFiles
              .topLevelPropertyReferences(module)
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

fun TopLevelPropertyReference.toDescriptorReference(): TopLevelPropertyReference.Descriptor {
  return when (this) {
    is TopLevelPropertyReference.Descriptor -> this
    is TopLevelPropertyReference.Psi -> {
      // Force using the descriptor.
      module.getPackage(fqName.parent()).memberScope
        .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .single { it.name.asString() == name }
        .toTopLevelPropertyReference(module)
        .also { descriptorReference ->
          assertThat(descriptorReference)
            .isInstanceOf(TopLevelPropertyReference.Descriptor::class.java)

          assertThat(this).isEqualTo(descriptorReference)
          assertThat(this.fqName).isEqualTo(descriptorReference.fqName)
        }
    }
  }
}

fun TopLevelPropertyReference.toPsiReference(): TopLevelPropertyReference.Psi {
  return when (this) {
    is TopLevelPropertyReference.Psi -> this
    is TopLevelPropertyReference.Descriptor -> {
      // Force using Psi.
      (property.source.getPsi() as KtProperty).toTopLevelPropertyReference(module)
        .also { psiReference ->
          assertThat(psiReference).isInstanceOf(TopLevelPropertyReference.Psi::class.java)

          assertThat(this).isEqualTo(psiReference)
          assertThat(this.fqName).isEqualTo(psiReference.fqName)
        }
    }
  }
}
