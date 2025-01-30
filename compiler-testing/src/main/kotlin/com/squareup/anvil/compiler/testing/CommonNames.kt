package com.squareup.anvil.compiler.testing

import com.squareup.anvil.compiler.testing.compilation.Compile2Result
import io.github.classgraph.ClassInfo
import io.github.classgraph.ScanResult
import org.jetbrains.kotlin.name.FqName

internal object CommonNames {

  val any = FqName("kotlin.Any")
  val nothing = FqName("kotlin.Nothing")
  val contributingObject = FqName("com.squareup.test.ContributingObject")
  val contributingInterface = FqName("com.squareup.test.ContributingInterface")
  val secondContributingInterface = FqName("com.squareup.test.SecondContributingInterface")
  val someClassInnerInterface = FqName("com.squareup.test.SomeClass\$InnerInterface")
  val parentInterface = FqName("com.squareup.test.ParentInterface")
  val parentInterface1 = FqName("com.squareup.test.ParentInterface1")
  val parentInterface2 = FqName("com.squareup.test.ParentInterface2")
  val componentInterface = FqName("com.squareup.test.ComponentInterface")
  val subcomponentInterface = FqName("com.squareup.test.SubcomponentInterface")
  val daggerModule1 = FqName("com.squareup.test.DaggerModule1")
  val assistedService = FqName("com.squareup.test.AssistedService")
  val assistedServiceFactory = FqName("com.squareup.test.AssistedServiceFactory")
  val daggerModule2 = FqName("com.squareup.test.DaggerModule2")
  val daggerModule3 = FqName("com.squareup.test.DaggerModule3")
  val daggerModule4 = FqName("com.squareup.test.DaggerModule4")
  val componentInterfaceInnerModule = FqName("com.squareup.test.ComponentInterface\$InnerModule")
  val parentClassNestedInjectClass = FqName("com.squareup.test.ParentClass\$NestedInjectClass")
  val injectClass = FqName("com.squareup.test.InjectClass")
  val injectClassFactory = FqName("com.squareup.test.InjectClass_Factory")
  val javaClass = FqName("com.squareup.test.JavaClass")
  val anyQualifier = FqName("com.squareup.test.AnyQualifier")
}

internal operator fun Compile2Result.invoke(fqName: FqName): Class<*> = classLoader.loadClass(fqName)

internal operator fun ClassLoader.get(fqName: FqName): Class<*> = loadClass(fqName)
internal operator fun ClassLoader.get(fqName: String): Class<*> = loadClass(fqName)
internal operator fun ScanResult.get(fqName: FqName): ClassInfo = getClassInfo(fqName)
internal operator fun ScanResult.get(fqName: String): ClassInfo = getClassInfo(fqName)

internal val Compile2Result.contributingObject: Class<*>
  get() = classLoader.loadClass(CommonNames.contributingObject)
internal val Compile2Result.contributingObjectInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.contributingObject)
internal val Compile2Result.contributingInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.contributingInterface)
internal val Compile2Result.contributingInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.contributingInterface)
internal val Compile2Result.secondContributingInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.secondContributingInterface)
internal val Compile2Result.secondContributingInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.secondContributingInterface)
internal val Compile2Result.someClassInnerInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.someClassInnerInterface)
internal val Compile2Result.someClassInnerInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.someClassInnerInterface)
internal val Compile2Result.parentInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.parentInterface)
internal val Compile2Result.parentInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.parentInterface)
internal val Compile2Result.parentInterface1: Class<*>
  get() = classLoader.loadClass(CommonNames.parentInterface1)
internal val Compile2Result.parentInterface1Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.parentInterface1)
internal val Compile2Result.parentInterface2: Class<*>
  get() = classLoader.loadClass(CommonNames.parentInterface2)
internal val Compile2Result.parentInterface2Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.parentInterface2)
internal val Compile2Result.componentInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.componentInterface)
internal val Compile2Result.componentInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.componentInterface)
internal val Compile2Result.subcomponentInterface: Class<*>
  get() = classLoader.loadClass(CommonNames.subcomponentInterface)
internal val Compile2Result.subcomponentInterfaceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.subcomponentInterface)
internal val Compile2Result.daggerModule1: Class<*>
  get() = classLoader.loadClass(CommonNames.daggerModule1)
internal val Compile2Result.daggerModule1Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.daggerModule1)
internal val Compile2Result.assistedService: Class<*>
  get() = classLoader.loadClass(CommonNames.assistedService)
internal val Compile2Result.assistedServiceInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.assistedService)
internal val Compile2Result.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass(CommonNames.assistedServiceFactory)
internal val Compile2Result.assistedServiceFactoryInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.assistedServiceFactory)
internal val Compile2Result.daggerModule2: Class<*>
  get() = classLoader.loadClass(CommonNames.daggerModule2)
internal val Compile2Result.daggerModule2Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.daggerModule2)
internal val Compile2Result.daggerModule3: Class<*>
  get() = classLoader.loadClass(CommonNames.daggerModule3)
internal val Compile2Result.daggerModule3Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.daggerModule3)
internal val Compile2Result.daggerModule4: Class<*>
  get() = classLoader.loadClass(CommonNames.daggerModule4)
internal val Compile2Result.daggerModule4Info: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.daggerModule4)
internal val Compile2Result.componentInterfaceInnerModule: Class<*>
  get() = classLoader.loadClass(CommonNames.componentInterfaceInnerModule)
internal val Compile2Result.componentInterfaceInnerModuleInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.componentInterfaceInnerModule)
internal val Compile2Result.parentClassNestedInjectClass: Class<*>
  get() = classLoader.loadClass(CommonNames.parentClassNestedInjectClass)
internal val Compile2Result.parentClassNestedInjectClassInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.parentClassNestedInjectClass)
internal val Compile2Result.injectClass: Class<*>
  get() = classLoader.loadClass(CommonNames.injectClass)
internal val Compile2Result.injectClassInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.injectClass)
internal val Compile2Result.injectClassFactory: Class<*>
  get() = classLoader.loadClass(CommonNames.injectClassFactory)
internal val Compile2Result.injectClassFactoryInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.injectClassFactory)
internal val Compile2Result.javaClass: Class<*>
  get() = classLoader.loadClass(CommonNames.javaClass)
internal val Compile2Result.javaClassInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.javaClass)
internal val Compile2Result.anyQualifier: Class<*>
  get() = classLoader.loadClass(CommonNames.anyQualifier)
internal val Compile2Result.anyQualifierInfo: ClassInfo
  get() = classGraph.getClassInfo(CommonNames.anyQualifier)

internal fun ScanResult.getClassInfo(fqName: FqName) = getClassInfo(fqName.asString())
internal fun ScanResult.getClassInfoOrNull(fqName: FqName) = try {
  getClassInfo(fqName)
} catch (e: ClassNotFoundException) {
  null
}

internal fun ClassLoader.loadClass(fqName: FqName): Class<*> = loadClass(fqName.asString())
internal fun ClassLoader.loadClassOrNull(fqName: FqName): Class<*>? = try {
  loadClass(fqName.asString())
} catch (e: ClassNotFoundException) {
  null
}

internal fun FqName.loadClass(classLoader: ClassLoader): Class<*> {
  return classLoader.loadClass(this.asString())
}
internal fun FqName.loadClass(scanResult: ScanResult): ClassInfo? {
  return scanResult.getClassInfo(this)
}
