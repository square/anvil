package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.packageName
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Assume
import kotlin.reflect.KClass

@Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
internal fun compile(
  vararg sources: String,
  block: Result.() -> Unit = { }
): Result = compileAnvil(
  sources = sources,
  useIR = USE_IR,
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

internal val Result.assistedService: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedService")

internal val Result.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass("com.squareup.test.AssistedServiceFactory")

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
  get() = getHintScope(HINT_CONTRIBUTES_PACKAGE_PREFIX)

internal val Class<*>.hintBinding: KClass<*>?
  get() = getHint(HINT_BINDING_PACKAGE_PREFIX)

internal val Class<*>.hintBindingScope: KClass<*>?
  get() = getHintScope(HINT_BINDING_PACKAGE_PREFIX)

internal val Class<*>.hintMultibinding: KClass<*>?
  get() = getHint(HINT_MULTIBINDING_PACKAGE_PREFIX)

internal val Class<*>.hintMultibindingScope: KClass<*>?
  get() = getHintScope(HINT_MULTIBINDING_PACKAGE_PREFIX)

private fun Class<*>.getHint(prefix: String): KClass<*>? = contributedProperties(prefix)
  ?.filter { it.java == this }
  ?.also { assertThat(it.size).isEqualTo(1) }
  ?.first()

private fun Class<*>.getHintScope(prefix: String): KClass<*>? =
  contributedProperties(prefix)
    ?.also { assertThat(it.size).isEqualTo(2) }
    ?.filter { it.java != this }
    ?.also { assertThat(it.size).isEqualTo(1) }
    ?.first()

private fun Class<*>.contributedProperties(packagePrefix: String): List<KClass<*>>? {
  // The capitalize() doesn't make sense, I don't know where this is coming from. Maybe it's a
  // bug in the compile testing library?
  val className = canonicalName.replace('.', '_')
    .capitalize() + "Kt"

  val clazz = try {
    classLoader.loadClass("$packagePrefix.${packageName()}$className")
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

internal fun assumeMergeComponent(annotationClass: KClass<*>) {
  Assume.assumeTrue(annotationClass == MergeComponent::class)
}
