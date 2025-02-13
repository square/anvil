package com.squareup.anvil.compiler.testing.classgraph

import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.asJavaNameString
import dagger.Component
import io.github.classgraph.ClassInfo
import io.github.classgraph.ClassInfoList
import io.github.classgraph.FieldInfo
import io.github.classgraph.PackageInfo
import io.github.classgraph.ScanResult
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public operator fun ClassInfoList.get(classId: ClassId): ClassInfo =
  requireNotNull(
    get(classId.asJavaNameString()),
  ) { "Class not found: ${classId.asJavaNameString()}" }

public fun ScanResult.getClassInfo(classId: ClassId): ClassInfo =
  requireNotNull(getClassInfo(classId.asJavaNameString())) {
    "Class not found: ${classId.asJavaNameString()}"
  }

public fun ScanResult.getClassInfo(fqName: FqName): ClassInfo =
  requireNotNull(getClassInfo(fqName.asString())) { "Class not found: $fqName" }

public fun ScanResult.getPackageInfo(fqName: FqName): PackageInfo =
  requireNotNull(getPackageInfo(fqName.asString())) { "Package not found: $fqName" }

public fun ScanResult.getClassInfoOrNull(fqName: FqName): ClassInfo? =
  getClassInfo(fqName.asString())

public fun ClassInfo.field(): ReadOnlyProperty<Any?, FieldInfo> =
  ReadOnlyProperty { _, property -> getFieldInfo(property.name).shouldNotBeNull() }

public fun ClassInfo.declaredField(): ReadOnlyProperty<Any?, FieldInfo> =
  ReadOnlyProperty { _, property -> getDeclaredFieldInfo(property.name).shouldNotBeNull() }

public fun FqName.getClassInfo(scanResult: ScanResult): ClassInfo {
  return scanResult.getClassInfo(this)
}

public operator fun ScanResult.get(fqName: FqName): ClassInfo = getClassInfo(fqName)
public operator fun ScanResult.get(fqName: String): ClassInfo = getClassInfo(fqName)

public operator fun ScanResult.getValue(thisRef: Any?, property: KProperty<*>): ClassInfo =
  getClassInfo(property.name)

/**
 * Returns all classes that are annotated with `@Module` in this scan result.
 * The classes are sorted by their name.
 *
 * ```
 * scanResult.allModuleClasses()
 *   .names() shouldBe listOf("com.squareup.test.MyModule")
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
 * scanResult.allDaggerFactoryClasses().names() shouldBe listOf(
 *     "com.squareup.test.InjectClass_Factory",
 *     "com.squareup.test.OtherClass_Factory",
 *   )
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
 * scanResult.allMergedModulesForComponent("com.squareup.test.AppComponent")
 *   .names() shouldBe listOf(
 *     "com.squareup.test.ModuleA",
 *     "com.squareup.test.ModuleB",
 *   )
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
 * scanResult.allMergedModulesForComponent("com.squareup.test.AppComponent")
 *   .names() shouldBe listOf(
 *     "com.squareup.test.ComponentA",
 *     "com.squareup.test.ComponentB",
 *   )
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
 * scanResult.allBindsMethods()
 *   .names() shouldBe listOf(
 *     "com.squareup.test.ModuleA.bindFoo",
 *     "com.squareup.test.ModuleA.provideBar",
 *     "com.squareup.test.ModuleB.bindBaz",
 *   )
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
 * scanResult.allBoundTypes() shouldBe listOf("com.example.FooImpl" to "com.example.Foo")
 * ```
 */
public fun ScanResult.allBoundTypes(): List<Pair<ParameterTypeName, ReturnTypeName>> {
  return allModuleClasses()
    .flatMap { it.allBindMethods() }
    .map { it.boundTypes() }
    .sortedBy { it.first }
}

public val PackageInfo.allClassNames: List<String>
  get() = classInfoRecursive.names.sorted()
public val ScanResult.any: ClassInfo
  get() = getClassInfo(TestNames.any)
public val ScanResult.nothing: ClassInfo
  get() = getClassInfo(TestNames.nothing)
public val ScanResult.squareupTest: PackageInfo
  get() = getPackageInfo(TestNames.squareupTest)
public val ScanResult.contributingObject: ClassInfo
  get() = getClassInfo(TestNames.contributingObject)
public val ScanResult.contributingInterface: ClassInfo
  get() = getClassInfo(TestNames.contributingInterface)
public val ScanResult.secondContributingInterface: ClassInfo
  get() = getClassInfo(TestNames.secondContributingInterface)
public val ScanResult.parentInterface: ClassInfo
  get() = getClassInfo(TestNames.parentInterface)
public val ScanResult.parentInterface1: ClassInfo
  get() = getClassInfo(TestNames.parentInterface1)
public val ScanResult.parentInterface2: ClassInfo
  get() = getClassInfo(TestNames.parentInterface2)
public val ScanResult.componentInterface: ClassInfo
  get() = getClassInfo(TestNames.componentInterface)
public val ScanResult.subcomponentInterface: ClassInfo
  get() = getClassInfo(TestNames.subcomponentInterface)
public val ScanResult.assistedService: ClassInfo
  get() = getClassInfo(TestNames.assistedService)
public val ScanResult.assistedServiceFactory: ClassInfo
  get() = getClassInfo(TestNames.assistedServiceFactory)
public val ScanResult.daggerModule1: ClassInfo
  get() = getClassInfo(TestNames.daggerModule1)
public val ScanResult.daggerModule2: ClassInfo
  get() = getClassInfo(TestNames.daggerModule2)
public val ScanResult.daggerModule3: ClassInfo
  get() = getClassInfo(TestNames.daggerModule3)
public val ScanResult.daggerModule4: ClassInfo
  get() = getClassInfo(TestNames.daggerModule4)
public val ScanResult.injectClass: ClassInfo
  get() = getClassInfo(TestNames.injectClass)
public val ScanResult.injectClass_Factory: ClassInfo
  get() = getClassInfo(TestNames.injectClass_Factory)
public val ScanResult.javaClass: ClassInfo
  get() = getClassInfo(TestNames.javaClass)
public val ScanResult.anyQualifier: ClassInfo
  get() = getClassInfo(TestNames.anyQualifier)
