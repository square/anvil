package com.squareup.anvil.compiler.testing

import com.google.common.collect.Lists.cartesianProduct
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.testing.temp.capitalize
import com.squareup.anvil.compiler.testing.temp.generateHintFileName
import com.squareup.anvil.compiler.testing.temp.use
import com.squareup.kotlinpoet.asClassName
import com.tschuchort.compiletesting.CompilationResult
import com.tschuchort.compiletesting.JvmCompilationResult
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.test.fail

internal val JvmCompilationResult.contributingObject: Class<*>
  get() = classLoader.loadClass("com.squareup.test.ContributingObject")

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
  get() = getHint()

internal val Class<*>.hintContributesScope: KClass<*>?
  get() = hintContributesScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintContributesScopes: List<KClass<*>>
  get() = getHintScopes()

internal val Class<*>.generatedBindingModule: Class<*>
  get() = generatedBindingModules(
    ContributesBinding::class,
  ).single()
internal val Class<*>.generatedMultiBindingModule: Class<*>
  get() = generatedBindingModules(
    ContributesMultibinding::class,
  ).single()

internal fun Class<*>.generatedBindingModules(): List<Class<*>> {
  return generatedBindingModules(ContributesBinding::class)
}

private val Annotation.scope: KClass<*>
  get() {
    return when (this) {
      is ContributesTo -> scope
      is ContributesBinding -> scope
      is ContributesMultibinding -> scope
      else -> error("Unknown annotation class: $this")
    }
  }

private val Annotation.boundType: KClass<*>
  get() {
    return when (this) {
      is ContributesBinding -> boundType
      is ContributesMultibinding -> boundType
      else -> error("Unknown annotation class: $this")
    }
  }

private fun Class<*>.generatedBindingModules(
  annotationClass: KClass<out Annotation>,
): List<Class<*>> {
  return getAnnotationsByType(annotationClass.java)
    .map { bindingAnnotation ->
      val scope = bindingAnnotation.scope.asClassName()

      val boundType = bindingAnnotation.boundType
        .let {
          if (it == Unit::class) {
            interfaces.singleOrNull()?.kotlin ?: superclass.kotlin
          } else {
            it
          }
        }
        .asClassName()

      val suffix = when (annotationClass) {
        ContributesBinding::class -> BINDING_MODULE_SUFFIX
        ContributesMultibinding::class -> MULTIBINDING_MODULE_SUFFIX
        else -> error("Unknown annotation class: $annotationClass")
      }

      TODO("Figure out what this looks like without all the k1 logic?")

      // val typeName = Contribution.uniqueTypeName(
      //   originType = kotlin.asClassName(),
      //   boundType = boundType,
      //   scopeType = scope,
      //   qualifierKeyOrNull = qualifierKey(bindingAnnotation),
      //   suffix = suffix,
      // )
      //
      // classLoader.loadClass(typeName.toString())
    }
}

private fun Class<*>.qualifierKey(bindingAnnotation: Annotation): String? {

  val ignoreQualifier = bindingAnnotation::class.memberProperties
    .firstOrNull { it.name == "ignoreQualifier" }
    ?.call(bindingAnnotation)

  if (ignoreQualifier == true) return null

  // For each annotation on the receiver class, check its class declaration
  // to see if it has the `@Qualifier` annotation.
  val qualifierAnnotation = annotations
    .firstOrNull { it.annotationClass.hasAnnotation<javax.inject.Qualifier>() }
    // If there is no qualifier annotation, there's no key
    ?: return null

  val qualifierFqName = qualifierAnnotation.annotationClass.qualifiedName!!

  val joinedArgs = qualifierAnnotation.annotationClass
    .declaredMemberProperties
    .joinToString("") { property ->

      val valueString = when (val argument = property.call(qualifierAnnotation)) {
        is Enum<*> -> "${argument::class.qualifiedName}.${argument.name}"
        is Class<*> -> argument.kotlin.qualifiedName
        is KClass<*> -> argument.qualifiedName
        else -> argument.toString()
      }
      property.name + valueString
    }

  return qualifierFqName + joinedArgs
}

internal val Class<*>.bindingOriginKClass: KClass<*>?
  get() {
    return resolveOriginClass(ContributesBinding::class)
  }

internal val Class<*>.bindingModuleScope: KClass<*>
  get() = bindingModuleScopes.first()

internal val Class<*>.bindingModuleScopes: List<KClass<*>>
  get() = generatedBindingModules()
    .map { generatedBindingModule ->
      val contributesTo = generatedBindingModule.getAnnotation(ContributesTo::class.java)
      contributesTo.scope
    }

internal val Class<*>.multibindingOriginClass: KClass<*>?
  get() = resolveOriginClass(ContributesMultibinding::class)

internal fun Class<*>.resolveOriginClass(bindingAnnotation: KClass<out Annotation>): KClass<*>? {
  val generatedBindingModule = generatedBindingModules(
    bindingAnnotation,
  ).firstOrNull() ?: return null
  val bindingFunction = generatedBindingModule.declaredMethods[0]
  val parameterImplType = bindingFunction.parameterTypes.firstOrNull()
  val internalBindingMarker: InternalBindingMarker =
    generatedBindingModule.kotlin.annotations.filterIsInstance<InternalBindingMarker>().single()
  val bindingMarkerOriginType = internalBindingMarker.originClass
  if (parameterImplType == null) {
    // Validate that the function is a provider in an object
    generatedBindingModule.kotlin.objectInstance.shouldNotBeNull()
  } else {
    generatedBindingModule.isInterface.shouldBeTrue()
    // Added validation that the binding marker type matches the binds param
    parameterImplType shouldBe bindingMarkerOriginType.java
  }
  return bindingMarkerOriginType
}

