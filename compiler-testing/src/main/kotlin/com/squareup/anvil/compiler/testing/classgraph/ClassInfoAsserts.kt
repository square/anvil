package com.squareup.anvil.compiler.testing.classgraph

import io.github.classgraph.ClassInfo
import io.github.classgraph.FieldInfo
import io.github.classgraph.MethodInfo
import io.github.classgraph.MethodInfoList
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize

public interface ClassInfoAsserts {

  public infix fun ClassInfo.shouldContainField(fieldName: String): FieldInfo {
    fieldInfo shouldContain fieldName
    return getFieldInfo(fieldName)
  }

  public infix fun ClassInfo.shouldContainFields(fieldNames: Collection<String>) {
    fieldNames shouldContain fieldNames
  }

  public infix fun ClassInfo.shouldContainExactFields(fieldNames: Collection<String>) {
    fieldNames shouldContainExactly fieldNames
  }

  public infix fun ClassInfo.shouldContainDeclaredField(fieldName: String): FieldInfo {
    declaredFieldInfo shouldContain fieldName
    return getDeclaredFieldInfo(fieldName)
  }

  public infix fun ClassInfo.shouldContainDeclaredFields(fieldNames: Collection<String>) {
    declaredFieldInfo shouldContain fieldNames
  }

  public infix fun ClassInfo.shouldContainExactDeclaredFields(fieldNames: Collection<String>) {
    declaredFieldInfo shouldContainExactly fieldNames
  }

  public infix fun ClassInfo.shouldContainMethod(methodName: String): MethodInfo {
    methodInfo shouldHaveSingleElement { it.name == methodName }
    return methodInfo.getSingleMethod(methodName)
  }

  public infix fun ClassInfo.shouldContainMethods(methodNames: Collection<String>) {
    methodNames shouldContain methodNames
  }

  public infix fun ClassInfo.shouldContainExactMethods(methodNames: Collection<String>) {
    methodNames shouldContainExactly methodNames
  }
  public infix fun ClassInfo.shouldContainDeclaredMethod(methodName: String): MethodInfo {
    declaredMethodInfo shouldHaveSingleElement { it.name == methodName }
    return declaredMethodInfo.getSingleMethod(methodName)
  }

  public infix fun ClassInfo.shouldContainDeclaredMethods(methodNames: Collection<String>) {
    declaredMethodInfo.names shouldContain methodNames
  }

  public infix fun ClassInfo.shouldContainExactDeclaredMethods(methodNames: Collection<String>) {
    declaredMethodInfo.names shouldContainExactly methodNames
  }

  public infix fun ClassInfo.shouldContainOverloadedMethodsNamed(
    methodName: String,
  ): MethodInfoList = getMethodInfo(methodName).also { it shouldHaveAtLeastSize 2 }

  public fun ClassInfo.shouldHaveSingleConstructor(): MethodInfo {
    return constructorInfo.shouldHaveSize(1).single()
  }
}
