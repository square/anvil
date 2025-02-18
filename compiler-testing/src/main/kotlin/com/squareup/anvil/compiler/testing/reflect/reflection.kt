package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.asJavaNameString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@ExperimentalAnvilApi
public fun Any.invokeGet(vararg args: Any?): Any {
  val method = this::class.java.getDeclaredMethod("get", *args.map { it?.javaClass }.toTypedArray())
  return method.invoke(this, *args)
}

@Deprecated("renamed to `getFieldValue`", ReplaceWith("getFieldValue(name)"))
@ExperimentalAnvilApi
public fun Any.getPropertyValue(name: String): Any = javaClass.getField(name).use { it.get(this) }

@ExperimentalAnvilApi
public fun Any.getFieldValue(name: String): Any = javaClass.getField(name).use { it.get(this) }

@ExperimentalAnvilApi
public fun Any.getDeclaredFieldValue(name: String): Any {
  return javaClass.getDeclaredField(name).use { it.get(this) }
}

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

public operator fun ClassLoader.get(fqName: FqName): Class<*> = loadClass(fqName)
public operator fun ClassLoader.get(fqName: String): Class<*> = loadClass(fqName)

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

public val Class<*>.generatedBindingModule: Class<*>
  get() = classLoader.loadClass("${kotlin.qualifiedName}BindingModule")
public val Class<*>.contributesToAnnotation: ContributesTo
  get() = annotations.filterIsInstance<ContributesTo>().single()
