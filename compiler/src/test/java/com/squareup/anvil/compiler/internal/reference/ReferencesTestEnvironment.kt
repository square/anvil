package com.squareup.anvil.compiler.internal.reference

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.JavaFileFileInjection
import com.rickbusarow.kase.files.LanguageInjection
import com.rickbusarow.kase.files.TestLocation
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.assertCompilationSucceeded
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.reference.ReferencesTestEnvironment.ReferenceType
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import io.kotest.assertions.ErrorCollectionMode
import io.kotest.assertions.ErrorCollector
import io.kotest.assertions.collectiveError
import io.kotest.assertions.errorCollector
import io.kotest.assertions.pushErrors
import io.kotest.assertions.throwCollectedErrors
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

  operator fun <E> Map<String, E>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): E = getValue(property.name)

  operator fun <E : PropertyReference> Iterable<E>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): E = singleOrNull { it.name == property.name }
    ?: error("PropertyReference not found: ${property.name}")

  operator fun <E : FunctionReference> Iterable<E>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): E = singleOrNull { it.name == property.name }
    ?: error("FunctionReference not found: ${property.name}")

  class ReferencesTestCodeGenerationResult(
    val typeReferences: Map<String, TypeReference>,
    val classReferences: Map<String, ClassReference>,
    val module: RealAnvilModuleDescriptor,
    val projectFiles: Collection<KtFile>,
  )

  fun compile(
    @Language("kotlin") content: String,
    @Language("kotlin") vararg additionalContent: String,
    allWarningsAsErrors: Boolean = false,
    previousCompilationResult: JvmCompilationResult? = null,
    codeGenerators: List<CodeGenerator> = emptyList(),
    expectExitCode: KotlinCompilation.ExitCode? = null,
    testAction: ReferencesTestCodeGenerationResult.() -> Unit,
  ) {

    val typeReferences = mutableMapOf<String, TypeReference>()
    val classReferences = mutableMapOf<String, ClassReference>()

    val referenceGenerator = simpleCodeGenerator { _, module, projectFiles ->

      errorCollector.collectErrors {
        val classes = projectFiles.classAndInnerClassReferences(module)

        classReferences.putAll(classes.associateBy { it.shortName })

        classes.singleOrNull { it.shortName == "RefsContainer" }
          ?.let { refsContainer ->

            val refsFun = when (referenceType) {
              ReferenceType.Psi -> refsContainer.declaredMemberFunctions
              ReferenceType.Descriptor -> refsContainer.toDescriptorReference().declaredMemberFunctions
            }
              .singleOrNull { it.name == "refs" }
              ?: error {
                "RefsContainer.refs not found.  " +
                  "Existing functions: ${refsContainer.declaredMemberFunctions.map { it.name }}"
              }

            when (referenceType) {
              ReferenceType.Psi -> typeReferences.putAll(
                refsFun.parameters.associate { it.name to it.type() },
              )
              ReferenceType.Descriptor -> typeReferences.putAll(
                refsFun.parameters.associate { it.name to it.type() },
              )
            }
          }

        ReferencesTestCodeGenerationResult(
          typeReferences = typeReferences,
          classReferences = classReferences,
          module = module as RealAnvilModuleDescriptor,
          projectFiles = projectFiles,
        ).testAction()
      }

      emptyList()
    }

    compileAnvil(
      content,
      *additionalContent,
      allWarningsAsErrors = allWarningsAsErrors,
      workingDir = workingDir,
      previousCompilationResult = previousCompilationResult,
      mode = AnvilCompilationMode.Embedded(codeGenerators + referenceGenerator),
      expectExitCode = expectExitCode,
    ) {
      errorCollector.throwCollectedErrors()

      assertCompilationSucceeded()
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

val MemberFunctionReference.text: String
  get() = when (this) {
    is MemberFunctionReference.Descriptor -> function.toString()
    is MemberFunctionReference.Psi -> function.text
  }
