@file:Suppress("unused")

package com.squareup.anvil.compiler.internal.testing

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.AnvilCommandLineProcessor
import com.squareup.anvil.compiler.AnvilComponentRegistrar
import com.squareup.anvil.compiler.internal.capitalize
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.Result
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import dagger.internal.codegen.ComponentProcessor
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.io.OutputStream
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.reflect.KClass

/**
 * A simple API over a [KotlinCompilation] with extra configuration support for Anvil.
 */
@ExperimentalAnvilApi
public class AnvilCompilation internal constructor(
  val kotlinCompilation: KotlinCompilation
) {

  private var isCompiled = false
  private var anvilConfigured = false
  private var swapClassLoader = false

  /** Configures this the Anvil behavior of this compilation. */
  @Suppress("SuspiciousCollectionReassignment")
  @ExperimentalAnvilApi
  public fun configureAnvil(
    enableDaggerAnnotationProcessor: Boolean = false,
    generateDaggerFactories: Boolean = false,
    generateDaggerFactoriesOnly: Boolean = false,
    disableComponentMerging: Boolean = false,
    enableExperimentalAnvilApis: Boolean = true,
  ) = apply {
    checkNotCompiled()
    anvilConfigured = true
    kotlinCompilation.apply {
      compilerPlugins = listOf(AnvilComponentRegistrar())
      if (enableDaggerAnnotationProcessor) {
        annotationProcessors = listOf(ComponentProcessor())
      }

      val anvilCommandLineProcessor = AnvilCommandLineProcessor()
      commandLineProcessors = listOf(anvilCommandLineProcessor)

      pluginOptions = listOf(
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "src-gen-dir",
          optionValue = File(workingDir, "build/anvil").absolutePath
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "generate-dagger-factories",
          optionValue = generateDaggerFactories.toString()
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "generate-dagger-factories-only",
          optionValue = generateDaggerFactoriesOnly.toString()
        ),
        PluginOption(
          pluginId = anvilCommandLineProcessor.pluginId,
          optionName = "disable-component-merging",
          optionValue = disableComponentMerging.toString()
        )
      )

      if (enableExperimentalAnvilApis) {
        kotlincArguments += listOf(
          "-Xopt-in=kotlin.RequiresOptIn",
          "-Xopt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi"
        )
      }
    }
  }

  /**
   * Helpful shim to configure both [KotlinCompilation.useIR] and [KotlinCompilation.useOldBackend]
   * accordingly.
   */
  public fun useIR(useIR: Boolean) = apply {
    checkNotCompiled()
    kotlinCompilation.useIR = useIR
    kotlinCompilation.useOldBackend = !useIR
  }

  /** Adds the given sources to this compilation with their packages and names inferred. */
  public fun addSources(@Language("kotlin") vararg sources: String) = apply {
    checkNotCompiled()
    kotlinCompilation.sources += sources.mapIndexed { index, content ->
      val packageDir = content.lines()
        .firstOrNull { it.trim().startsWith("package ") }
        ?.substringAfter("package ")
        ?.replace('.', '/')
        ?.let { "$it/" }
        ?: ""

      val name = "${kotlinCompilation.workingDir.absolutePath}/sources/src/main/java/" +
        "$packageDir/Source$index.kt"

      Files.createDirectories(File(name).parentFile.toPath())

      SourceFile.kotlin(name, contents = content, trimIndent = true)
    }
  }

  public fun addPreviousCompilationResult(result: Result) = apply {
    checkNotCompiled()
    kotlinCompilation.classpaths += result.outputDirectory
    swapClassLoader = true
  }

  /**
   * Returns an Anvil-generated file with the given [packageName] and [fileName] from its expected
   * path.
   */
  public fun generatedAnvilFile(
    packageName: String,
    fileName: String
  ): File {
    check(isCompiled) {
      "No compilation run yet! Call compile() first."
    }
    return File(
      kotlinCompilation.workingDir,
      "build/anvil/${packageName.replace('.', File.separatorChar)}/$fileName.kt"
    )
      .apply {
        check(exists()) {
          "Generated file not found!"
        }
      }
  }

  private fun checkNotCompiled() {
    check(!isCompiled) {
      "Already compiled! Create a new compilation if you want to compile again."
    }
  }

  /**
   * Compiles the underlying [KotlinCompilation]. Note that if [configureAnvil] has not been called
   * prior to this, it will be configured with default behavior.
   */
  public fun compile(
    @Language("kotlin") vararg sources: String,
    block: Result.() -> Unit = {}
  ): Result {
    checkNotCompiled()
    if (!anvilConfigured) {
      // Configure with default behaviors
      configureAnvil()
    }
    addSources(*sources)
    isCompiled = true
    return kotlinCompilation.compile()
      .apply(block)
      .apply { swapClassLoaderIfNecessary() }
  }

  // This workaround is needed until this issue is resolved:
  // https://github.com/tschuchortdev/kotlin-compile-testing/issues/216
  private fun Result.swapClassLoaderIfNecessary() {
    if (!swapClassLoader) return

    val newClassLoader = URLClassLoader(
      kotlinCompilation.classpaths.plus(outputDirectory).map { it.toURI().toURL() }.toTypedArray(),
      this::class.java.classLoader
    )

    Result::class.java.declaredFields
      .single { it.name == "classLoader" }
      .use { it.set(this, newClassLoader) }
  }

  companion object {
    public operator fun invoke(): AnvilCompilation {
      return AnvilCompilation(
        KotlinCompilation().apply {
          // Sensible default behaviors
          inheritClassPath = true
          jvmTarget = JvmTarget.JVM_1_8.description
          verbose = false
        }
      )
    }
  }
}

