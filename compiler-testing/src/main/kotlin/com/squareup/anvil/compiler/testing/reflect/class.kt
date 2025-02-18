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
