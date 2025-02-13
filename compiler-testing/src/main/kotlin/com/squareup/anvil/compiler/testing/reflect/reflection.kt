package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.asJavaNameString
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import kotlin.properties.ReadOnlyProperty

@ExperimentalAnvilApi
public fun Any.invokeGet(vararg args: Any?): Any {
  val method = this::class.java.getDeclaredMethod("get", *args.map { it?.javaClass }.toTypedArray())
  return method.invoke(this, *args)
}

@ExperimentalAnvilApi
public fun Any.getFieldValue(name: String): Any {
  return javaClass.getField(name).use { it.get(this) }
}

@ExperimentalAnvilApi
public fun Any.getDeclaredFieldValue(name: String): Any {
  return javaClass.getDeclaredField(name).use { it.get(this) }
}

@ExperimentalAnvilApi
public val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

@ExperimentalAnvilApi
public val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)

public fun FqName.loadClass(classLoader: ClassLoader): Class<*> {
  return classLoader.loadClass(this.asString())
}

public fun ClassLoader.loadClass(fqName: FqName): Class<*> = loadClass(fqName.asString())
public fun ClassLoader.loadClassOrNull(fqName: FqName): Class<*>? = try {
  loadClass(fqName.asString())
} catch (e: ClassNotFoundException) {
  null
}

public fun ClassLoader.loadClass(classId: ClassId): Class<*> = loadClass(classId.asJavaNameString())

public fun ClassLoader.loadClassOrNull(classId: ClassId): Class<*>? = try {
  loadClass(classId.asJavaNameString())
} catch (e: ClassNotFoundException) {
  null
}

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

public operator fun ClassLoader.get(fqName: FqName): Class<*> = loadClass(fqName)
public operator fun ClassLoader.get(fqName: String): Class<*> = loadClass(fqName)

public fun Class<*>.field(): ReadOnlyProperty<Any?, Field> =
  ReadOnlyProperty { _, property -> getField(property.name).shouldNotBeNull() }

public fun Class<*>.declaredField(): ReadOnlyProperty<Any?, Field> =
  ReadOnlyProperty { _, property -> getDeclaredField(property.name).shouldNotBeNull() }

public val ClassLoader.any: Class<*>
  get() = loadClass(TestNames.any)
public val ClassLoader.nothing: Class<*>
  get() = loadClass(TestNames.nothing)
public val ClassLoader.contributingObject: Class<*>
  get() = loadClass(TestNames.contributingObject)
public val ClassLoader.contributingInterface: Class<*>
  get() = loadClass(TestNames.contributingInterface)
public val ClassLoader.secondContributingInterface: Class<*>
  get() = loadClass(TestNames.secondContributingInterface)
public val ClassLoader.parentInterface: Class<*>
  get() = loadClass(TestNames.parentInterface)
public val ClassLoader.parentInterface1: Class<*>
  get() = loadClass(TestNames.parentInterface1)
public val ClassLoader.parentInterface2: Class<*>
  get() = loadClass(TestNames.parentInterface2)
public val ClassLoader.componentInterface: Class<*>
  get() = loadClass(TestNames.componentInterface)
public val ClassLoader.subcomponentInterface: Class<*>
  get() = loadClass(TestNames.subcomponentInterface)
public val ClassLoader.assistedService: Class<*>
  get() = loadClass(TestNames.assistedService)
public val ClassLoader.assistedServiceFactory: Class<*>
  get() = loadClass(TestNames.assistedServiceFactory)
public val ClassLoader.daggerModule1: Class<*>
  get() = loadClass(TestNames.daggerModule1)
public val ClassLoader.daggerModule2: Class<*>
  get() = loadClass(TestNames.daggerModule2)
public val ClassLoader.daggerModule3: Class<*>
  get() = loadClass(TestNames.daggerModule3)
public val ClassLoader.daggerModule4: Class<*>
  get() = loadClass(TestNames.daggerModule4)
public val ClassLoader.injectClass: Class<*>
  get() = loadClass(TestNames.injectClass)
public val ClassLoader.injectClass_Factory: Class<*>
  get() = loadClass(TestNames.injectClass_Factory)
public val ClassLoader.javaClass: Class<*>
  get() = loadClass(TestNames.javaClass)
public val ClassLoader.anyQualifier: Class<*>
  get() = loadClass(TestNames.anyQualifier)