/**
 * Helpful for testing code generators in unit tests end to end.
 *
 * This covers common cases, but is built upon reusable logic in [AnvilCompilation] and
 * [configureForAnvil]. Consider using those APIs if more advanced configuration is needed.
 */
@ExperimentalAnvilApi
public fun compileAnvil(
  @Language("kotlin") vararg sources: String,
  enableDaggerAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  generateDaggerFactoriesOnly: Boolean = false,
  disableComponentMerging: Boolean = false,
  allWarningsAsErrors: Boolean = true,
  useIR: Boolean = true,
  messageOutputStream: OutputStream = System.out,
  workingDir: File? = null,
  enableExperimentalAnvilApis: Boolean = true,
  previousCompilationResult: Result? = null,
  block: Result.() -> Unit = { }
): Result {
  return AnvilCompilation()
    .apply {
      kotlinCompilation.apply {
        this.allWarningsAsErrors = allWarningsAsErrors
        this.messageOutputStream = messageOutputStream
        if (workingDir != null) {
          this.workingDir = workingDir
        }
      }

      if (previousCompilationResult != null) {
        addPreviousCompilationResult(previousCompilationResult)
      }
    }
    .configureAnvil(
      enableDaggerAnnotationProcessor = enableDaggerAnnotationProcessor,
      generateDaggerFactories = generateDaggerFactories,
      generateDaggerFactoriesOnly = generateDaggerFactoriesOnly,
      disableComponentMerging = disableComponentMerging,
      enableExperimentalAnvilApis = enableExperimentalAnvilApis,
    )
    .useIR(useIR)
    .compile(*sources)
    .also(block)
}

@ExperimentalAnvilApi
public fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  return classLoader.loadClass(
    "${generatedClassesString()}${companionString}_${providerMethodName.capitalize()}Factory"
  )
}

@ExperimentalAnvilApi
public fun Class<*>.factoryClass(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_Factory")
}

@ExperimentalAnvilApi
public fun Class<*>.implClass(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_Impl")
}

@ExperimentalAnvilApi
public fun Class<*>.membersInjector(): Class<*> {
  return classLoader.loadClass("${generatedClassesString()}_MembersInjector")
}

@ExperimentalAnvilApi
public fun Class<*>.generatedClassesString(
  separator: String = "_"
): String {
  return generateSequence(enclosingClass) { it.enclosingClass }
    .toList()
    .reversed()
    .joinToString(separator = "", prefix = packageName(), postfix = simpleName) {
      "${it.simpleName}$separator"
    }
}

@ExperimentalAnvilApi
public fun Class<*>.packageName(): String = `package`.name.let {
  if (it.isBlank()) "" else "$it."
}

@ExperimentalAnvilApi
public val Class<*>.daggerComponent: Component
  get() = annotations.filterIsInstance<Component>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public val Class<*>.daggerSubcomponent: Subcomponent
  get() = annotations.filterIsInstance<Subcomponent>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public val Class<*>.daggerModule: Module
  get() = annotations.filterIsInstance<Module>()
    .also { assertThat(it).hasSize(1) }
    .first()

@ExperimentalAnvilApi
public infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)

@ExperimentalAnvilApi
public infix fun KClass<*>.extends(other: KClass<*>): Boolean =
  other.java.isAssignableFrom(this.java)

@ExperimentalAnvilApi
public fun Array<KClass<*>>.withoutAnvilModule(): List<KClass<*>> = toList().withoutAnvilModule()

@ExperimentalAnvilApi
public fun Collection<KClass<*>>.withoutAnvilModule(): List<KClass<*>> =
  filterNot { it.qualifiedName!!.startsWith("anvil.module") }

@ExperimentalAnvilApi
public fun Any.invokeGet(vararg args: Any?): Any {
  val method = this::class.java.declaredMethods.single { it.name == "get" }
  return method.invoke(this, *args)
}

@ExperimentalAnvilApi
public fun Any.getPropertyValue(name: String): Any {
  return this::class.java.fields.first { it.name == name }.use { it.get(this) }
}

@Suppress("UNCHECKED_CAST")
@ExperimentalAnvilApi
public fun <T> Annotation.getValue(): T =
  this::class.java.declaredMethods.single { it.name == "value" }.invoke(this) as T