internal val Class<*>.multibindingModuleScope: KClass<*>?
  get() = multibindingModuleScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.multibindingModuleScopes: List<KClass<*>>
  get() = generatedBindingModules(ContributesMultibinding::class)
    .map { generatedBindingModule ->
      val contributesTo = generatedBindingModule.getAnnotation(ContributesTo::class.java)
      contributesTo.scope
    }

/**
 * Given a [mergeAnnotation], finds and returns the resulting merged Dagger annotation's modules.
 *
 * This is useful for testing that module merging worked correctly in the final Dagger component
 * during IR.
 */
internal fun Class<*>.mergedModules(mergeAnnotation: KClass<out Annotation>): Array<KClass<*>> {
  val mergedAnnotation = when (mergeAnnotation) {
    MergeComponent::class -> Component::class
    MergeSubcomponent::class -> Subcomponent::class
    MergeModules::class -> Module::class
    else -> error("Unknown merge annotation class: $mergeAnnotation")
  }
  return when (val annotation = getAnnotation(mergedAnnotation.java)) {
    is Component -> annotation.modules
    is Subcomponent -> annotation.modules
    is Module -> annotation.includes
    else -> error("Unknown merge annotation class: $mergeAnnotation")
  }
}

internal val Class<*>.hintSubcomponent: KClass<*>?
  get() = getHint()

internal val Class<*>.hintSubcomponentParentScope: KClass<*>?
  get() = hintSubcomponentParentScopes.takeIf { it.isNotEmpty() }?.single()

internal val Class<*>.hintSubcomponentParentScopes: List<KClass<*>>
  get() = getHintScopes()

private fun Class<*>.getHint(): KClass<*>? = contributedProperties()
  ?.filter { it.java == this }
  ?.also { it.size shouldBe 1 }
  ?.first()

private fun Class<*>.getHintScopes(): List<KClass<*>> =
  contributedProperties()
    ?.also { it.size shouldBeGreaterThanOrEqual 2 }
    ?.filter { it.java != this }
    ?: emptyList()

public fun Class<*>.contributedProperties(): List<KClass<*>>? {
  // The capitalize() comes from kotlinc's implicit handling of file names -> class names. It will
  // always, unless otherwise instructed via `@file:JvmName`, capitalize its facade class.

  val className = if (getAnnotation(InternalBindingMarker::class.java) != null) {
    generateSequence(this) { it.enclosingClass }
      .toList()
      .reversed()
      .joinToString(separator = "_") { it.simpleName }
      .capitalize()
      .plus("Kt")
  } else {

    kotlin.asClassName()
      .generateHintFileName(separator = "_", suffix = "", capitalizePackage = true)
      .plus("Kt")
  }

  val clazz = try {
    classLoader.loadClass("$HINT_PACKAGE.$className")
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

internal fun isFullTestRun(): Boolean = FULL_TEST_RUN
internal fun checkFullTestRun() = assumeTrue(isFullTestRun())

internal fun JvmCompilationResult.walkGeneratedFiles(): Sequence<File> {
  return outputDirectory.parentFile
    .resolve("build${File.separator}anvil")
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
}

internal fun JvmCompilationResult.singleGeneratedFile(): File {
  return walkGeneratedFiles().toList()
    .shouldHaveSize(1)
    .first()
}

internal fun JvmCompilationResult.assertFileGenerated(fileName: String) {
  if (generatedFileOrNull(fileName) == null) {
    val files = walkGeneratedFiles()
    fail(
      buildString {
        appendLine("Could not find file '$fileName' in the generated files:")
        for (file in files.sorted()) {
          appendLine("\t- ${file.path}")
        }
      },
    )
  }
}

internal fun JvmCompilationResult.generatedFileOrNull(
  fileName: String,
): File? = walkGeneratedFiles()
  .singleOrNull { it.name == fileName && "anvil${File.separatorChar}hint" !in it.absolutePath }

/**
 * Parameters for configuring [AnvilCompilationMode] and whether to run a full test run or not.
 */
internal fun testParams(
  embeddedCreator: () -> CompilationMode.K2? = { CompilationMode.K2(useKapt = false) },
): Collection<Any> {
  return cartesianProduct(
    listOf(
      isFullTestRun(),
      false,
    ),
    listOfNotNull(
      embeddedCreator(),
    ),
  ).mapNotNull { (useDagger, mode) ->
    arrayOf(useDagger, mode)
  }.distinct()
}

internal fun CompilationResult.compilationErrorLine(): String {
  return messages.lineSequence()
    .first { it.startsWith("e:") }
}

internal inline fun <T, R> Array<out T>.flatMapArray(transform: (T) -> Array<R>) =
  flatMapTo(ArrayList()) {
    transform(it).asIterable()
  }
