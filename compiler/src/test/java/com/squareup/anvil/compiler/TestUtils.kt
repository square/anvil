package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.Result
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import dagger.internal.codegen.ComponentProcessor
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.name.FqName
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale.US
import kotlin.reflect.KClass

internal fun compile(
  source: String,
  enableDaggerAnnotationProcessor: Boolean = false,
  generateDaggerFactories: Boolean = false,
  block: Result.() -> Unit = { }
): Result {
  return KotlinCompilation()
      .apply {
        compilerPlugins = listOf(AnvilComponentRegistrar())
        useIR = USE_IR
        inheritClassPath = true
        jvmTarget = JvmTarget.JVM_1_8.description

        if (enableDaggerAnnotationProcessor) {
          annotationProcessors = listOf(ComponentProcessor())
        }

        val commandLineProcessor = AnvilCommandLineProcessor()
        commandLineProcessors = listOf(commandLineProcessor)

        pluginOptions = listOf(
            PluginOption(
                pluginId = commandLineProcessor.pluginId,
                optionName = srcGenDirName,
                optionValue = File(workingDir, "build/anvil").absolutePath
            ),
            PluginOption(
                pluginId = commandLineProcessor.pluginId,
                optionName = generateDaggerFactoriesName,
                optionValue = generateDaggerFactories.toString()
            )
        )

        val name = "${workingDir.absolutePath}/sources/src/main/java/com/squareup/test/Source.kt"
        check(File(name).parentFile.mkdirs())

        sources = listOf(SourceFile.kotlin(name, contents = source, trimIndent = true))
      }
      .compile()
      .also(block)
}

internal val Result.contributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ContributingInterface")

internal val Result.secondContributingInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SecondContributingInterface")

internal val Result.innerInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SomeClass\$InnerInterface")

internal val Result.parentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ParentInterface")

internal val Result.componentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface")

internal val Result.componentInterfaceAnvilModule: Class<*>
  get() = classLoader
      .loadClass("$MODULE_PACKAGE_PREFIX.com.squareup.test.ComponentInterfaceAnvilModule")

internal val Result.subcomponentInterface: Class<*>
  get() = classLoader.loadClass("com.squareup.test.SubcomponentInterface")

internal val Result.subcomponentInterfaceAnvilModule: Class<*>
  get() = classLoader
      .loadClass("$MODULE_PACKAGE_PREFIX.com.squareup.test.SubcomponentInterfaceAnvilModule")

internal val Result.daggerModule1: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule1")

internal val Result.daggerModule1AnvilModule: Class<*>
  get() = classLoader
      .loadClass("$MODULE_PACKAGE_PREFIX.com.squareup.test.DaggerModule1AnvilModule")

internal val Result.daggerModule2: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule2")

internal val Result.daggerModule3: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule3")

internal val Result.daggerModule4: Class<*>
  get() = classLoader.loadClass("com.squareup.test.DaggerModule4")

internal val Result.innerModule: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ComponentInterface\$InnerModule")

internal val Result.injectClass: Class<*>
  get() = classLoader.loadClass("com.squareup.test.InjectClass")

internal val Class<*>.hintContributes: KClass<*>?
  get() = contributedProperties(HINT_CONTRIBUTES_PACKAGE_PREFIX)
      ?.filter { it.java == this }
      ?.also { assertThat(it.size).isEqualTo(1) }
      ?.first()

internal val Class<*>.hintContributesScope: KClass<*>?
  get() = contributedProperties(HINT_CONTRIBUTES_PACKAGE_PREFIX)
      ?.also { assertThat(it.size).isEqualTo(2) }
      ?.filter { it.java != this }
      ?.also { assertThat(it.size).isEqualTo(1) }
      ?.first()

internal val Class<*>.hintBinding: KClass<*>?
  get() = contributedProperties(HINT_BINDING_PACKAGE_PREFIX)
      ?.filter { it.java == this }
      ?.also { assertThat(it.size).isEqualTo(1) }
      ?.first()

internal val Class<*>.hintBindingScope: KClass<*>?
  get() = contributedProperties(HINT_BINDING_PACKAGE_PREFIX)
      ?.also { assertThat(it.size).isEqualTo(2) }
      ?.filter { it.java != this }
      ?.also { assertThat(it.size).isEqualTo(1) }
      ?.first()

internal fun Class<*>.moduleFactoryClass(
  providerMethodName: String,
  companion: Boolean = false
): Class<*> {
  val companionString = if (companion) "_Companion" else ""
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass(
      "${`package`.name}.$enclosingClassString$simpleName$companionString" +
          "_${providerMethodName.capitalize(US)}Factory"
  )
}

internal fun Class<*>.factoryClass(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}.$enclosingClassString${simpleName}_Factory")
}

internal fun Class<*>.membersInjector(): Class<*> {
  val enclosingClassString = enclosingClass?.let { "${it.simpleName}_" } ?: ""

  return classLoader.loadClass("${`package`.name}." +
      "$enclosingClassString${simpleName}_MembersInjector")
}

private fun Class<*>.contributedProperties(packagePrefix: String): List<KClass<*>>? {
  // The capitalize() doesn't make sense, I don't know where this is coming from. Maybe it's a
  // bug in the compile testing library?
  val className = canonicalName.replace('.', '_')
      .capitalize(US) + "Kt"

  val clazz = try {
    classLoader.loadClass("$packagePrefix.${`package`.name}.$className")
  } catch (e: ClassNotFoundException) {
    return null
  }

  return clazz.declaredFields
      .map {
        it.isAccessible = true
        it.get(null)
      }
      .filterIsInstance<KClass<*>>()
}

internal val Class<*>.daggerComponent: Component
  get() = annotations.filterIsInstance<Component>()
      .also { assertThat(it).hasSize(1) }
      .first()

internal val Class<*>.daggerSubcomponent: Subcomponent
  get() = annotations.filterIsInstance<Subcomponent>()
      .also { assertThat(it).hasSize(1) }
      .first()

internal val Class<*>.daggerModule: Module
  get() = annotations.filterIsInstance<Module>()
      .also { assertThat(it).hasSize(1) }
      .first()

internal infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)

internal fun assumeMergeComponent(annotationClass: KClass<*>) {
  assumeTrue(annotationClass == MergeComponent::class)
}

internal fun Array<KClass<*>>.withoutAnvilModule(): List<KClass<*>> = toList().withoutAnvilModule()
internal fun Collection<KClass<*>>.withoutAnvilModule(): List<KClass<*>> =
  filterNot { FqName(it.qualifiedName!!).isAnvilModule() }
