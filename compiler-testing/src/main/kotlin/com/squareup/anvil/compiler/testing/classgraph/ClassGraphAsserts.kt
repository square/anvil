package com.squareup.anvil.compiler.testing.classgraph

import com.squareup.anvil.compiler.testing.MoreAsserts
import com.squareup.anvil.compiler.testing.asJavaNameString
import io.github.classgraph.ClassInfo
import io.github.classgraph.ClassInfoList
import io.github.classgraph.PackageInfo
import io.github.classgraph.ScanResult
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public interface ClassGraphAsserts : MoreAsserts {

  public infix fun ScanResult.shouldContainPackage(packageFqName: String): PackageInfo {
    packageInfo.names shouldContain packageFqName
    return getPackageInfo(packageFqName)
  }

  public infix fun ScanResult.shouldContainPackage(packageFqName: FqName): PackageInfo =
    getPackageInfo(packageFqName.asString())

  public infix fun ScanResult.shouldContainClass(classFqName: String): ClassInfo {
    allClasses.names shouldContain classFqName
    return getClassInfo(classFqName)
  }

  public infix fun ScanResult.shouldContainClass(classId: ClassId): ClassInfo {
    allClasses.classIds() shouldContain classId
    return getClassInfo(classId.asJavaNameString())
  }

  public infix fun ScanResult.shouldContainClasses(classNames: Iterable<String>) {
    allClasses.names shouldContain classNames
  }

  public infix fun ScanResult.shouldContainClasses(classIds: Collection<ClassId>) {
    allClasses.classIds() shouldContain classIds
  }

  public infix fun ScanResult.shouldNotContainClass(classFqName: String) {
    allClasses.names shouldNotContain classFqName
  }

  public infix fun ScanResult.shouldNotContainClass(classId: ClassId) {
    allClasses.classIds() shouldNotContain classId
  }

  public infix fun ClassInfoList.shouldContainClass(classFqName: String): ClassInfo {
    names shouldContain classFqName
    return get(classFqName)
  }

  public infix fun ClassInfoList.shouldContainClass(classId: ClassId): ClassInfo {
    classIds() shouldContain classId
    return get(classId)
  }

  public infix fun ClassInfoList.shouldContainClasses(classNames: Iterable<String>) {
    names shouldContain classNames
  }

  public infix fun ClassInfoList.shouldContainClasses(classIds: Collection<ClassId>) {
    classIds() shouldContain classIds
  }

  public infix fun ClassInfoList.shouldNotContainClass(classFqName: String) {
    names shouldNotContain classFqName
  }

  public infix fun ClassInfoList.shouldNotContainClass(classId: ClassId) {
    classIds() shouldNotContain classId
  }

  public infix fun ClassInfoList.shouldContainExactly(classNames: Collection<String>) {
    names shouldContainExactly classNames
  }

  /** An alias for [shouldContainExactly], since order doesn't matter when comparing class names. */
  public infix fun ClassInfoList.shouldBe(classNames: Collection<String>) {
    names shouldContainExactly classNames
  }
}
