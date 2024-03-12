package com.squareup.anvil.plugin.testing

import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain

interface ClassGraphAsserts {

  infix fun ScanResult.shouldContainClass(classFqName: String) {
    allClasses.names shouldContain classFqName
  }

  infix fun ScanResult.shouldContainClasses(classNames: Collection<String>) {
    allClasses.names shouldContain classNames
  }

  infix fun ScanResult.shouldNotContainClass(classFqName: String) {
    allClasses.names shouldNotContain classFqName
  }

  infix fun ClassInfoList.shouldNotContainClass(classFqName: String) {
    names shouldNotContain classFqName
  }

  infix fun ClassInfoList.shouldContainClass(classFqName: String) {
    names shouldContain classFqName
  }

  infix fun ClassInfoList.shouldContainExactly(classNames: Collection<String>) {
    names shouldContainExactly classNames
  }

  /** An alias for [shouldContainExactly], since order doesn't matter when comparing class names. */
  infix fun ClassInfoList.shouldBe(classNames: Collection<String>) {
    names shouldContainExactly classNames
  }
}
