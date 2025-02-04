package com.squareup.anvil.compiler.testing.classgraph

import dagger.Component
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import io.github.classgraph.HasName
import io.github.classgraph.MethodInfo
import io.github.classgraph.ScanResult
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.kotlin.name.FqName

/** A class that's annotated with `@Module` */
public typealias ModuleClassInfo = ClassInfo

/** A class that implements `dagger.internal.Factory` */
public typealias DaggerFactoryClassInfo = ClassInfo

/** A method that's annotated with `@Binds` */
public typealias BindsMethodInfo = MethodInfo

/** A method that's annotated with `@Provides` */
public typealias ProvidesMethodInfo = MethodInfo

public typealias ReturnTypeName = String
public typealias ParameterTypeName = String

public fun ClassInfo.getAnnotationInfo(fqName: FqName): AnnotationInfo? {
  return getAnnotationInfo(fqName.asString())
}

/**
 * Returns all classes that are annotated with `@Module` in this scan result.
 * The classes are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   classGraph.allModuleClasses()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
public fun ScanResult.allModuleClasses(): List<ModuleClassInfo> {
  return allClasses
    .filter { it.hasAnnotation("dagger.Module") }
    .sortedBy { it.name }
}

/**
 * Returns all classes that implement `dagger.internal.Factory` in this scan result.
 * The classes are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   classGraph.allDaggerFactoryClasses()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
public fun ScanResult.allDaggerFactoryClasses(): List<DaggerFactoryClassInfo> {
  return allClasses
    .filter { it.implementsInterface("dagger.internal.Factory") }
    .sortedBy { it.name }
}

/**
 * Returns all classes that are arguments to the [modules][dagger.Component.modules] parameter
 * of a specific [@Component][dagger.Component] annotation applied to [componentInterfaceName].
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   classGraph.allMergedModulesForComponent("com.squareup.test.AppComponent")
 *     .names() shouldBe listOf(
 *       "com.squareup.test.ModuleA",
 *       "com.squareup.test.ModuleB",
 *     )
 * }
 * ```
 */
public fun ScanResult.allMergedModulesForComponent(
  componentInterfaceName: String,
): List<ModuleClassInfo> {
  val componentClass = loadClass(componentInterfaceName, false)
  val annotation = componentClass.getAnnotation(Component::class.java)

  return annotation.modules.map { getClassInfo(it.java.name) as ModuleClassInfo }
}

/**
 * Returns all interfaces that are direct supertypes to [className].
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *
 *   // ...
 *
 *   classGraph.allMergedModulesForComponent("com.squareup.test.AppComponent")
 *     .names() shouldBe listOf(
 *       "com.squareup.test.A",
 *       "com.squareup.test.B",
 *     )
 * }
 * ```
 */
public fun ScanResult.allDirectInterfaces(className: String): List<ClassInfo> {
  return loadClass(className, false)
    .interfaces
    .map { getClassInfo(it.name) as ClassInfo }
}

/**
 * Returns all methods inside `@Module` types that are annotated with `@Binds` in this scan result.
 * The methods are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   classGraph.allBindsMethods()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
public fun ScanResult.allBindsMethods(): List<BindsMethodInfo> {
  return allModuleClasses()
    .flatMap { it.allBindMethods() }
    .sortedBy { it.name }
}

/**
 * Returns pairs of types bound in `@Binds` methods,
 * where the first type is the parameter/contributed type
 * and the second type is the return/bound type.
 * The pairs are sorted by the parameter type.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   classGraph.allBoundTypes() shouldBe listOf("com.example.FooImpl" to "com.example.Foo")
 * }
 * ```
 */
public fun ScanResult.allBoundTypes(): List<Pair<ParameterTypeName, ReturnTypeName>> {
  return allModuleClasses()
    .flatMap { it.allBindMethods() }
    .map { it.boundTypes() }
    .sortedBy { it.first }
}

/**
 * Returns all binding methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
public fun ModuleClassInfo.allBindMethods(): List<BindsMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Binds") }
}

/**
 * Returns all `provide___` methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
public fun ModuleClassInfo.allProvidesMethods(): List<ProvidesMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Provides") }
}

public fun BindsMethodInfo.boundTypes(): Pair<ParameterTypeName, ReturnTypeName> {
  val returnType = typeSignatureOrTypeDescriptor.resultType.toString()
  val parameterType = parameterInfo.single().typeSignatureOrTypeDescriptor.toString()
  return parameterType to returnType
}

public fun Collection<HasName>.fqNames(): List<FqName> = map { FqName(it.name) }

public fun ScanResult.shouldContainClass(classFqName: String): ClassInfo {
  return getClassInfo(classFqName).shouldNotBeNull()
}

public fun ScanResult.shouldContainClass(classFqName: FqName): ClassInfo {
  return getClassInfo(classFqName).shouldNotBeNull()
}

public fun ScanResult.getClassInfo(fqName: FqName): ClassInfo =
  requireNotNull(getClassInfo(fqName.asString())) { "Class not found: $fqName" }

public fun ScanResult.getClassInfoOrNull(fqName: FqName): ClassInfo? =
  getClassInfo(fqName.asString())

public fun ClassLoader.loadClass(fqName: FqName): Class<*> = loadClass(fqName.asString())
public fun ClassLoader.loadClassOrNull(fqName: FqName): Class<*>? = try {
  loadClass(fqName.asString())
} catch (e: ClassNotFoundException) {
  null
}

public fun FqName.loadClass(classLoader: ClassLoader): Class<*> {
  return classLoader.loadClass(this.asString())
}

public fun FqName.loadClass(scanResult: ScanResult): ClassInfo? {
  return scanResult.getClassInfo(this)
}
