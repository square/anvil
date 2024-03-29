package com.squareup.anvil.compiler.internal.reference

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.JavaFileFileInjection
import com.rickbusarow.kase.files.LanguageInjection
import com.rickbusarow.kase.files.TestLocation
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.FileWithContent
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.reference.ReferencesTestEnvironment.ReferenceType
import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.assertions.ErrorCollectionMode
import io.kotest.assertions.ErrorCollector
import io.kotest.assertions.collectiveError
import io.kotest.assertions.errorCollector
import io.kotest.assertions.pushErrors
import io.kotest.assertions.throwCollectedErrors
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KProperty

interface ReferenceTests :
  KaseTestFactory<ReferenceType, ReferencesTestEnvironment, ReferencesTestEnvironment.Factory> {

  override val testEnvironmentFactory get() = ReferencesTestEnvironment.Factory

  override val params: List<ReferenceType>
    get() = listOf(ReferenceType.Psi, ReferenceType.Descriptor)
}

class ReferencesTestEnvironment(
  val referenceType: ReferenceType,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir = hasWorkingDir),
  LanguageInjection<File> by LanguageInjection(JavaFileFileInjection()) {

  lateinit var typeReferenceMap: Map<String, TypeReference>
    private set

  lateinit var classReferenceMap: Map<String, ClassReference>
    private set

  operator fun <E : FunctionReference> List<E>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): E = single { it.name == property.name }

  operator fun <E> Map<String, E>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): E = getValue(property.name)

  fun compile(
    @Language("kotlin") content: String,
    @Language("kotlin") vararg additionalContent: String,
    allWarningsAsErrors: Boolean = false,
    testAction: CodeGenerator.() -> Unit,
  ) {

    compile(
      content,
      *additionalContent,
      allWarningsAsErrors = allWarningsAsErrors,
      workingDir = workingDir,
      codeGenerators = listOf(
        object : CodeGenerator {
          override fun isApplicable(context: AnvilContext): Boolean = true
          override fun generateCode(
            codeGenDir: File,
            module: org.jetbrains.kotlin.descriptors.ModuleDescriptor,
            projectFiles: Collection<KtFile>,
          ): Collection<FileWithContent> {

            errorCollector.collectErrors {
              val classes = projectFiles.classAndInnerClassReferences(module)

              classReferenceMap = classes.associateBy { it.shortName }

              classes.singleOrNull { it.shortName == "RefsContainer" }
                ?.let { refsContainer ->

                  val refsFun = when (referenceType) {
                    ReferenceType.Psi -> refsContainer.functions
                    ReferenceType.Descriptor -> refsContainer.toDescriptorReference().functions
                  }
                    .singleOrNull { it.name == "refs" }
                    ?: error {
                      "RefsContainer.refs not found.  " +
                        "Existing functions: ${refsContainer.functions.map { it.name }}"
                    }

                  typeReferenceMap = when (referenceType) {
                    ReferenceType.Psi -> refsFun.parameters.associate { it.name to it.type() }
                    ReferenceType.Descriptor -> refsFun.parameters.associate { it.name to it.type() }
                  }
                }
            }

            testAction()

            return emptyList()
          }
        },
      ),
    ) {
      errorCollector.throwCollectedErrors()

      exitCode shouldBe KotlinCompilation.ExitCode.OK
    }
  }

  enum class ReferenceType {
    Psi,
    Descriptor,
  }

  companion object Factory : ParamTestEnvironmentFactory<ReferenceType, ReferencesTestEnvironment> {

    private fun readResolve(): Any = Factory
    override fun create(
      params: ReferenceType,
      names: List<String>,
      location: TestLocation,
    ): ReferencesTestEnvironment = ReferencesTestEnvironment(
      referenceType = params,
      HasWorkingDir.invoke(
        testVariantNames = names.map { name ->
          // Any '?' characters are replaced with '_' for working directory names,
          // so we preempt that with '__nullable_'
          // so that tests that use type names still get unique working directories.
          name.replace("?", "__nullable_")
        },
        testLocation = location,
      ),
    )
  }
}

inline fun ErrorCollector.collectErrors(assertions: () -> Unit) {
  val errorCollector = this

  if (errorCollector.getCollectionMode() == ErrorCollectionMode.Soft) {
    val oldErrors = errorCollector.errors()
    errorCollector.clear()
    errorCollector.depth++

    return try {
      assertions()
    } catch (t: Throwable) {
      errorCollector.pushError(t)
    } finally {
      val aggregated = errorCollector.collectiveError()
      errorCollector.clear()
      errorCollector.pushErrors(oldErrors)
      aggregated?.let { errorCollector.pushError(it) }
      errorCollector.depth--
    }
  }

  errorCollector.setCollectionMode(ErrorCollectionMode.Soft)
  return try {
    assertions()
  } catch (t: Throwable) {
    errorCollector.pushError(t)
  } finally {
    errorCollector.setCollectionMode(ErrorCollectionMode.Hard)
  }
}
