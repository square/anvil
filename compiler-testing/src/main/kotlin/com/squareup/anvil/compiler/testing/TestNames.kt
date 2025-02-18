package com.squareup.anvil.compiler.testing

import com.squareup.anvil.compiler.testing.classgraph.getClassInfo
import com.squareup.anvil.compiler.testing.classgraph.loadClass
import com.squareup.anvil.compiler.testing.compilation.Compile2Result
import io.github.classgraph.ClassInfo
import io.github.classgraph.ScanResult
import org.jetbrains.kotlin.name.FqName

public object TestNames {
  public val testPackage: String = "package com.squareup.test"
  public val any: FqName = FqName("kotlin.Any")
  public val nothing: FqName = FqName("kotlin.Nothing")
  public val contributingObject: FqName = FqName("com.squareup.test.ContributingObject")
  public val contributingInterface: FqName = FqName("com.squareup.test.ContributingInterface")
  public val secondContributingInterface: FqName =
    FqName("com.squareup.test.SecondContributingInterface")
  public val someClassInnerInterface: FqName = FqName("com.squareup.test.SomeClass\$InnerInterface")
  public val parentInterface: FqName = FqName("com.squareup.test.ParentInterface")
  public val parentInterface1: FqName = FqName("com.squareup.test.ParentInterface1")
  public val parentInterface2: FqName = FqName("com.squareup.test.ParentInterface2")
  public val componentInterface: FqName = FqName("com.squareup.test.ComponentInterface")
  public val subcomponentInterface: FqName = FqName("com.squareup.test.SubcomponentInterface")
  public val daggerModule1: FqName = FqName("com.squareup.test.DaggerModule1")
  public val assistedService: FqName = FqName("com.squareup.test.AssistedService")
  public val assistedServiceFactory: FqName = FqName("com.squareup.test.AssistedServiceFactory")
  public val daggerModule2: FqName = FqName("com.squareup.test.DaggerModule2")
  public val daggerModule3: FqName = FqName("com.squareup.test.DaggerModule3")
  public val daggerModule4: FqName = FqName("com.squareup.test.DaggerModule4")
  public val componentInterfaceInnerModule: FqName =
    FqName("com.squareup.test.ComponentInterface\$InnerModule")
  public val parentClassNestedInjectClass: FqName =
    FqName("com.squareup.test.ParentClass\$NestedInjectClass")
  public val injectClass: FqName = FqName("com.squareup.test.InjectClass")
  public val injectClassFactory: FqName = FqName("com.squareup.test.InjectClass_Factory")
  public val javaClass: FqName = FqName("com.squareup.test.JavaClass")
  public val anyQualifier: FqName = FqName("com.squareup.test.AnyQualifier")
}

internal operator fun Compile2Result.invoke(fqName: FqName): Class<*> =
  classLoader.loadClass(fqName)

internal operator fun ClassLoader.get(fqName: FqName): Class<*> = loadClass(fqName)
internal operator fun ClassLoader.get(fqName: String): Class<*> = loadClass(fqName)
internal operator fun ScanResult.get(fqName: FqName): ClassInfo = getClassInfo(fqName)
internal operator fun ScanResult.get(fqName: String): ClassInfo = getClassInfo(fqName)

public val Compile2Result.contributingObject: Class<*>
  get() = classLoader.loadClass(TestNames.contributingObject)
public val Compile2Result.contributingObjectInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.contributingObject)
public val Compile2Result.contributingInterface: Class<*>
  get() = classLoader.loadClass(TestNames.contributingInterface)
public val Compile2Result.contributingInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.contributingInterface)
public val Compile2Result.secondContributingInterface: Class<*>
  get() = classLoader.loadClass(TestNames.secondContributingInterface)
public val Compile2Result.secondContributingInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.secondContributingInterface)
public val Compile2Result.someClassInnerInterface: Class<*>
  get() = classLoader.loadClass(TestNames.someClassInnerInterface)
public val Compile2Result.someClassInnerInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.someClassInnerInterface)
public val Compile2Result.parentInterface: Class<*>
  get() = classLoader.loadClass(TestNames.parentInterface)
public val Compile2Result.parentInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.parentInterface)
public val Compile2Result.parentInterface1: Class<*>
  get() = classLoader.loadClass(TestNames.parentInterface1)
public val Compile2Result.parentInterface1Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.parentInterface1)
public val Compile2Result.parentInterface2: Class<*>
  get() = classLoader.loadClass(TestNames.parentInterface2)
public val Compile2Result.parentInterface2Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.parentInterface2)
public val Compile2Result.componentInterface: Class<*>
  get() = classLoader.loadClass(TestNames.componentInterface)
public val Compile2Result.componentInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.componentInterface)
public val Compile2Result.subcomponentInterface: Class<*>
  get() = classLoader.loadClass(TestNames.subcomponentInterface)
public val Compile2Result.subcomponentInterfaceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.subcomponentInterface)
public val Compile2Result.daggerModule1: Class<*>
  get() = classLoader.loadClass(TestNames.daggerModule1)
public val Compile2Result.daggerModule1Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.daggerModule1)
public val Compile2Result.assistedService: Class<*>
  get() = classLoader.loadClass(TestNames.assistedService)
public val Compile2Result.assistedServiceInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.assistedService)
public val Compile2Result.assistedServiceFactory: Class<*>
  get() = classLoader.loadClass(TestNames.assistedServiceFactory)
public val Compile2Result.assistedServiceFactoryInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.assistedServiceFactory)
public val Compile2Result.daggerModule2: Class<*>
  get() = classLoader.loadClass(TestNames.daggerModule2)
public val Compile2Result.daggerModule2Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.daggerModule2)
public val Compile2Result.daggerModule3: Class<*>
  get() = classLoader.loadClass(TestNames.daggerModule3)
public val Compile2Result.daggerModule3Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.daggerModule3)
public val Compile2Result.daggerModule4: Class<*>
  get() = classLoader.loadClass(TestNames.daggerModule4)
public val Compile2Result.daggerModule4Info: ClassInfo
  get() = scanResult.getClassInfo(TestNames.daggerModule4)
public val Compile2Result.componentInterfaceInnerModule: Class<*>
  get() = classLoader.loadClass(TestNames.componentInterfaceInnerModule)
public val Compile2Result.componentInterfaceInnerModuleInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.componentInterfaceInnerModule)
public val Compile2Result.parentClassNestedInjectClass: Class<*>
  get() = classLoader.loadClass(TestNames.parentClassNestedInjectClass)
public val Compile2Result.parentClassNestedInjectClassInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.parentClassNestedInjectClass)
public val Compile2Result.injectClass: Class<*>
  get() = classLoader.loadClass(TestNames.injectClass)
public val Compile2Result.injectClassInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.injectClass)
public val Compile2Result.injectClassFactory: Class<*>
  get() = classLoader.loadClass(TestNames.injectClassFactory)
public val Compile2Result.injectClassFactoryInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.injectClassFactory)
public val Compile2Result.javaClass: Class<*>
  get() = classLoader.loadClass(TestNames.javaClass)
public val Compile2Result.javaClassInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.javaClass)
public val Compile2Result.anyQualifier: Class<*>
  get() = classLoader.loadClass(TestNames.anyQualifier)
public val Compile2Result.anyQualifierInfo: ClassInfo
  get() = scanResult.getClassInfo(TestNames.anyQualifier)
