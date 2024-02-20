package com.squareup.anvil.compiler

import com.google.common.collect.Lists.cartesianProduct
import com.google.common.truth.ComparableSubject
import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode.Embedded
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode.Ksp
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.generatedClassesString
import com.squareup.anvil.compiler.internal.testing.packageName
import com.squareup.anvil.compiler.internal.testing.use
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.INTERNAL_ERROR
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.fail

internal fun compile(
  @Language("kotlin") vararg sources: String,
  previousCompilationResult: JvmCompilationResult? = null,
  enableDaggerAnnotationProcessor: Boolean = false,
  trackSourceFiles: Boolean = true,
  codeGenerators: List<CodeGenerator> = emptyList(),
  allWarningsAsErrors: Boolean = WARNINGS_AS_ERRORS,
  mode: AnvilCompilationMode = AnvilCompilationMode.Embedded(codeGenerators),
  block: JvmCompilationResult.() -> Unit = { },
): JvmCompilationResult = compileAnvil(
  sources = sources,
  allWarningsAsErrors = allWarningsAsErrors,
  previousCompilationResult = previousCompilationResult,
  enableDaggerAnnotationProcessor = enableDaggerAnnotationProcessor,
  trackSourceFiles = trackSourceFiles,
  mode = mode,
  block = block,
)

internal val JvmCompilationResult.contributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ContributingInterface")

internal val JvmCompilationResult.secondContributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SecondContributingInterface")

internal val JvmCompilationResult.innerInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SomeClass\$InnerInterface")

internal val JvmCompilationResult.parentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface")

internal val JvmCompilationResult.parentInterface1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface1")

internal val JvmCompilationResult.parentInterface2: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface2")

internal val JvmCompilationResult.componentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface")

internal val JvmCompilationResult.subcomponentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface")

internal val JvmCompilationResult.daggerModule1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule1")

internal val JvmCompilationResult.assistedService: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedService")

internal val JvmCompilationResult.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedServiceFactory")

internal val JvmCompilationResult.daggerModule2: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule2")

internal val JvmCompilationResult.daggerModule3: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule3")

internal val JvmCompilationResult.daggerModule4: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule4")

internal val JvmCompilationResult.innerModule: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface\$InnerModule")

internal val JvmCompilationResult.nestedInjectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentClass\$NestedInjectClass")

internal val JvmCompilationResult.injectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.InjectClass")

internal val JvmCompilationResult.anyQualifier: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AnyQualifier")

@Suppress("UNCHECKED_CAST")
internal val JvmCompilationResult.bindingKey: Class<out Annotation>
  get() = classLoader.loadClass("com.squareup.test.BindingKey") as Class<out Annotation>

internal val Class<*>.hintContributes: KClass<*>?
  get() = getHint(HINT_CONTRIBUTES_PACKAGE_PREFIX)

internal val Class<*>.hintContributesScope: KClass<*>?
  get() = hintContributesScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintContributesScopes: List<KClass<*>>
  get() = getHintScopes(HINT_CONTRIBUTES_PACKAGE_PREFIX)

private fun Class<*>.generatedBindingModules(): List<Class<*>> {
  return getAnnotationsByType(ContributesBinding::class.java)
    .map { bindingAnnotation ->
      val scope = bindingAnnotation.scope.simpleName!!.capitalize()
      val boundType = bindingAnnotation.boundType
        .let {
          if (it == Unit::class) {
            interfaces.singleOrNull()?.kotlin ?: superclass.kotlin
          } else {
            it
          }
        }
        .simpleName!!.capitalize()
      val className = "${generatedClassesString()}As${boundType}To${scope}BindingModule"
      classLoader.loadClass(className)
    }
}

private fun Class<*>.generatedMultiBindingModules(): List<Class<*>> {
  return getAnnotationsByType(ContributesMultibinding::class.java)
    .map { bindingAnnotation ->
      val scope = bindingAnnotation.scope.simpleName!!.capitalize()
      val boundType = bindingAnnotation.boundType
        .let {
          if (it == Unit::class) {
            interfaces.singleOrNull()?.kotlin ?: superclass.kotlin
          } else {
            it
          }
        }
        .simpleName!!.capitalize()
      val className = "${generatedClassesString()}As${boundType}To${scope}MultiBindingModule"
      classLoader.loadClass(className)
    }
}

internal val Class<*>.bindingModule: KClass<*>?
  get() {
    val generatedBindingModule = generatedBindingModules().first()
    val bindingFunction = generatedBindingModule.declaredMethods[0]
    val implType = bindingFunction.parameterTypes[0]
    return implType.kotlin
  }

internal val Class<*>.bindingModuleScope: KClass<*>?
  get() = bindingModuleScopes.first()

internal val Class<*>.bindingModuleScopes: List<KClass<*>>
  get() = generatedBindingModules()
    .map { generatedBindingModule ->
      val contributesTo = generatedBindingModule.getAnnotation(ContributesTo::class.java)
      contributesTo.scope
    }

internal val Class<*>.multibindingModule: KClass<*>?
  get() {
    val generatedBindingModule = generatedMultiBindingModules().first()
    // TODO use kotlinx-metadata to read type args? Validate they match?
    val internalBindingMarker =
      generatedBindingModule.getAnnotation(InternalBindingMarker::class.java)
    val bindingFunction = generatedBindingModule.declaredMethods[0]
    val implType = bindingFunction.parameterTypes[0]
    return implType.kotlin
  }

