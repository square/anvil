package com.squareup.anvil.compiler

import com.google.common.truth.ComparableSubject
import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.generatedClassesString
import com.squareup.anvil.compiler.internal.testing.packageName
import com.squareup.anvil.compiler.internal.testing.use
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.INTERNAL_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.reflect.KClass

internal fun compile(
  @Language("kotlin") vararg sources: String,
  previousCompilationResult: Result? = null,
  enableDaggerAnnotationProcessor: Boolean = false,
  codeGenerators: List<CodeGenerator> = emptyList(),
  allWarningsAsErrors: Boolean = WARNINGS_AS_ERRORS,
  mode: AnvilCompilationMode = AnvilCompilationMode.Embedded(codeGenerators),
  block: Result.() -> Unit = { }
): Result = compileAnvil(
  sources = sources,
  allWarningsAsErrors = allWarningsAsErrors,
  previousCompilationResult = previousCompilationResult,
  enableDaggerAnnotationProcessor = enableDaggerAnnotationProcessor,
  mode = mode,
  block = block
)

internal val Result.contributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ContributingInterface")

internal val Result.secondContributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SecondContributingInterface")

internal val Result.innerInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SomeClass\$InnerInterface")

internal val Result.parentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface")

internal val Result.parentInterface1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface1")

internal val Result.parentInterface2: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface2")

internal val Result.componentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface")

internal val Result.subcomponentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface")

internal val Result.daggerModule1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule1")

internal val Result.assistedService: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedService")

internal val Result.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedServiceFactory")

internal val Result.daggerModule2: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule2")

internal val Result.daggerModule3: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule3")

internal val Result.daggerModule4: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule4")

internal val Result.innerModule: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface\$InnerModule")

internal val Result.nestedInjectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentClass\$NestedInjectClass")

internal val Result.injectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.InjectClass")

internal val Result.anyQualifier: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AnyQualifier")

@Suppress("UNCHECKED_CAST")
internal val Result.bindingKey: Class<out Annotation>
  get() = classLoader.loadClass("com.squareup.test.BindingKey") as Class<out Annotation>

internal val Class<*>.hintContributes: KClass<*>?
  get() = getHint(HINT_CONTRIBUTES_PACKAGE_PREFIX)

internal val Class<*>.hintContributesScope: KClass<*>?
  get() = hintContributesScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintContributesScopes: List<KClass<*>>
  get() = getHintScopes(HINT_CONTRIBUTES_PACKAGE_PREFIX)

internal val Class<*>.hintBinding: KClass<*>?
  get() = getHint(HINT_BINDING_PACKAGE_PREFIX)

internal val Class<*>.hintBindingScope: KClass<*>?
  get() = hintBindingScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintBindingScopes: List<KClass<*>>
  get() = getHintScopes(HINT_BINDING_PACKAGE_PREFIX)

internal val Class<*>.hintMultibinding: KClass<*>?
  get() = getHint(HINT_MULTIBINDING_PACKAGE_PREFIX)

internal val Class<*>.hintMultibindingScope: KClass<*>?
  get() = hintMultibindingScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintMultibindingScopes: List<KClass<*>>
  get() = getHintScopes(HINT_MULTIBINDING_PACKAGE_PREFIX)

internal val Class<*>.hintSubcomponent: KClass<*>?
  get() = getHint(HINT_SUBCOMPONENTS_PACKAGE_PREFIX)

internal val Class<*>.hintSubcomponentParentScope: KClass<*>?
  get() = hintSubcomponentParentScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintSubcomponentParentScopes: List<KClass<*>>
  get() = getHintScopes(HINT_SUBCOMPONENTS_PACKAGE_PREFIX)

internal val Class<*>.anvilModule: Class<*>
  get() = classLoader.loadClass(
    "$MODULE_PACKAGE_PREFIX.${generatedClassesString(separator = "")}$ANVIL_MODULE_SUFFIX"
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

internal fun Result.walkGeneratedFiles(mode: AnvilCompilationMode): Sequence<File> {
  val dirToSearch = when (mode) {
    is AnvilCompilationMode.Embedded -> outputDirectory.parentFile.resolve("build/anvil")
    is AnvilCompilationMode.Ksp -> outputDirectory.parentFile.resolve("ksp/sources")
  }
  return dirToSearch.walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
}
