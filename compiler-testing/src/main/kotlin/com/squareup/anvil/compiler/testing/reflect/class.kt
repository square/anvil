package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.internal.InternalBindingMarker
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import com.squareup.anvil.compiler.k2.utils.names.membersInjectorSibling
import dagger.Component
import dagger.Module
import dagger.Subcomponent
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

@ExperimentalAnvilApi
public val Class<*>.membersInjector: Class<*>
  get() = classLoader.loadClass(classId.membersInjectorSibling)

@ExperimentalAnvilApi
public val Class<*>.daggerComponent: Component
  get() = annotations.filterIsInstance<Component>().single()

@ExperimentalAnvilApi
public val Class<*>.daggerSubcomponent: Subcomponent
  get() = annotations.filterIsInstance<Subcomponent>().single()

@ExperimentalAnvilApi
public val Class<*>.daggerModule: Module
  get() = annotations.filterIsInstance<Module>().single()

@ExperimentalAnvilApi
public fun Class<*>.factoryClass(): Class<*> = classLoader.loadClass(classId.factoryJoined)

public fun Class<*>.field(): ReadOnlyProperty<Any?, Field> =
  ReadOnlyProperty { _, property -> getField(property.name).shouldNotBeNull() }

public fun Class<*>.declaredField(): ReadOnlyProperty<Any?, Field> =
  ReadOnlyProperty { _, property -> getDeclaredField(property.name).shouldNotBeNull() }

@ExperimentalAnvilApi
public fun Class<*>.packageName(): String = `package`.name.let {
  if (it.isBlank()) "" else "$it."
}

/** Returns the ClassId for this Class. Throws if the class is primitive or an array. */
public val Class<*>.classId: ClassId
  get() {
    require(!isPrimitive) { "Can't compute ClassId for primitive type: $this" }
    require(!isArray) { "Can't compute ClassId for array type: $this" }
    val outerClass = declaringClass
    @Suppress("RecursivePropertyAccessor")
    return when {
      outerClass == null -> ClassId.topLevel(FqName(canonicalName))
      isLocalClass -> ClassId(FqName(packageName()), FqName(simpleName), isLocalClass)
      else -> outerClass.classId.createNestedClassId(Name.identifier(simpleName))
    }
  }

/**
 * Creates a new instance of this class with the given arguments. This method assumes that this
 * class only declares a single constructor.
 */
@ExperimentalAnvilApi
@Suppress("UNCHECKED_CAST")
public fun <T : Any> Class<T>.createInstance(
  vararg initargs: Any?,
): T = declaredConstructors.single().use { it.newInstance(*initargs) } as T

@ExperimentalAnvilApi
public inline fun <T, E : AccessibleObject> E.use(block: (E) -> T): T {
  // Deprecated since Java 9, but many projects still use JDK 8 for compilation.
  @Suppress("DEPRECATION")
  val original = isAccessible

  return try {
    isAccessible = true
    block(this)
  } finally {
    isAccessible = original
  }
}

@ExperimentalAnvilApi
public infix fun Class<*>.extends(other: Class<*>): Boolean = other.isAssignableFrom(this)

@ExperimentalAnvilApi
public fun Array<KClass<*>>.withoutAnvilModules(): List<KClass<*>> = asList().withoutAnvilModules()

@ExperimentalAnvilApi
public fun Collection<KClass<*>>.withoutAnvilModules(): List<KClass<*>> =
  filterNot {
    it.qualifiedName!!.startsWith("anvil.module") ||
      it.java.isAnnotationPresent(
        InternalBindingMarker::class.java,
      )
  }

/*

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
    assertThat(generatedBindingModule.kotlin.objectInstance).isNotNull()
  } else {
    assertThat(generatedBindingModule.isInterface).isTrue()
    // Added validation that the binding marker type matches the binds param
    assertThat(parameterImplType).isEqualTo(bindingMarkerOriginType.java)
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
  ?.also { assertThat(it.size).isEqualTo(1) }
  ?.first()

private fun Class<*>.getHintScopes(): List<KClass<*>> =
  contributedProperties()
    ?.also { assertThat(it.size).isAtLeast(2) }
    ?.filter { it.java != this }
    ?: emptyList()

fun Class<*>.contributedProperties(): List<KClass<*>>? {
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

internal fun CompilationResult.compilationErrorLine(): String {
  return messages.lineSequence()
    .first { it.startsWith("e:") }
}

*/
