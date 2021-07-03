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
import org.jetbrains.kotlin.config.JvmTarget
import java.io.File
import java.io.OutputStream
import kotlin.reflect.KClass

/**
 * Helpful for testing code generators in unit tests end to end.
 */
@ExperimentalAnvilApi
public fun compileAnvil(
  vararg sources: String,
  enableDaggerAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  generateDaggerFactoriesOnly: Boolean = false,
  disableComponentMerging: Boolean = false,
  allWarningsAsErrors: Boolean = true,
  useIR: Boolean = true,
  messageOutputStream: OutputStream = System.out,
  workingDir: File? = null,
  block: Result.() -> Unit = { }
): Result {
  return KotlinCompilation()
    .apply {
      compilerPlugins = listOf(AnvilComponentRegistrar())
      this.useIR = useIR
      useOldBackend = !useIR
      inheritClassPath = true
      jvmTarget = JvmTarget.JVM_1_8.description
      verbose = false
      this.allWarningsAsErrors = allWarningsAsErrors
      this.messageOutputStream = messageOutputStream

      if (workingDir != null) {
        this.workingDir = workingDir
      }

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

      this.sources = sources.map { content ->
        val packageDir = content.lines()
          .firstOrNull { it.trim().startsWith("package ") }
          ?.substringAfter("package ")
          ?.replace('.', '/')
          ?.let { "$it/" }
          ?: ""

        val name = "${this.workingDir.absolutePath}/sources/src/main/java/$packageDir/Source.kt"
        with(File(name).parentFile) {
          check(exists() || mkdirs())
        }

        SourceFile.kotlin(name, contents = content, trimIndent = true)
      }
    }
    .compile()
    .also(block)
}

@ExperimentalAnvilApi
public fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
    "${packageName()}$enclosingClassString$simpleName$companionString" +
      "_${providerMethodName.capitalize()}Factory"
  )
}

@ExperimentalAnvilApi
public fun Class<*>.factoryClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${packageName()}$enclosingClassString${simpleName}_Factory")
}

@ExperimentalAnvilApi
public fun Class<*>.implClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""
  return classLoader.loadClass("${packageName()}$enclosingClassString${simpleName}_Impl")
}

@ExperimentalAnvilApi
public fun Class<*>.membersInjector(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
    "${packageName()}$enclosingClassString${simpleName}_MembersInjector"
  )
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
  return this::class.java.getDeclaredField(name).use { it.get(this) }
}

@Suppress("UNCHECKED_CAST")
@ExperimentalAnvilApi
public fun <T> Annotation.getValue(): T =
  this::class.java.declaredMethods.single { it.name == "value" }.invoke(this) as T
