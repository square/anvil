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
import io.kotest.assertions.asClue
import io.kotest.assertions.collectiveError
import io.kotest.assertions.errorCollector
import io.kotest.assertions.pushErrors
import io.kotest.assertions.throwCollectedErrors
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
  LanguageInjection<File> by LanguageInjection(JavaFileFileInjection()),
  ReferenceAsserts {

  lateinit var referenceMap: Map<String, TypeReference>
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

              val refsContainer = classes.singleOrNull { it.shortName == "RefsContainer" }
                ?: error {
                  "RefsContainer not found.  Existing types: ${classes.map { it.shortName }}"
                }

              val refsFun = when (referenceType) {
                ReferenceType.Psi -> refsContainer.functions
                ReferenceType.Descriptor -> refsContainer.toDescriptorReference().functions
              }
                .singleOrNull { it.name == "refs" }
                ?: error {
                  "RefsContainer.refs not found.  " +
                    "Existing functions: ${refsContainer.functions.map { it.name }}"
                }

              referenceMap = when (referenceType) {
                ReferenceType.Psi -> refsFun.parameters.associate { it.name to it.type() }
                ReferenceType.Descriptor -> refsFun.parameters.associate { it.name to it.type() }
              }

              testAction()
            }

            return emptyList()
          }
        },
      ),
    ) {
      errorCollector.throwCollectedErrors()

      exitCode shouldBe KotlinCompilation.ExitCode.OK
    }
  }

  fun compile(
    @Language("kotlin") vararg content: String,
    assignable: Boolean,
    assigned: String,
    assignedTo: String,
    allWarningsAsErrors: Boolean = false,
    testAction: AssignableTestAction,
  ) {

    fun String.paramName(): String {
      return substringAfterLast('.')
        .replace(" ", "")
        .replace("?", "_nullable_")
        .replace("*", "STAR")
        .replace(Regex("[<,>]"), "_")
    }

    val assignedName = assigned.paramName()
    val assignedToName = assignedTo.paramName()
      .let { if (it == assignedName) it + "To" else it }

    val extra = """
      |package com.squareup.test
      |
      |interface RefsContainer {
      |  fun refs(
      |    $assignedName: $assigned,
      |    $assignedToName: $assignedTo
      |  ): $assignedTo = $assignedName
      |}
    """.trimMargin()

    compile(
      *content,
      extra,
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

              val refsContainer = classes.singleOrNull { it.shortName == "RefsContainer" }
                ?: error {
                  "RefsContainer not found.  Existing types: ${classes.map { it.shortName }}"
                }

              val refsFun = when (referenceType) {
                ReferenceType.Psi -> refsContainer.functions
                ReferenceType.Descriptor -> refsContainer.toDescriptorReference().functions
              }
                .singleOrNull { it.name == "refs" }
                ?: error {
                  "RefsContainer.refs not found.  " +
                    "Existing functions: ${refsContainer.functions.map { it.name }}"
                }

              val referenceMap = when (referenceType) {
                ReferenceType.Psi -> refsFun.parameters.associate { it.name to it.type() }
                ReferenceType.Descriptor -> refsFun.parameters.associate { it.name to it.type() }
              }

              testAction(
                assignable = assignable,
                assigned = referenceMap.getValue(assignedName),
                assignedTo = referenceMap.getValue(assignedToName),
              )
            }

            return emptyList()
          }
        },
      ),
    ) {

      messages.asClue {
        if (assignable) {
          exitCode shouldBe KotlinCompilation.ExitCode.OK
        } else {
          messages shouldContain "inferred type is $assigned but $assignedTo was expected"
          exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        }
      }

      errorCollector.throwCollectedErrors()
    }
  }

  fun interface AssignableTestAction {
    operator fun invoke(
      assignable: Boolean,
      assigned: TypeReference,
      assignedTo: TypeReference,
    )
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
          name.replace("?", "__nullable_")
        },
        testLocation = location,
      ),
    )
  }
}

inline fun ErrorCollector.collectErrors(assertions: () -> Unit) {
  val errorCollector = this

  // Handle the edge case of nested calls to this function by only calling throwCollectedErrors in the
  // outermost verifyAll block
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