// TODO remove
internal val Class<*>.multibindingModuleScope: KClass<*>?
  get() = multibindingModuleScopes.takeIf { it.isNotEmpty() }?.single()

// TODO remove
internal val Class<*>.multibindingModuleScopes: List<KClass<*>>
  get() = generatedMultiBindingModules()
    .map { generatedBindingModule ->
      val contributesTo = generatedBindingModule.getAnnotation(ContributesTo::class.java)
      contributesTo.scope
    }

internal val Class<*>.hintSubcomponent: KClass<*>?
  get() = getHint(HINT_SUBCOMPONENTS_PACKAGE_PREFIX)

internal val Class<*>.hintSubcomponentParentScope: KClass<*>?
  get() = hintSubcomponentParentScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintSubcomponentParentScopes: List<KClass<*>>
  get() = getHintScopes(HINT_SUBCOMPONENTS_PACKAGE_PREFIX)

internal val Class<*>.anvilModule: Class<*>
  get() = classLoader.loadClass(
    "$MODULE_PACKAGE_PREFIX.${generatedClassesString(separator = "")}$ANVIL_MODULE_SUFFIX",
  )

private fun Class<*>.getHint(prefix: String): KClass<*>? = contributedProperties(prefix)
  ?.filter { it.java == this }
  ?.also { assertThat(it.size).isEqualTo(1) }
  ?.first()

private fun Class<*>.getHintScopes(prefix: String): List<KClass<*>> =
  contributedProperties(prefix)
    ?.also { assertThat(it.size).isAtLeast(2) }
    ?.filter { it.java != this }
    ?: emptyList()

private fun Class<*>.contributedProperties(packagePrefix: String): List<KClass<*>>? {
  // The capitalize() doesn't make sense, I don't know where this is coming from. Maybe it's a
  // bug in the compile testing library?
  val className = generateSequence(this) { it.enclosingClass }
    .toList()
    .reversed()
    .joinToString(separator = "_") { it.simpleName }
    .capitalize() + "Kt"

  val clazz = try {
    classLoader.loadClass("$packagePrefix.${packageName()}$className")
  } catch (e: ClassNotFoundException) {
    return null
  }

  return clazz.declaredFields
    .sortedBy { it.name }
    .map { field -> field.use { it.get(null) } }
    .filterIsInstance<KClass<*>>()
}

internal fun assumeMergeComponent(annotationClass: KClass<*>) {
  assumeTrue(annotationClass == MergeComponent::class)
}

internal fun ComparableSubject<ExitCode>.isError() {
  isIn(setOf(COMPILATION_ERROR, INTERNAL_ERROR))
}

internal fun isFullTestRun(): Boolean = FULL_TEST_RUN
internal fun checkFullTestRun() = assumeTrue(isFullTestRun())
internal fun includeKspTests(): Boolean = INCLUDE_KSP_TESTS

internal fun JvmCompilationResult.walkGeneratedFiles(mode: AnvilCompilationMode): Sequence<File> {
  val dirToSearch = when (mode) {
    is AnvilCompilationMode.Embedded ->
      outputDirectory.parentFile.resolve("build${File.separator}anvil")

    is AnvilCompilationMode.Ksp -> outputDirectory.parentFile.resolve("ksp${File.separator}sources")
  }
  return dirToSearch.walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
}

internal fun JvmCompilationResult.singleGeneratedFile(mode: AnvilCompilationMode): File {
  val files = walkGeneratedFiles(mode).toList()
  assertThat(files).hasSize(1)
  return files[0]
}

internal fun JvmCompilationResult.assertFileGenerated(
  mode: AnvilCompilationMode,
  fileName: String,
) {
  if (generatedFileOrNull(mode, fileName) == null) {
    val files = walkGeneratedFiles(mode).toList()
    fail(
      buildString {
        appendLine("Could not find file '$fileName' in the generated files:")
        for (file in files.sorted()) {
          appendLine("\t- ${file.name}")
        }
      },
    )
  }
}

internal fun JvmCompilationResult.generatedFileOrNull(
  mode: AnvilCompilationMode,
  fileName: String,
): File? = walkGeneratedFiles(mode).singleOrNull { it.name == fileName }

/**
 * Parameters for configuring [AnvilCompilationMode] and whether to run a full test run or not.
 */
internal fun useDaggerAndKspParams(
  embeddedCreator: () -> Embedded? = { Embedded() },
  kspCreator: () -> Ksp? = { Ksp() },
): Collection<Any> {
  return cartesianProduct(
    listOf(
      isFullTestRun(),
      false,
    ),
    listOfNotNull(
      embeddedCreator(),
      kspCreator(),
    ),
  ).mapNotNull { (useDagger, mode) ->
    if (useDagger == true && mode is Ksp) {
      // TODO Dagger is not supported with KSP in Anvil's tests yet
      null
    } else {
      arrayOf(useDagger, mode)
    }
  }.distinct()
}

/** In any failing compilation in KSP, it always prints this error line first. */
private const val KSP_ERROR_HEADER = "e: Error occurred in KSP, check log for detail"

internal fun CompilationResult.compilationErrorLine(): String {
  return messages
    .lineSequence()
    .first { it.startsWith("e:") && KSP_ERROR_HEADER !in it }
}

internal fun File.assertStableInterfaceOrder() {
  val classNamesAndLineNumbers = readText().lines().withIndex()
    .filter { (_, line) -> line.startsWith("public interface ") }
    .sortedBy { it.value }

  assertThat(classNamesAndLineNumbers).isEqualTo(classNamesAndLineNumbers.sortedBy { it.index })
}
